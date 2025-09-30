package com.example.doorlock;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

public class ServiceLauncherActivity extends Activity {
    private static final String TAG = "ServiceLauncherActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "透明Activity已创建");

        setTheme(android.R.style.Theme_Translucent_NoTitleBar);

        Intent serviceIntent = new Intent(this, BluetoothService.class);
        serviceIntent.setAction(BluetoothService.ACTION_UNLOCK);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "服务启动命令已发送");
        } catch (SecurityException e) {
            Log.e(TAG, "启动服务失败: " + e.getMessage());
        }

        finish();
    }
}