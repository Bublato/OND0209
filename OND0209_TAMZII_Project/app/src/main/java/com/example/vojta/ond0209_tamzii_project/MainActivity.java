package com.example.vojta.ond0209_tamzii_project;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;


/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class MainActivity extends Activity implements DeviceActionListener, WifiP2pManager.ChannelListener, WifiP2pManager.ConnectionInfoListener {
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver = null;
    public boolean WifiEnabled = false;
    public Button connect_btn = null;
    public Button disconnect_btn = null;
    public Button stream_btn = null;
    ProgressDialog progressDialog = null;
    private boolean retryChannel = false;
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1001;
    private static final int PERMISSIONS_REQUEST_CODE_RECORD_AUDIO = 1002;
    private static final int sampleRate = 8000;
    private static final int channelConfig = 12;
    private static final int audioFormat = 3;
    public int minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 10;
    public static AudioTrack speaker = null;
    public AudioRecord recorder = null;
    public boolean isStreaming = false;
    public boolean isReceiving = false;
    public int port = 50005;
    public String groupOwnerIP;
    public boolean isHost = false;
    public DatagramSocket sendSocket = null;
    public DatagramSocket receiveSocket = null;
    public String clientAddr = null;


    public void setWifiEnabled (boolean state)
    {
        this.WifiEnabled = state;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION:
                if  (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
                break;
            case PERMISSIONS_REQUEST_CODE_RECORD_AUDIO:
                if  (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
                break;
        }
    }
    private void requestRecordAudioPermission() {
        //check API version, do nothing if API version < 23!
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion > android.os.Build.VERSION_CODES.LOLLIPOP){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // add necessary intent values to be matched.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new MyReceiver(manager, channel, this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MainActivity.PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }

        requestRecordAudioPermission();

        final Button button = (Button) findViewById(R.id.discover_btn);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!WifiEnabled) {
                    Toast.makeText(MainActivity.this, R.string.p2p_off_warning,
                            Toast.LENGTH_SHORT).show();
                }
                final DeviceList fragment = (DeviceList) getFragmentManager()
                        .findFragmentById(R.id.list_fragment);
                fragment.onInitiateDiscovery();
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this, "Discovery Failed : " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        connect_btn = (Button) findViewById(R.id.connect_btn);
        disconnect_btn = (Button) findViewById(R.id.disconnect_btn);
        stream_btn = (Button) findViewById(R.id.stream_btn);
        stream_btn.setEnabled(false);
        connect_btn.setEnabled(false);
        disconnect_btn.setEnabled(false);
        disconnect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });

        stream_btn.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        isStreaming = true;
                        isReceiving = false;
                        startStreaming();
                        break;

                    case MotionEvent.ACTION_UP:
                        isReceiving = true;
                        isStreaming = false;
                        if(recorder != null)
                        {
                            recorder.release();
                        }
                        startReceiving();
                        break;
                }
                return false;
            }
        });
    }

    @Override
    protected void onResume(){
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(receiver);
    }


    @Override
    public void showButtons(final WifiP2pDevice device){
        connect_btn.setEnabled(true);

        connect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                progressDialog = ProgressDialog.show( MainActivity.this,"Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
                );
                connect(config);
            }
        });
    }

    @Override
    public void onChannelDisconnected() {
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
            connect_btn.setEnabled(false);
            disconnect_btn.setEnabled(false);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
            connect_btn.setEnabled(false);
            disconnect_btn.setEnabled(false);
        }
    }

    @Override
    public void cancelDisconnect() {
        if (manager != null) {
            final DeviceList fragment = (DeviceList) getFragmentManager()
                    .findFragmentById(R.id.list_fragment);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                manager.cancelConnect(channel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    @Override
    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                disconnect_btn.setEnabled(true);
                stream_btn.setEnabled(true);
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                connect_btn.setEnabled(false);

            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    public void disconnect() {
        manager.removeGroup(channel, new ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(MainActivity.this, "Disconnect failed. Retry." + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Disconnect successful.",
                        Toast.LENGTH_SHORT).show();
                isReceiving = false;
                isStreaming = false;
                if(recorder != null)
                {
                    recorder.release();
                }
                disconnect_btn.setEnabled(false);
                stream_btn.setEnabled(false);
            }
        });
    }

    public void setupConnection()
    {
        final Thread connectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(!isHost)
                    {
                        DatagramSocket socket = new DatagramSocket();
                        byte[] buffer = new byte[256];
                        DatagramPacket packet;
                        InetAddress destination;
                        destination = InetAddress.getByName(groupOwnerIP);
                        packet = new DatagramPacket (buffer, buffer.length, destination, port);
                        socket.send(packet);
                    }

                }catch (SocketException e)
                {

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        connectThread.start();
    }

    public void startReceiving() {

        final Thread receiveThread = new Thread (new Runnable() {

            @Override
            public void run() {

                try {
                    receiveSocket = new DatagramSocket(null);
                    receiveSocket.setReuseAddress(true);
                    receiveSocket.bind(new InetSocketAddress(50005));

                    Log.d("VR", "Socket Created");

                    byte[] buffer = new byte[minBufSize];

                    try {
                        speaker = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig, audioFormat, minBufSize, AudioTrack.MODE_STREAM);
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                    while(isReceiving) {
                        try {

                            DatagramPacket packet = new DatagramPacket(buffer,buffer.length);

                            receiveSocket.receive(packet);
                            clientAddr = packet.getAddress().getHostAddress();

                            buffer=packet.getData();
                            speaker.write(buffer, 0, buffer.length);

                            speaker.play();

                        } catch(IOException e) {
                            Log.e("VR","IOException");
                        }
                    }

                } catch (SocketException e) {
                    Log.e("VR", "SocketException");
                }
            }
        });
        receiveThread.start();
    }

    public void startStreaming() {
        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    sendSocket = new DatagramSocket();
                    Log.d("VS", "Socket Created");

                    byte[] buffer = new byte[minBufSize];

                    Log.d("VS","Buffer created of size " + minBufSize);
                    DatagramPacket packet;
                    InetAddress destination;
                    if (isHost)
                    {
                        destination = InetAddress.getByName(clientAddr);
                    }
                    else
                    {
                        destination = InetAddress.getByName(groupOwnerIP);
                    }

                    Log.d("VS", "Address retrieved");
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize);
                    recorder.startRecording();

                    while(isStreaming) {
                        int read = recorder.read(buffer, 0, buffer.length);

                        packet = new DatagramPacket (buffer, read, destination, port);

                        sendSocket.send(packet);
                    }

                } catch(UnknownHostException e) {
                    Log.e("VS", "UnknownHostException");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("VS", "IOException");
                }
            }
        });
        streamThread.start();
    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        groupOwnerIP = info.groupOwnerAddress.getHostAddress();
        isHost = info.isGroupOwner;
        isReceiving = true;
        startReceiving();
        setupConnection();
    }
}