package com.example.vojta.ond0209_tamzii_project;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.widget.Toast;

public class MyReceiver extends BroadcastReceiver {

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private MainActivity mActivity;

    public MyReceiver (WifiP2pManager mManager, WifiP2pManager.Channel mChannel, MainActivity mActivity)
    {
        super();
        this.mActivity = mActivity;
        this.mChannel = mChannel;
        this.mManager = mManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                mActivity.setWifiEnabled(true);
            } else {
                mActivity.setWifiEnabled(false);
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            if (mManager != null) {
                Toast.makeText(mActivity, "Peers Available", Toast.LENGTH_LONG).show();
                mManager.requestPeers(mChannel, (PeerListListener) mActivity.getFragmentManager()
                        .findFragmentById(R.id.list_fragment));
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if (mManager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected()) {
                mActivity.disconnect_btn.setEnabled(true);
                mActivity.stream_btn.setEnabled(true);
                mManager.requestConnectionInfo(mChannel, mActivity);

            } else {
                mActivity.disconnect_btn.setEnabled(false);
                mActivity.connect_btn.setEnabled(false);
                mActivity.stream_btn.setEnabled(false);
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            DeviceList fragment = (DeviceList) mActivity.getFragmentManager()
                    .findFragmentById(R.id.list_fragment);
            fragment.updateThisDevice((WifiP2pDevice) intent.getParcelableExtra(
                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
        }
    }
}
