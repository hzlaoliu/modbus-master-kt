# ModbusMaster-KT 框架

ModbusMaster-KT 框架是一个使用 Kotlin 编写的开源项目，用于实现 Modbus 主站（Master）功能。该框架提供了一组易于使用的 API，帮助开发者快速集成 Modbus 通信功能到他们的应用程序中。

## 特性

- 支持 Modbus TCP 和 Modbus RTU 通信协议。
- 提供了灵活的数据读写接口，包括读取和写入线圈、输入状态、保持寄存器和输入寄存器等功能。
- 支持自定义寄存器地址、数据类型和数据长度。
- 提供了异常处理机制，用于处理 Modbus 从站返回的错误信息。
- 支持多线程操作，可同时处理多个 Modbus 请求。
- 简化了 Modbus 通信的实现细节，减少了开发时间和复杂性。

## 安装

你可以通过以下方式获取该框架：

- 下载源代码并手动集成到你的项目中。
- 使用 Maven 或 Gradle 等构建工具，将该框架添加为依赖项。

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.hzlaoliu:modbus:1.0.0'
}
```

## 使用示例

下面是一个简单的示例代码，展示了如何使用 Modbus Master 框架进行数据读取:

#### 初始化

* 该框架只做 modbus 的数据协议处理，不做链路层的链接。需要自行链接，并用 `inputStream` 和 `outputStream` 初始化框架

```kotlin
//RTU模式
val serialPort = SerialPort(File("/dev/ttyS1"), 115200, 1, 8, 0, 0, 0)
modeBus = ModbusMaster.init(ModBusMaserConfig(ModBusMode.RTU), serialPort.inputStream, serialPort.outputStream)
modeBus?.onReceive()

//TCP模式
val socket = Socket("192.168.0.1", 5020)
modeBus = ModbusMaster.init(ModBusMaserConfig(ModBusMode.TCP), socket.getInputStream(), socket.getOutputStream())
modeBus?.onReceive()
```

#### 支持的读写

```kotlin
/** 读 0x01 ，线圈*/
fun createRequestRead01(slaveId: Byte, start: Int, num: Int): ReadCoilsRequest = ReadCoilsRequest(slaveId, start, num)

/** 读 0x02 ，离散寄存器*/
fun createRequestRead02(slaveId: Byte, start: Int, num: Int): ReadDiscreteRequest = ReadDiscreteRequest(slaveId, start, num)

/** 读 0x03 ，保持寄存器*/
fun createRequestRead03(slaveId: Byte, start: Int, num: Int): ReadHoldRegisterRequest = ReadHoldRegisterRequest(slaveId, start, num)

/** 读 0x04 ，输入寄存器*/
fun createRequestRead04(slaveId: Byte, start: Int, num: Int): ReadInputRegisterRequest = ReadInputRegisterRequest(slaveId, start, num)

/** 写 0x05 ，单个线圈*/
fun createRequestWrite05(slaveId: Byte, start: Int, value: Boolean): WriteCoilOneRequest = WriteCoilOneRequest(slaveId, start, value)

/** 写 0x06，单个寄存器*/
fun createRequestWrite06(slaveId: Byte, start: Int, value: Short): WriteRegisterOneRequest = WriteRegisterOneRequest(slaveId, start, value)

/** 写 0x10 ,多个寄存器*/
fun createRequestWrite10(slaveId: Byte, start: Int, values: ShortArray): WriteRegistersRequest = WriteRegistersRequest(slaveId, start, values)

/** 写 0x0f ，多个线圈*/
fun createRequestWrite0f(slaveId: Byte, start: Int, values: BooleanArray): WriteCoilsRequest = WriteCoilsRequest(slaveId, start, values)
```

#### 读示例

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    //读取从站 =1 的线圈，从0开始读取100个
    val response = modeBus?.read(ModbusMaster.createRequestRead01(1, 0, 100))
    Log.d("TAG", "返回的原始数据 ${response?.raw}")
    Log.d("TAG", "返回的 BooleanArray 类型的 result ${response?.result}")
}

lifecycleScope.launch(Dispatchers.IO) {
    //读取从站 =1 的保持寄存器，从0开始读20个
    val response = modeBus?.read(ModbusMaster.createRequestRead03(1, 0, 20))
    Log.d("TAG", "返回的原始数据 ${response?.raw}")
    Log.d("TAG", "返回的 ShortArray 类型的 result ${response?.result}")
}
```

#### 写示例

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    //写从站 =1 的多个寄存器，从200开始写入 [98,25,33] 这三个数据
    val response = modeBus?.write(ModbusMaster.createRequestWrite10(1, 200, shortArrayOf(98, 25, 33)))
    Log.d("TAG", "返回的原始数据 ${response?.raw}")
    Log.d("TAG", "返回的 ByteArray 类型的 pdu ${response?.pdu}")
}
```

## 贡献

欢迎对该项目进行贡献！如果你发现了 Bug、有新的功能建议或者愿意改进现有功能，请提交 Issue 或 Pull Request。任何形式的贡献都将不胜感激。

## 许可证

该项目采用 MIT 许可证进行许可。