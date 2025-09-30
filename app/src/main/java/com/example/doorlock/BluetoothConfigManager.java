package com.example.doorlock;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.UUID;

public class BluetoothConfigManager {
    private static final String TAG = "BluetoothConfigManager";
    private static BluetoothConfigManager instance;

    private String deviceName;
    private UUID serviceUuid;
    private UUID rxCharacteristicUuid;
    private String pressTime;
    private String releaseTime;

    private BluetoothConfigManager(Context context) {
        loadConfig(context);
    }

    public static synchronized BluetoothConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothConfigManager(context);
        }
        return instance;
    }

    private void loadConfig(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("BluetoothConfig", MODE_PRIVATE);

        deviceName = prefs.getString("DEVICE_NAME", "ESP32Lock");
        pressTime = prefs.getString("PRESS_TIME", "450");
        releaseTime = prefs.getString("RELEASE_TIME", "300");

        try {
            serviceUuid = UUID.fromString(prefs.getString("SERVICE_UUID", "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"));
            rxCharacteristicUuid = UUID.fromString(prefs.getString("RX_CHARACTERISTIC_UUID", "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "存储的UUID格式错误，使用默认值", e);
            serviceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
            rxCharacteristicUuid = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
        }

        Log.d(TAG, "加载蓝牙配置: " + deviceName + ", " + serviceUuid + ", " +
                rxCharacteristicUuid+ ", " + pressTime+ ", " + releaseTime);
    }

    // Getter 方法
    public String getDeviceName() {
        return deviceName;
    }

    public UUID getServiceUuid() {
        return serviceUuid;
    }

    public UUID getRxCharacteristicUuid() {
        return rxCharacteristicUuid;
    }

    public String getPressTime() {
        return pressTime;
    }

    public String getReleaseTime() {
        return releaseTime;
    }

    // 更新配置的方法
    public void updateConfig(Context context, String newDeviceName, String newServiceUuid, String
            newRxCharacteristicUuid, String newPressTime, String newReleaseTime) {
        SharedPreferences prefs = context.getSharedPreferences("BluetoothConfig", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("DEVICE_NAME", newDeviceName);
        editor.putString("SERVICE_UUID", newServiceUuid);
        editor.putString("RX_CHARACTERISTIC_UUID", newRxCharacteristicUuid);
        editor.putString("PRESS_TIME", newPressTime);
        editor.putString("RELEASE_TIME", newReleaseTime);
        editor.apply();

        // 重新加载配置
        loadConfig(context);
    }
}