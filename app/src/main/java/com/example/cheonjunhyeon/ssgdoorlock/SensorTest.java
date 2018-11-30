package com.example.cheonjunhyeon.ssgdoorlock;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

public class SensorTest extends AppCompatActivity implements SensorEventListener{
    private SensorManager mSensorManager = null;

    public double[] mag_val, acc_val;
    public int mag_cnt;
    public int mag_sample_cnt = 100;
    public int move_cnt, stop_cnt, acc_cnt;
    public int acc_sample_cnt = 100;
    private boolean move_flag;
    private boolean mag_inout_flag;

    TextView mag, acc, result;


    TextView tv;
    ToggleButton tb;

    private double home_longitude; //경도
    private double home_latitude;  //위도
    private boolean home_flag;

    private Kalman mKalmanAccX, mKalmanAccY, mKalmanAccZ;

    private BluetoothAdapter mBluetoothAdapter;
    Button dis;

    private final String[] permissions = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_test);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mag_val = new double[mag_sample_cnt];
        acc_val = new double[3];
        mag_cnt = 0;
        acc_cnt = 0;
        stop_cnt = 0;
        move_cnt = 0;
        move_flag = false;
        mag_inout_flag = false;

        mag = (TextView) findViewById(R.id.mag);
        acc = (TextView) findViewById(R.id.acc);
        result = (TextView) findViewById(R.id.result);

        mKalmanAccX = new Kalman(0.0f);
        mKalmanAccY = new Kalman(0.0f);
        mKalmanAccZ = new Kalman(0.0f);

//        +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        tv = (TextView) findViewById(R.id.textView2);
        tv.setText("위치정보 미수신중");

        tb = (ToggleButton)findViewById(R.id.toggle1);

        // LocationManager 객체를 얻어온다
        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        tb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    if(tb.isChecked()){
                        tv.setText("수신중..");
                        // GPS 제공자의 정보가 바뀌면 콜백하도록 리스너 등록하기~!!!
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, // 등록할 위치제공자
                                100, // 통지사이의 최소 시간간격 (miliSecond)
                                1, // 통지사이의 최소 변경거리 (m)
                                mLocationListener);
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, // 등록할 위치제공자
                                100, // 통지사이의 최소 시간간격 (miliSecond)
                                1, // 통지사이의 최소 변경거리 (m)
                                mLocationListener);
                    }else{
                        tv.setText("위치정보 미수신중");
                        lm.removeUpdates(mLocationListener);  //  미수신할때는 반드시 자원해체를 해주어야 한다.
                    }
                }catch(SecurityException ex){
                }
            }
        });

        home_flag = false;

//        ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        if(!mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.enable(); //강제 활성화
        }

        dis = (Button) findViewById(R.id.bthTestBut);
        dis.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBluetoothAdapter.startDiscovery();
                Log.d("Sibal", "start Discovery");
            }
        });

        if (!chkPermissions()) {
            ActivityCompat.requestPermissions(SensorTest.this, permissions, 100);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float[] values = sensorEvent.values;
        int sensorType = sensorEvent.sensor.getType();

        switch (sensorType) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                if (mag_cnt == mag_sample_cnt) {
                    mag_cnt = 0;
                    double mag_avg = 0;
                    double mag_var = 0;
                    for (int i = 0; i < mag_sample_cnt; i++) {
                        mag_avg += mag_val[i];
                    }
                    mag_avg = mag_avg/mag_sample_cnt;

                    for (int i = 0; i < mag_sample_cnt; i++) {
                        double temp = mag_val[i] - mag_avg;
                        mag_var += Math.pow(temp, 2);
                    }
                    mag_var = mag_var/(mag_sample_cnt - 1);

                    String mag_var_result = String.format("%.1f", mag_var);

                    mag.setText(mag_var_result);
                } else {
                    mag_val[mag_cnt++] = Math.sqrt(values[0]*values[0] + values[1]*values[1] + values[2]*values[2]);
                    String mag_val_result = String.format("%.1f", mag_val[mag_cnt-1]);
                    acc.setText(mag_val_result);
                }
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                if (acc_cnt == acc_sample_cnt) {
                    acc_cnt = 0;
//                    acc.setText("move : " + String.valueOf(move_cnt));
                    result.setText("stop : " + String.valueOf(stop_cnt));
                    move_cnt = 0;
                    stop_cnt = 0;
                }
                else {
                    //                acc_val = Math.sqrt(values[0]*values[0]+values[2]*values[2]); //values[1]*values[1]+
                    double filteredX = 0.0f;
                    double filteredY = 0.0f;
                    double filteredZ = 0.0f;
//                filteredX = mKalmanAccX.update(sensorEvent.values[0]);
//                filteredY = mKalmanAccY.update(sensorEvent.values[1]);
//                filteredZ = mKalmanAccZ.update(sensorEvent.values[2]);

                    filteredY = sensorEvent.values[1];
                    filteredZ = sensorEvent.values[2];
                    filteredX = sensorEvent.values[0];

                    double accVal = Math.sqrt(filteredX*filteredX + filteredY*filteredY + filteredZ*filteredZ);
//                    double accVec = Math.abs(filteredX) + Math.abs(filteredY) + Math.abs(filteredZ);
//                    String acc_result = String.format("%.1f", accVal);
//                    String vec_result = String.format("%.1f", accVec);
//                    acc.setText(acc_result+" m/s2");
//                    result.setText(vec_result);

                    if (accVal > 0.4) move_cnt++;
                    else stop_cnt++;
                    acc_cnt++;
                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private final LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            //여기서 위치값이 갱신되면 이벤트가 발생한다.
            //값은 Location 형태로 리턴되며 좌표 출력 방법은 다음과 같다.

            Log.d("test", "onLocationChanged, location:" + location);
            double longitude = location.getLongitude(); //경도
            double latitude = location.getLatitude();   //위도
            double altitude = location.getAltitude();   //고도
            float accuracy = location.getAccuracy();    //정확도
            String provider = location.getProvider();   //위치제공자
            if (home_flag == false) {
                home_longitude = longitude;
                home_latitude = latitude;
                home_flag = true;
            }
            //Gps 위치제공자에 의한 위치변화. 오차범위가 좁다.
            //Network 위치제공자에 의한 위치변화
            //Network 위치는 Gps에 비해 정확도가 많이 떨어진다.
            tv.setText("위치정보 : " + provider + "\n위도 : " + longitude + "\n경도 : " + latitude
                    + "\n고도 : " + altitude + "\n정확도 : "  + accuracy);
        }
        public void onProviderDisabled(String provider) {
            // Disabled시
            Log.d("test", "onProviderDisabled, provider:" + provider);
        }

        public void onProviderEnabled(String provider) {
            // Enabled시
            Log.d("test", "onProviderEnabled, provider:" + provider);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            // 변경시
            Log.d("test", "onStatusChanged, provider:" + provider + ", status:" + status + " ,Bundle:" + extras);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                if (name == null) {
                    return;
                }
                else if (name.equals("nsl")) {
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    Log.d("Sibal", name + " rssi : " + String.valueOf(rssi));
                    mBluetoothAdapter.cancelDiscovery();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mBluetoothAdapter.startDiscovery();
                }

            }
        }
    };

    private Boolean chkPermissions() {
        Boolean rtn = true;

        for (String perm : permissions) {
            // Here, thisActivity is the current activity
            if (ContextCompat.checkSelfPermission(SensorTest.this, perm)
                    != PackageManager.PERMISSION_GRANTED) {

                rtn = false;
            }
        }

        return rtn;
    }
}

