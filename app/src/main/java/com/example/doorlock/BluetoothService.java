package com.example.doorlock;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final String CHANNEL_ID = "BluetoothServiceChannel";
    private BluetoothDevice device;
    private String device_name;
    private UUID serviceUuid;
    private UUID rxCharacteristicUuid;
    private String pressTime;
    private String releaseTime;
    public static final String ACTION_UNLOCK = "ACTION_UNLOCK";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private boolean isConnected = false;
    private static final long CONNECTION_TIMEOUT = 30000;
    private Runnable scanTimeoutRunnable;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private Handler mainHandler;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "蓝牙服务已创建");
        // 加载配置
        BluetoothConfigManager configManager = BluetoothConfigManager.getInstance(this);
        device_name = configManager.getDeviceName();
        serviceUuid = configManager.getServiceUuid();
        rxCharacteristicUuid = configManager.getRxCharacteristicUuid();
        pressTime = configManager.getPressTime();
        releaseTime = configManager.getReleaseTime();
        mainHandler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutRunnable = () -> {
            if (!isConnected) {
                Log.e(TAG, "连接超时");
                disconnectDevice();
                showToast("连接超时，请检查设备是否开启");
            }
        };

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务启动");

        try {
            Notification notification = createNotification();
            Log.d(TAG, "通知创建成功");
            startForeground(1, notification);
            Log.d(TAG, "前台服务已启动");
        } catch (Exception e) {
            Log.e(TAG, "启动前台服务失败", e);
        }

        if (intent != null && ACTION_UNLOCK.equals(intent.getAction())) {
            Log.d(TAG, "收到开锁指令");
            connectAndUnlock();
        }

        return START_STICKY;
    }

    private Notification createNotification() {
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("门锁控制服务")
                .setContentText("正在执行开锁操作...")
                .setOngoing(false);

        try {
            builder.setSmallIcon(R.drawable.lock);
        } catch (Exception e) {
            Log.e(TAG, "无法设置通知图标，使用默认图标", e);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "蓝牙服务通道",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("门锁控制服务通知");

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void saveDeviceInfo(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "缺少蓝牙扫描权限");
            showToast("缺少蓝牙扫描权限");
            return;
        }
        SharedPreferences prefs = getSharedPreferences("BluetoothPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("device_name", device.getName());
        editor.putString("device_address", device.getAddress());
        editor.apply();
        Log.d(TAG, "设备信息已保存");
    }

    private BluetoothDevice loadDeviceInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "缺少蓝牙扫描权限");
            showToast("缺少蓝牙扫描权限");
            return null;
        }
        SharedPreferences prefs = getSharedPreferences("BluetoothPrefs", MODE_PRIVATE);
        String deviceName = prefs.getString("device_name", "");
        String deviceAddress = prefs.getString("device_address", "");

        if (!deviceName.isEmpty() && !deviceAddress.isEmpty()) {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                if (device != null && deviceName.equals(device.getName())) {
                    return device;
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "无效的设备地址: " + e.getMessage());
            }
        }
        return null;
    }

    private void connectAndUnlock() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "缺少蓝牙扫描权限");
            showToast("缺少蓝牙扫描权限");
            return;
        }
        Log.d(TAG, "开始连接门锁");

        if (bluetoothAdapter == null) {
            Log.e(TAG, "蓝牙适配器未初始化");
            showToast("蓝牙适配器不可用");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "蓝牙未启用");
            showToast("请先启用蓝牙");
            return;
        }

        BluetoothDevice savedDevice = loadDeviceInfo();
        if (savedDevice != null) {
            Log.d(TAG, "使用保存的设备连接");
            device = savedDevice;
            connectToDevice(device);
            return;
        }

        startDeviceScan();
    }

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "缺少蓝牙扫描权限");
            showToast("缺少蓝牙扫描权限");
            return;
        }
        Log.d(TAG, "尝试连接设备: " + device.getName());

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "缺少蓝牙连接权限");
                showToast("缺少蓝牙连接权限");
                return;
            }

            // 保存设备信息
            saveDeviceInfo(device);

            bluetoothGatt = device.connectGatt(this, false, gattCallback);
            Log.d(TAG, "正在连接门锁...");
            startConnectionTimeout();
        } catch (SecurityException e) {
            Log.e(TAG, "连接权限错误: " + e.getMessage());
            showToast("连接权限错误: " + e.getMessage());
        }
    }

    private void startDeviceScan() {
        Log.d(TAG, "开始扫描蓝牙设备...");
        showToast("正在扫描门锁设备...");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "缺少蓝牙扫描权限");
            showToast("缺少蓝牙扫描权限");
            return;
        }

        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "无法获取蓝牙扫描器");
            showToast("无法扫描蓝牙设备");
            return;
        }

        ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "缺少蓝牙扫描权限，无法处理扫描结果");
                    return;
                }

                BluetoothDevice device = result.getDevice();
                String deviceName = device.getName();

                if (deviceName != null && deviceName.equals(device_name)) {
                    Log.d(TAG, "找到目标设备: " + deviceName);

                    try {
                        bluetoothLeScanner.stopScan(this);
                        Log.d(TAG,"已停止扫描");

                        // 取消扫描超时任务
                        if (scanTimeoutRunnable != null) {
                            mainHandler.removeCallbacks(scanTimeoutRunnable);
                            Log.d(TAG, "已取消扫描超时任务");
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "停止扫描权限错误: " + e.getMessage());
                    }

                    BluetoothService.this.device = device;
                    connectToDevice(device);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "扫描失败，错误码: " + errorCode);
                showToast("扫描设备失败");

                // 扫描失败时也取消超时任务
                if (scanTimeoutRunnable != null) {
                    mainHandler.removeCallbacks(scanTimeoutRunnable);
                    Log.d(TAG, "扫描失败，已取消扫描超时任务");
                }
            }
        };

        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            Log.d(TAG, "开始扫描");
            bluetoothLeScanner.startScan(null, settings, scanCallback);

            // 创建扫描超时任务
            scanTimeoutRunnable = () -> {
                try {
                    bluetoothLeScanner.stopScan(scanCallback);
                } catch (SecurityException e) {
                    Log.e(TAG, "停止扫描权限错误: " + e.getMessage());
                }
                Log.d(TAG, "扫描超时");
                showToast("未找到门锁设备");
            };

            // 设置扫描超时
            mainHandler.postDelayed(scanTimeoutRunnable, 10000);
        } catch (SecurityException e) {
            Log.e(TAG, "扫描权限错误: " + e.getMessage());
            showToast("扫描权限错误: " + e.getMessage());
        }
    }

    private void sendUnlockCommand() {
        Log.d(TAG, "尝试发送开锁命令");

        if (bluetoothGatt == null) {
            Log.e(TAG, "未连接到设备");
            return;
        }

        try {

            BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
            if (service == null) {
                Log.e(TAG, "未找到服务");
                showToast("未找到服务, 请检查服务UUID是否填写正确");
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(rxCharacteristicUuid);
            if (characteristic == null) {
                Log.e(TAG, "未找到特征");
                showToast("未找到特征, 请检查RX特征UUID是否填写正确");
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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            boolean success = bluetoothGatt.writeCharacteristic(characteristic);
            if (success) {
                Log.d(TAG, "开锁命令发送成功");
                showToast("开锁命令发送成功");
                removeNotification();
                updateWidgetStatus(true);

                mainHandler.postDelayed(() -> {
                    Log.d(TAG, "恢复锁定状态");
                    updateWidgetStatus(false);
                    disconnectDevice();
                }, 2000);
            } else {
                Log.e(TAG, "开锁命令发送失败");
                showToast("开锁命令发送失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "错误: " + e.getMessage());
            showToast("错误: " + e.getMessage());
        }
    }

    private void removeNotification() {
        stopForeground(true);
        Log.d(TAG, "通知已移除");
    }

    private void updateWidgetStatus(final boolean isUnlocked) {
        mainHandler.post(() -> {
            Log.d(TAG, "发送小组件状态更新: " + (isUnlocked ? "门已开" : "门已锁"));

            Intent updateIntent = new Intent(LockControlWidget.ACTION_UPDATE);
            updateIntent.putExtra("isUnlocked", isUnlocked);
            updateIntent.setPackage(getPackageName());
            sendBroadcast(updateIntent);

            Log.d(TAG, "小组件状态更新广播已发送");
        });
    }

    private void startConnectionTimeout() {
        timeoutHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT);
    }

    private void stopConnectionTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
    }

    private void disconnectDevice() {
        stopConnectionTimeout();

        try {
            if (bluetoothGatt != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "缺少蓝牙连接权限");
                    return;
                }
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
                Log.d(TAG, "已断开连接");
            }
        } catch (Exception e) {
            Log.e(TAG, "断开连接错误: " + e.getMessage());
        }

        updateWidgetStatus(false);
        stopSelf();
    }

    private void showToast(final String message) {
        mainHandler.post(() -> Toast.makeText(BluetoothService.this, message, Toast.LENGTH_SHORT).show());
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);
            stopConnectionTimeout();

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                Log.d(TAG, "设备已连接");

                try {
                    bluetoothGatt.discoverServices();
                    Log.d(TAG, "正在发现服务...");
                } catch (SecurityException e) {
                    Log.e(TAG, "发现服务错误: " + e.getMessage());
                    showToast("发现服务错误: " + e.getMessage());
                    disconnectDevice();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                Log.d(TAG, "设备已断开");
                showToast("门锁连接失败");
                updateWidgetStatus(false);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered: status=" + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "服务发现成功");
                sendUnlockCommand();
                List<BluetoothGattService> services = gatt.getServices();
                Log.d(TAG, "发现的服务数量: " + services.size());
            } else {
                Log.e(TAG, "服务发现失败");
                showToast("服务发现失败");
                disconnectDevice();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnectDevice();
        Log.d(TAG, "蓝牙服务已停止");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}