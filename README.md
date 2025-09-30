# ESP32 Smart Door Lock - Android Controller

## 一个基于 ESP32 和 Android 的智能门锁控制系统，通过蓝牙低功耗（BLE）技术实现手机对门锁的远程控制。

### 功能特点

🚪 智能门锁控制

• 多种触发方式：支持蓝牙、米家开关多种控制方式

• 可调参数：可自定义下压时间、回位时间等电机参数

• 安全冷却：内置冷却时间机制，防止误操作和电机过热

📱 Android 应用

• 蓝牙连接：通过 BLE 与 ESP32 设备通信

• 参数配置：可调节门锁动作参数

• 用户友好：直观的界面设计，易于操作

### 软件栈

• Android 端：Java/Kotlin，使用 Android Bluetooth API

• 通信协议：自定义 JSON 格式 over BLE

### 安装和使用

## Android 应用安装

1. 克隆本项目到 Android Studio
2. 配置蓝牙权限：
   <uses-permission android:name="android.permission.BLUETOOTH"/>
   <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
   <!-- Android 12+ 所需权限 -->
   <uses-permission android:name="android.permission.BLUETOOTH_SCAN"/>
   <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
   
3. 构建并安装应用到 Android 设备

## 使用步骤

1. 打开 Android 应用
2. 确保 ESP32 设备已上电并在广播状态
3. 在应用中扫描并连接 ESP32 设备
4. 配置门锁参数（可选）
5. 点击"开锁"按钮或使用点击小组件

## 通信协议

### BLE 服务特征

• 服务 UUID: 6E400001-B5A3-F393-E0A9-XXX

• RX 特征 (写): 6E400002-B5A3-F393-E0A9-XXX

### 数据格式

应用使用 JSON 格式进行通信：
{
  "press_time": 450,
  "release_time": 300,
  "action": "unlock"
}

## 开发说明

### 自定义参数

您可以通过修改以下常量来自定义门锁行为：
PRESS_TIME = 450     # 下压时间（毫秒）
RELEASE_TIME = 300    # 回位时间（毫秒）

## 常见问题

1. 蓝牙连接失败：检查设备是否在广播状态，Android 权限是否正确
2. 门锁不动作：检查电机接线和电源供应
3. 米家开关无响应：检查开关状态和中断设置

## 贡献指南

### 欢迎提交 Issue 和 Pull Request 来改进这个项目：
1. Fork 本项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 LICENSE 文件。

## 免责声明

本项目仅供学习和研究目的。在实际门锁系统中使用时，请确保：
• 有适当的安全备份机制（如物理钥匙）

• 测试所有故障情况下的行为

• 考虑电源故障时的应急方案

## 支持

如果您在使用过程中遇到问题：
1. 查看 https://github.com/yourusername/esp32-smart-door-lock/issues 中是否已有解决方案
2. 提交新的 Issue，描述详细的问题和环境信息
3. 联系开发团队（如有提供联系方式）

如果您觉得这个项目有用，请给它一个 ⭐ 以表示支持！
