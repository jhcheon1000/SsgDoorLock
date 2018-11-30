package com.example.cheonjunhyeon.ssgdoorlock;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import static java.lang.Boolean.FALSE;

public class SplashActivity extends Activity {
    private final String TAG = "SplashActivity";
    private final String[] permissions = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    SharedPreferences pref;
    Boolean isInitPasswd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        initValue();

//        if (!chkPermissions()) {
//            ActivityCompat.requestPermissions(SplashActivity.this, permissions, 100);
//        }

        if (isInitPasswd)
            goChkPasswd();
        else {
            if (!chkPermissions()) {
                ActivityCompat.requestPermissions(SplashActivity.this, permissions, 100);
            }
        }


    }

    private void initValue() {
        pref = getSharedPreferences("pref", MODE_PRIVATE);
        isInitPasswd = pref.getBoolean("isInitPasswd",FALSE);
    }

    private Boolean chkPermissions() {
        Boolean rtn = true;

        for (String perm : permissions) {
            // Here, thisActivity is the current activity
            if (ContextCompat.checkSelfPermission(SplashActivity.this, perm)
                    != PackageManager.PERMISSION_GRANTED) {

                rtn = false;
            }
        }

        return rtn;
    }

    private void goChkPasswd() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
            Intent intent = new Intent(SplashActivity.this, ChkPasswdActivity.class);
            startActivity(intent);
            finish();
            }
        }, 1000);
    }

    private void goSetPasswd() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
            Intent intent = new Intent(SplashActivity.this, SetPasswdActivity.class);
            startActivity(intent);
            finish();
            }
        }, 1000);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults.length > 0) {
                goSetPasswd();
            }
        }
    }
}

