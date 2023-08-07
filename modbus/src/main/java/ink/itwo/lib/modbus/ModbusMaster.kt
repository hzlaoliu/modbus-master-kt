package ink.itwo.lib.modbus

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream

/** Created by wang on 2023/4/27. */
interface ModbusMaster {
    companion object {
        fun init(config: ModBusMaserConfig, input: InputStream, output: OutputStream): ModbusMaster {
            return when (config.model) {
                ModBusMode.RTU -> ModbusRtu(config, input, output)
                ModBusMode.TCP -> ModbusTCP(config, input, output)
            }
        }

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
    }

    suspend fun onReceive()
    suspend fun read(request: ReadCoilRequest): CoilResponse
    suspend fun read(request: ReadRegisterRequest): RegisterResponse
    suspend fun write(request: WriteRequest): WriteResponse
    fun close()
}

abstract class ModbusMasterAbs(protected val config: ModBusMaserConfig, private val input: InputStream, private val output: OutputStream) : ModbusMaster {
    private var flag = true
    private var lastBuffer: ByteArray? = null
    private var foldBuffer = byteArrayOf()
    private var transmitting = true
    private val byte0: Byte = 0
    private val mutex by lazy { Mutex() }
    override suspend fun onReceive() {
        while (flag) {
            try {
                val byteArray = ByteArray(1024)
                val size = input.read(byteArray)
                val bytes = byteArray.copyOf(size)
                if (config.fold == null) {
                    lastBuffer = bytes
                } else {
                    lastBuffer = config.fold.invoke(bytes)
                }
                config.logger?.invoke("${config.loggerPrefix} rx : ${bytes.toRadix16Separator}   lastBuffer ${lastBuffer?.toRadix16Separator}")
//                fold(bytes)
            } catch (e: Exception) {
                config.logger?.invoke("${config.loggerPrefix} rx : ${e.message} err Exception")
            } finally {
            }
        }
    }

    private suspend fun fold(byteArray: ByteArray) {
        mutex.withLock {
            foldBuffer = foldBuffer.plus(byteArray)
            val crc16 = foldBuffer.crc16
//            config.logger?.invoke("${config.loggerPrefix} fold : \n foldBuffer ${foldBuffer.toRadix16Separator} \n byteArray   ${byteArray.toRadix16Separator}  \n lastBuffer  ${lastBuffer?.toRadix16Separator}  crc16 ${crc16.toRadix16Separator}")
            if (crc16.size == 2 && crc16.firstOrNull() == byte0 && crc16.lastOrNull() == byte0) {
                lastBuffer = foldBuffer.copyOf()
                foldBuffer = byteArrayOf()
                config.logger?.invoke("${config.loggerPrefix} rx :${lastBuffer?.toRadix16Separator}")
                transmitting = true
            }
        }
    }

    protected suspend fun publish(byteArray: ByteArray): ByteArray? {
        try {
            if (!transmitting) {
                config.logger?.invoke("${config.loggerPrefix} tx transmitting = $transmitting")
                return null
            }
            transmitting = true
            config.logger?.invoke("${config.loggerPrefix} tx ${byteArray.toRadix16Separator}")
            output.write(byteArray)
            output.flush()
            transmitting = false
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        val onResult = onResult(byteArray)
        transmitting = true
        return onResult
    }

    private suspend fun onResult(txArray: ByteArray): ByteArray? {
        delay(config.timeout)
        var check = lastBuffer?.let { checkResult(txArray, it) }
        if (check == true) {
            val bytes = lastBuffer?.copyOf()
            lastBuffer = null
            return bytes
        }
        if (config.retryTime == 0L) {
            config.logger?.invoke("${config.loggerPrefix} tx ${txArray.toRadix16Separator}  lastReceive ${lastBuffer?.toRadix16Separator}  err timeout ")
            val bytes = lastBuffer?.copyOf()
            lastBuffer = null
            return bytes
        }
        delay(config.retryTime)
        check = lastBuffer?.let { checkResult(txArray, it) }
        if (check != true) {
            config.logger?.invoke("${config.loggerPrefix} tx ${txArray.toRadix16Separator}  lastReceive ${lastBuffer?.toRadix16Separator}  err timeout ")
            val bytes = lastBuffer?.copyOf()
            lastBuffer = null
            return bytes
        }
        val bytes = lastBuffer?.copyOf()
        lastBuffer = null
        return bytes
    }

    override  fun close() {
        flag = false
    }

    protected abstract fun checkResult(src: ByteArray, receive: ByteArray): Boolean
}


class ModbusRtu(config: ModBusMaserConfig, input: InputStream, output: OutputStream) : ModbusMasterAbs(config, input, output) {


