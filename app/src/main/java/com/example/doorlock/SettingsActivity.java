package com.example.doorlock;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "BluetoothConfig";

    // 默认值
    private static final String DEFAULT_DEVICE_NAME = "ESP32Lock";
    private static final String DEFAULT_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String DEFAULT_RX_CHARACTERISTIC_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String PRESS_TIME = "450";
    private static final String RELEASE_TIME = "300";

    private TextInputEditText etDeviceName;
    private TextInputEditText etServiceUuid;
    private TextInputEditText etRxCharacteristicUuid;
    private TextInputEditText etPressTime;
    private TextInputEditText etReleaseTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etDeviceName = findViewById(R.id.et_device_name);
        etServiceUuid = findViewById(R.id.et_service_uuid);
        etRxCharacteristicUuid = findViewById(R.id.et_rx_characteristic_uuid);
        etPressTime = findViewById(R.id.et_press_time);
        etReleaseTime = findViewById(R.id.et_release_time);

        Button btnSave = findViewById(R.id.btn_save);
        Button btnReset = findViewById(R.id.btn_reset);

        // 加载已保存的配置
        loadSettings();

        btnSave.setOnClickListener(v -> saveSettings());
        btnReset.setOnClickListener(v -> resetToDefaults());
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String deviceName = prefs.getString("DEVICE_NAME", DEFAULT_DEVICE_NAME);
        String serviceUuid = prefs.getString("SERVICE_UUID", DEFAULT_SERVICE_UUID);
        String rxCharacteristicUuid = prefs.getString("RX_CHARACTERISTIC_UUID", DEFAULT_RX_CHARACTERISTIC_UUID);
        String pressTime = prefs.getString("PRESS_TIME", PRESS_TIME);
        String releaseTime = prefs.getString("RELEASE_TIME", RELEASE_TIME);

        etDeviceName.setText(deviceName);
        etServiceUuid.setText(serviceUuid);
        etRxCharacteristicUuid.setText(rxCharacteristicUuid);
        etPressTime.setText(pressTime);
        etReleaseTime.setText(releaseTime);

        Log.d(TAG, "配置已加载: " + deviceName + ", " + serviceUuid + ", " + rxCharacteristicUuid+
                ", " + etPressTime+ ", " + etReleaseTime);
    }

    private void saveSettings() {
        String deviceName = Objects.requireNonNull(etDeviceName.getText()).toString().trim();
        String serviceUuid = Objects.requireNonNull(etServiceUuid.getText()).toString().trim();
        String rxCharacteristicUuid = Objects.requireNonNull(etRxCharacteristicUuid.getText()).toString().trim();
        String pressTime = Objects.requireNonNull(etPressTime.getText()).toString().trim();
        String releaseTime = Objects.requireNonNull(etReleaseTime.getText()).toString().trim();

        try {
            Integer.parseInt(pressTime);
            Integer.parseInt(releaseTime);
        } catch (Exception e){
            Toast.makeText(this, "时间格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }

        // 验证UUID格式
        if (isValidUuid(serviceUuid) || isValidUuid(rxCharacteristicUuid)) {
            Toast.makeText(this, "UUID格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            BluetoothConfigManager configManager = BluetoothConfigManager.getInstance(this);
            configManager.updateConfig(this, deviceName, serviceUuid, rxCharacteristicUuid, pressTime, releaseTime);
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "配置已保存: " + deviceName + ", " + serviceUuid + ", " + rxCharacteristicUuid);
        } catch (Exception e) {
            Toast.makeText(this, "保存失败" + e, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetToDefaults() {
        etDeviceName.setText(DEFAULT_DEVICE_NAME);
        etServiceUuid.setText(DEFAULT_SERVICE_UUID);
        etRxCharacteristicUuid.setText(DEFAULT_RX_CHARACTERISTIC_UUID);
        etRxCharacteristicUuid.setText(PRESS_TIME);
        etRxCharacteristicUuid.setText(RELEASE_TIME);

        Toast.makeText(this, "已恢复默认值", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "已恢复默认值");
    }

    private boolean isValidUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return true;
        }

        try {
            // 尝试创建UUID对象来验证格式
            java.util.UUID.fromString(uuid);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}