package com.example.cheonjunhyeon.ssgdoorlock;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;


public class DoorLockService extends Service {
    private final String TAG = "DoorLockService";
    private Boolean isInit;
    private Boolean isPostDelay;
    //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // by cheon
    private int isHomeArea;
    private int isMovement;
    private int isIndoor;

    //for indoor process
    private int isFront;
    private int isStopFront;

    //for managing sesnor (accelerometer, magnetism)
    private SensorManager mSensorManager = null;

    //for managing gps
    private Location locationHomeArea;
    LocationManager mLocationManager = null;
    GnssStatus.Callback mGnssStatusCallback;
    private int satCnt;


    private static final int STATE_TRUE = 1;
    private static final int STATE_FALSE = 2;
    //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++



    // Member fields
    private static final UUID uuidSPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private SharedPreferences pref;
    private Handler handler;
    private BluetoothAdapter mAdapter;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    private static final int STATE_NONE = 0;        // we're doing nothing
    private static final int STATE_LISTEN = 1;      // now listening for incoming connections
    private static final int STATE_CONNECTING = 2;  // now initiating an outgoing connection
    private static final int STATE_CONNECTED = 3;   // now connected to a remote device


    public static final int METHODS_RELOAD = -1;
    public static final int METHODS_INIT = 0;
    public static final int METHODS_OPEN = 2;
    public static final int METHODS_CLOSE = 3;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG,"onBind()");
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy()");
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        Log.d(TAG,"onCreate()");
        super.onCreate();

        handler = new Handler();
        pref = getSharedPreferences("pref", MODE_PRIVATE);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        isInit = pref.getBoolean("isInit", FALSE);
        isPostDelay = FALSE;

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        // by cheon
        double lat = Double.valueOf(pref.getString("homeLatitude", "-1"));
        double lon = Double.valueOf(pref.getString("homeLongitude", "-1"));
        locationHomeArea = new Location("homeArea");
        locationHomeArea.setLatitude(lat);
        locationHomeArea.setLongitude(lon);

        satCnt = -1;
        isHomeArea = STATE_NONE;
        isMovement = STATE_NONE;
        isIndoor = STATE_NONE;
        isFront = STATE_NONE;
        isStopFront = STATE_NONE;
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

//        ForeGroundService.startForeground(this);
        Intent localIntent = new Intent(this, ForeGroundService.class);
        startService(localIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG,"onStartCommand()");

        int methods = intent.getIntExtra("methods", 0);
        BluetoothDevice bdevice;

        switch (methods) {
            case METHODS_RELOAD:
                Log.d(TAG, "METHODS_RELOAD");
                String Address = pref.getString("Address", null);

                if (isInit && mState != STATE_CONNECTED && Address != null) {
                    Toast.makeText(this, "reload", Toast.LENGTH_SHORT).show();
                    bdevice = mAdapter.getRemoteDevice(Address);
                    connect(bdevice);
                }
                break;
            case METHODS_INIT:
                Log.d(TAG, "METHODS_INIT");

                start();
                bdevice = intent.getParcelableExtra("bdevice");
                connect(bdevice);
                break;

            case METHODS_OPEN:
                Log.d(TAG, "METHODS_OPEN");

                if (mConnectedThread != null)
                    mConnectedThread.write("1");
                break;
            case METHODS_CLOSE:
                Log.d(TAG, "METHODS_CLOSE");

                if (mConnectedThread != null)
                    mConnectedThread.write("0");
                break;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null; // 디바이스 정보를 얻어서 BluetoothSocket 생성
            try {
                tmp = device.createRfcommSocketToServiceRecord(uuidSPP);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // 연결을 시도하기 전에는 항상 기기 검색을 중지한다.
            // 기기 검색이 계속되면 연결속도가 느려지기 때문이다.
            mAdapter.cancelDiscovery();

            // BluetoothSocket 연결 시도
            try {
                // BluetoothSocket 연결 시도에 대한 return 값은 succes 또는 exception이다.
                mmSocket.connect();
                Log.d(TAG, "Connect Sucacess");
            } catch (IOException e) {
                connectionFailed();	// 연결 실패시 불러오는 메소드
                Log.d(TAG, "Connect Fail"); // socket을 닫는다.
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                } // 연결중? 혹은 연결 대기상태인 메소드를 호출한다.

                DoorLockService.this.start();
                return;
            }

            // ConnectThread 클래스를 reset한다.
            synchronized (DoorLockService.this) {
                mConnectThread = null;
            }

            // ConnectThread를 시작한다.
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        if(!mAdapter.isEnabled()){
            mAdapter.enable(); //강제 활성화
        }

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING)
        {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }
    private void connectionFailed() {
        setState(STATE_LISTEN);

        if(!isPostDelay){
            isPostDelay = TRUE;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isPostDelay = FALSE;
                    String Address = pref.getString("Address",null);

                    if (Address != null) {
                        BluetoothDevice bdevice = mAdapter.getRemoteDevice(Address);
                        connect(bdevice);
                    }
                }
            }, 3000 );
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, BluetoothDevice device) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // BluetoothSocket의 inputstream 과 outputstream을 얻는다.
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

            DoorLockService.this.setInit(device);
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;



            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // InputStream으로부터 값을 받는 읽는 부분(값을 받는다)
                    bytes = mmInStream.read(buffer);
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        public void write(String msg) {
            try {
                // 값을 쓰는 부분(값을 보낸다)
                mmOutStream.write(msg.getBytes());
                mmOutStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread == null) { }
        else {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread == null) { }
        else {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, device);
        mConnectedThread.start();
        setState(STATE_CONNECTED);
    }
    private void connectionLost() {
        setState(STATE_LISTEN);
        Log.d(TAG, "connectionLost() start");
        if(!isPostDelay){
            isPostDelay = TRUE;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isPostDelay = FALSE;
                    String Address = pref.getString("Address",null);

                    if (Address != null) {
                        Log.d(TAG, "connectionLost() handler try connect");
                        BluetoothDevice bdevice = mAdapter.getRemoteDevice(Address);
                        connect(bdevice);
                    }
                }
            }, 3000 );
        }
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    private synchronized void setInit(BluetoothDevice device){
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("isInit", TRUE);
        editor.putString("Address", device.getAddress());
        editor.commit();
    }
}