    override suspend fun read(request: ReadCoilRequest): CoilResponse {
        val bytes = publish(request.adu)
        return CoilResponse(bytes).apply { parse() }
    }

    override suspend fun read(request: ReadRegisterRequest): RegisterResponse {
        val bytes = publish(request.adu)
        return RegisterResponse(bytes).apply { parse() }
    }

    override suspend fun write(request: WriteRequest): WriteResponse {
        val bytes = publish(request.adu)
        return WriteResponse(bytes).apply { parse() }
    }

    override fun checkResult(src: ByteArray, receive: ByteArray): Boolean {
        if (receive.isEmpty() || receive.size < 5) {
            config.logger?.invoke("${config.loggerPrefix} tx: ${src.toRadix16Separator} receive: ${receive.toRadix16Separator}  err size < 6 ")
            return false
        }
        val crc16 = receive.crc16
        if (crc16.size != 2 || crc16.firstOrNull()?.toInt() != 0 || crc16.lastOrNull()?.toInt() != 0) {
            config.logger?.invoke("${config.loggerPrefix} tx:${src.toRadix16Separator} rx: ${receive.toRadix16Separator} err CRC ")
            return false
        }
        if (src.elementAtOrNull(1) != receive.elementAtOrNull(1) || src.elementAtOrNull(0) != receive.elementAtOrNull(0)) {
            config.logger?.invoke("${config.loggerPrefix} tx:${src.toRadix16Separator} rx: ${receive.toRadix16Separator}   err fun code diff ")
            return false
        }
        return true
    }
}

class ModbusTCP(config: ModBusMaserConfig, input: InputStream, output: OutputStream) : ModbusMasterAbs(config, input, output) {
    private var serialNum: Int = 0x00
    private val protocolId by lazy { byteArrayOf(0x00, 0x00) }
    private val byte0: Byte = 0


    //传输标识符：用于标识应用数据单元，即请求和应答之间的配对；客户端对该部分进行初始化， x2
    //协议标识符：系统间的协议标识，0=Modbus； x2
    //长度：接下来要发送的数据长度，即：单元标识符+PDU的总长度，以字节为单位； x2
    //单元标识符：用于系统间的站寻址，比如在以太网+串行链路的网络中，远程站的地址； x1
    //0032 0000 0006 01 03 00c8 0011
    override suspend fun read(request: ReadCoilRequest): CoilResponse {
        val byteArray = createRequestByteArray(request)
        val bytes = publish(byteArray)
        return CoilResponse(bytes).apply { parseTcp() }
    }


    override suspend fun read(request: ReadRegisterRequest): RegisterResponse {
        val byteArray = createRequestByteArray(request)
        val bytes = publish(byteArray)
        return RegisterResponse(bytes).apply { parseTcp() }
    }

    //000d 0000 0009 01 10 0304 0001 02 000d
    override suspend fun write(request: WriteRequest): WriteResponse {
        val byteArray = createRequestByteArray(request)
        val bytes = publish(byteArray)
        return WriteResponse(bytes).apply { parseTcp() }
    }

    override fun checkResult(src: ByteArray, receive: ByteArray): Boolean {
        if (receive.isEmpty() || receive.size < 8) {
            config.logger?.invoke("${config.loggerPrefix} err size < 8 tx: ${src.toRadix16Separator} receive: ${receive.toRadix16Separator}")
            return false
        }

        if (receive.elementAtOrNull(2) != byte0 || receive.elementAtOrNull(3) != byte0) {
            config.logger?.invoke("${config.loggerPrefix} err receive protocol fail  tx: ${src.toRadix16Separator} receive: ${receive.toRadix16Separator}")
            return false
        }
        val size = receive.sliceArray(4..5)?.toInt2 ?: 0
        if (size == 0 || size + 6 != receive.size) {
            config.logger?.invoke("${config.loggerPrefix} err receive size fail  tx: ${src.toRadix16Separator} receive: ${receive.toRadix16Separator}")
            return false
        }

        if (src.elementAtOrNull(6) != receive.elementAtOrNull(6)) {
            config.logger?.invoke("${config.loggerPrefix}  err fun code diff tx:${src.toRadix16Separator} rx: ${receive.toRadix16Separator}")
            return false
        }
        return true
    }


    private fun createRequestByteArray(request: Request): ByteArray {
        val transactionId = serialNum++.toByte2
        val pdu = byteArrayOf(request.slaveId).plus(request.pdu)
        val pduSize = pdu.size.toByte2
        return transactionId.plus(protocolId).plus(pduSize).plus(pdu)
    }
}