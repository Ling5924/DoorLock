package com.example.doorlock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DoorLockApp";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;

    private boolean scanning = false;
    private final Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;
    private String device_name;
    private UUID serviceUuid;
    private UUID rxCharacteristicUuid;
    private String pressTime;
    private String releaseTime;
    private Button connectButton;
    private Button unlockButton;
    private TextView statusText;
    private TextView devicesText;

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();
            String deviceName = null;
            String deviceAddress = device.getAddress();

            try {
                if (hasRequiredPermissions()) {
                    deviceName = device.getName();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "安全异常: " + e.getMessage());
            }

            final String finalDeviceName = deviceName;
            final String finalDeviceAddress = deviceAddress;

            if (finalDeviceName != null && finalDeviceName.equals(device_name)) {
                stopScan();
                connectToDevice(device);
            }

            runOnUiThread(() -> {
                String currentText = devicesText.getText().toString();
                if (finalDeviceName != null) {
                    devicesText.setText(currentText + "\n发现设备: " + finalDeviceName + " (" + finalDeviceAddress + ")");
                } else {
                    devicesText.setText(currentText + "\n发现设备: " + finalDeviceAddress);
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "扫描失败，错误代码: " + errorCode);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "蓝牙扫描失败", Toast.LENGTH_SHORT).show();
                scanning = false;
                connectButton.setEnabled(true);
                connectButton.setText("连接门锁");
            });
        }
    };
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台时重新加载配置
        reloadConfiguration();
    }

    private void reloadConfiguration() {
        BluetoothConfigManager configManager = BluetoothConfigManager.getInstance(this);
        String newDeviceName = configManager.getDeviceName();
        UUID newServiceUuid = configManager.getServiceUuid();
        UUID newRxCharacteristicUuid = configManager.getRxCharacteristicUuid();
        pressTime = configManager.getPressTime();
        releaseTime = configManager.getReleaseTime();
        // 检查配置是否发生变化
        boolean configChanged = !device_name.equals(newDeviceName) ||
                !serviceUuid.equals(newServiceUuid) ||
                !rxCharacteristicUuid.equals(newRxCharacteristicUuid);

        if (configChanged) {
            Log.d(TAG, "检测到配置变化，重新加载配置");

            // 更新配置
            device_name = newDeviceName;
            serviceUuid = newServiceUuid;
            rxCharacteristicUuid = newRxCharacteristicUuid;

            // 断开现有连接
            disconnectFromDevice();

            // 更新UI状态
            runOnUiThread(() -> {
                statusText.setText("配置已更新");
                connectButton.setEnabled(true);
                connectButton.setText("连接门锁");
                unlockButton.setEnabled(false);

                Toast.makeText(MainActivity.this, "配置已更新，请重新连接", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void disconnectFromDevice() {
        stopScan();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            } catch (SecurityException e) {
                Log.e(TAG, "安全异常: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "APP初始化");
        // 初始化配置管理器
        BluetoothConfigManager configManager = BluetoothConfigManager.getInstance(this);
        // 使用配置
        device_name = configManager.getDeviceName();
        serviceUuid = configManager.getServiceUuid();
        rxCharacteristicUuid = configManager.getRxCharacteristicUuid();
        connectButton = findViewById(R.id.connectButton);
        unlockButton = findViewById(R.id.unlockButton);
        statusText = findViewById(R.id.statusText);
        devicesText = findViewById(R.id.devicesText);
        unlockButton.setEnabled(false);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        connectButton.setOnClickListener(v -> scanAndConnect());

        unlockButton.setOnClickListener(v -> sendUnlockCommand());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            checkAndRequestPermissions();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = getStrings();
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    private static List<String> getStrings() {
        List<String> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requiredPermissions.add(Manifest.permission.BLUETOOTH);
        requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
        requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE);
        requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE);
        requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION);
        requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        return requiredPermissions;
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void scanAndConnect() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "需要权限才能连接设备", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                checkAndRequestPermissions();
            }
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先启用蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        if (scanning) {
            stopScan();
        } else {
            startScan();
        }
    }

    private void startScan() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "需要权限才能扫描设备", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                checkAndRequestPermissions();
            }
            return;
        }

        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                Toast.makeText(this, "无法启动蓝牙扫描", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        runOnUiThread(() -> {
            devicesText.setText("扫描中...");
            connectButton.setEnabled(false);
            connectButton.setText("扫描中...");
        });

        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            bluetoothLeScanner.startScan(new ArrayList<>(), settings, scanCallback);
            scanning = true;

            handler.postDelayed(() -> {
                if (scanning) {
                    stopScan();
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "扫描超时，未找到设备", Toast.LENGTH_SHORT).show();
                        connectButton.setEnabled(true);
                        connectButton.setText("连接门锁");
                    });
                }
            }, SCAN_PERIOD);

        } catch (SecurityException e) {
            Log.e(TAG, "安全异常: " + e.getMessage());
            Toast.makeText(this, "权限错误，无法扫描设备", Toast.LENGTH_SHORT).show();
            connectButton.setEnabled(true);
            connectButton.setText("连接门锁");
        }
    }

    private void stopScan() {
        if (scanning && bluetoothLeScanner != null) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
            } catch (SecurityException e) {
                Log.e(TAG, "安全异常: " + e.getMessage());
                Toast.makeText(this, "权限错误，无法停止扫描", Toast.LENGTH_SHORT).show();
            }
            scanning = false;
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "需要权限才能连接设备", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                checkAndRequestPermissions();
            }
            return;
        }

        try {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
            statusText.setText("正在连接...");
            connectButton.setEnabled(false);
        } catch (SecurityException e) {
            Log.e(TAG, "安全异常: " + e.getMessage());
            Toast.makeText(this, "权限错误，无法连接设备", Toast.LENGTH_SHORT).show();
            connectButton.setEnabled(true);
            connectButton.setText("连接门锁");
        }
    }

    private void sendUnlockCommand() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "需要权限才能发送命令", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                checkAndRequestPermissions();
            }
            return;
        }

        if (bluetoothGatt == null) {
            Toast.makeText(this, "未连接到设备", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
            if (service == null) {
                Log.e(TAG, "未找到服务");
                Toast.makeText(this, "未找到服务", Toast.LENGTH_SHORT).show();
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(rxCharacteristicUuid);
            if (characteristic == null) {
                Toast.makeText(this, "未找到特征", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject sendMessage = new JSONObject();
            try {
                sendMessage.put("press_time", pressTime);
                sendMessage.put("release_time", releaseTime);
                sendMessage.put("action", "unlock");

                // 将 JSON 对象转换为字符串并发送
                String jsonString = sendMessage.toString();
                characteristic.setValue(jsonString.getBytes(StandardCharsets.UTF_8));

                Log.d(TAG, "发送 JSON 数据: " + jsonString);
            } catch (JSONException e) {
                Log.e(TAG, "创建 JSON 数据时出错: " + e.getMessage());
            }
            characteristic.setValue(sendMessage.toString().getBytes());
            Log.d(TAG, "sendMessage" + sendMessage);
            boolean success = bluetoothGatt.writeCharacteristic(characteristic);
            if (success) {
                Toast.makeText(this, "发送开锁命令", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "安全异常: " + e.getMessage());
            Toast.makeText(this, "权限错误，无法发送命令", Toast.LENGTH_SHORT).show();
        }
    }



    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            runOnUiThread(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        bluetoothGatt.discoverServices();
                        statusText.setText("发现服务中...");
                    } catch (SecurityException e) {
                        Log.e(TAG, "安全异常: " + e.getMessage());
                        statusText.setText("权限错误");
                        connectButton.setEnabled(true);
                        connectButton.setText("连接门锁");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectButton.setEnabled(true);
                    connectButton.setText("连接门锁");
                    unlockButton.setEnabled(false);
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            runOnUiThread(() -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    try {
                        List<BluetoothGattService> services = gatt.getServices();
                        boolean serviceFound = false;

                        for (BluetoothGattService service : services) {
                            if (service.getUuid().equals(serviceUuid)) {
                                serviceFound = true;
                                break;
                            }
                        }

                        if (serviceFound) {
                            statusText.setText("已连接");
                            unlockButton.setEnabled(true);
                            connectButton.setText("已连接");
                        } else {
                            Log.e(TAG, "服务未找到");
                            statusText.setText("服务未找到");
                            try {
                                gatt.disconnect();
                            } catch (SecurityException e) {
                                Log.e(TAG, "安全异常: " + e.getMessage());
                            }
                            connectButton.setEnabled(true);
                            connectButton.setText("连接门锁");
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "安全异常: " + e.getMessage());
                        statusText.setText("权限错误");
                        connectButton.setEnabled(true);
                        connectButton.setText("连接门锁");
                    }
                } else {
                    statusText.setText("服务发现失败");
                    Log.e(TAG, "服务发现失败");
                    try {
                        gatt.disconnect();
                    } catch (SecurityException e) {
                        Log.e(TAG, "安全异常: " + e.getMessage());
                    }
                    connectButton.setEnabled(true);
                    connectButton.setText("连接门锁");
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            runOnUiThread(() -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Toast.makeText(MainActivity.this, "命令发送成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "命令发送失败", Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
            } catch (SecurityException e) {
                Log.e(TAG, "安全异常: " + e.getMessage());
            }
            try {
                bluetoothGatt.close();
            } catch (SecurityException e) {
                Log.e(TAG, "安全异常: " + e.getMessage());
            }
        }
    }
}