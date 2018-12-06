package com.example.cheonjunhyeon.ssgdoorlock;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class GetRssiThread extends Thread {

    private BroadcastReceiver mReceiver;
    private BluetoothAdapter mAdapter;
    private Handler rssiHandler;
    private IntentFilter itf;
    private Context rcvContext;

    private int rssi;

    public GetRssiThread(BluetoothAdapter bleApt, Context context) {
        mAdapter = bleApt;
        rcvContext = context;
        itf = new IntentFilter();
        itf.addAction(BluetoothDevice.ACTION_FOUND);

        rssi = 0;
    }

    private void registerReceiver() {
        if (mReceiver != null) return;

        itf = new IntentFilter();
        itf.addAction(BluetoothDevice.ACTION_FOUND);
        itf.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        itf.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);

//                if(BluetoothAdapter.SCAN_MODE_NONE == state) {
//                    Log.d("siba", "SCAN_MODE_NONE");
//                    unregisterReceiver();
//                    registerReceiver();
//                    mAdapter.startDiscovery();
//                    return;
//                }

                if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Log.d("siba", "DISCOVERY_FINISHED");
                    try{
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                            e.printStackTrace();
                    }
                    mAdapter.startDiscovery();
                    return;
                }
                else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                    Log.d("siba", "DISCOVERY_STARTED");

                    rssiHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.cancelDiscovery();
                        }
                    }, 1000);
                }

                if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                    if (name == null) {
//                        rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
//                        Log.d("siba", "null rssi : " + String.valueOf(rssi));
                        return;
                    }
                    else if (name.equals("G7 ThinQ")) {
                        rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                        Log.d("jaebal", "GetRssiThread");
                        Log.d("siba", name + " rssi : " + String.valueOf(rssi));
                        Toast.makeText(rcvContext, "nsl rssi : " + String.valueOf(rssi), Toast.LENGTH_SHORT).show();
//                        mAdapter.cancelDiscovery();
//                        try{
//                            Thread.sleep(10);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
                    }
                    else {
//                        Log.d("siba", "siba " + name);
                    }
                }
            }
        };

        rcvContext.registerReceiver(this.mReceiver, itf);
    }

    private void unregisterReceiver() {
        if (mReceiver != null) {
            rcvContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        mAdapter.cancelDiscovery();
    }

    public void run() {
        Looper.prepare();
        rssiHandler = new Handler();
        registerReceiver();
        mAdapter.startDiscovery();
        Looper.loop();
    }

    public void finish() {
        if(rssiHandler != null) {
            rssiHandler.getLooper().quit();
        }
        if(mReceiver != null) {
            unregisterReceiver();
        }
    }

    public int getRSSI() {
        return rssi;
    }


}
