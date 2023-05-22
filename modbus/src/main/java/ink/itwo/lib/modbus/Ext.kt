package ink.itwo.lib.modbus

import kotlin.experimental.and
import kotlin.experimental.or


enum class ModBusMode { RTU, TCP }

data class ModBusMaserConfig(
    val model: ModBusMode,
    val timeout: Long = 100,
    val retryTime: Long = 0,
    val loggerPrefix: String = "Modbus ",
    val logger: ((String) -> Unit)? = null,
)


private object ModBusFunCode {

    /** 读 0x01 ，线圈*/
    const val ReadCoils: Byte = 0x01

    /** 读 0x02 ，离散寄存器*/
    const val ReadDiscrete: Byte = 0x02

    /** 读 0x03 ，保持寄存器*/
    const val ReadHoldRegister: Byte = 0x03

    /** 读 0x04 ，输入寄存器*/
    const val ReadInputRegister: Byte = 0x04

    /** 写 0x05 ，单个线圈*/
    const val WriteCoilOne: Byte = 0x05

    /** 写 0x06，单个寄存器*/
    const val WriteRegisterOne: Byte = 0x06

    /** 写 0x10 ,多个寄存器*/
    const val WriteRegisters: Byte = 0x10

    /** 写 0x0f ，多个线圈*/
    const val WriteCoils: Byte = 0x0f

    /** 读 0x14 ,文件记录*/
    const val ReadFile: Byte = 0x14

    /** 写 0x15 ,文件记录*/
    const val WriteFile: Byte = 0x015
}

interface Request {
    val slaveId: Byte
    val funCode: Byte
}

interface ReadRequest : Request {
    val start: Int
    val num: Int
}

interface Response {
    var raw: ByteArray?
    var pdu: ByteArray?
}

interface ReadCoilRequest : ReadRequest

interface ReadRegisterRequest : ReadRequest

interface WriteRequest : Request

class CoilResponse(override var raw: ByteArray?, override var pdu: ByteArray? = null, var result: BooleanArray? = null) : Response


class RegisterResponse(override var raw: ByteArray?, override var pdu: ByteArray? = null, var result: ShortArray? = null) : Response

class WriteResponse(override var raw: ByteArray?, override var pdu: ByteArray? = null) : Response

class ReadCoilsRequest(override val slaveId: Byte, override val start: Int, override val num: Int) : ReadCoilRequest {
    override val funCode = ModBusFunCode.ReadCoils
}

class ReadDiscreteRequest(override val slaveId: Byte, override val start: Int, override val num: Int) : ReadCoilRequest {
    override val funCode = ModBusFunCode.ReadDiscrete
}

class ReadHoldRegisterRequest(override val slaveId: Byte, override val start: Int, override val num: Int) : ReadRegisterRequest {
    override val funCode = ModBusFunCode.ReadHoldRegister
}

class ReadInputRegisterRequest(override val slaveId: Byte, override val start: Int, override val num: Int) : ReadRegisterRequest {
    override val funCode = ModBusFunCode.ReadInputRegister
}


class WriteCoilOneRequest(override val slaveId: Byte, val start: Int, val value: Boolean) : WriteRequest {
    override val funCode = ModBusFunCode.WriteCoilOne
}

class WriteRegisterOneRequest(override val slaveId: Byte, val start: Int, val value: Short) : WriteRequest {
    override val funCode = ModBusFunCode.WriteRegisterOne
}

class WriteRegistersRequest(override val slaveId: Byte, val start: Int, val values: ShortArray) : WriteRequest {
    override val funCode = ModBusFunCode.WriteRegisters
}

class WriteCoilsRequest(override val slaveId: Byte, val start: Int, val values: BooleanArray) : WriteRequest {
    override val funCode = ModBusFunCode.WriteCoils
}


internal val Request.pdu: ByteArray
    get() {
        return when (this) {
            // code 1 start 2 num 2
            is ReadRequest -> byteArrayOf(funCode).plus(start.toByte2).plus(num.toByte2)
            // code 1 start 2 value 2 (0xFF00 = on 0x0000 = off)
            is WriteCoilOneRequest -> byteArrayOf(funCode).plus(start.toByte2).plus(if (value) (0xFF).toByte() else 0x00).plus(0x00)
            // code 1 start 2 value 2
            is WriteRegisterOneRequest -> byteArrayOf(funCode).plus(start.toByte2).plus(value.toInt().toByte2)
            // code 1 start 2 寄存器数量 2 字节数 1 寄存器值 N*2
            is WriteRegistersRequest -> {
                val funCodeByte = byteArrayOf(funCode)
                val startBytes = start.toByte2
                val numBytes = values.size.toByte2
                val countBytes = (values.size * 2).toByte()
                val valueBytes = values.toByteArray
                return funCodeByte.plus(startBytes).plus(numBytes).plus(countBytes).plus(valueBytes)
            }
            // code 1 start 2 输出数量 2 字节数 1 写入值 N*1
            is WriteCoilsRequest -> {
                val funCodeByte = byteArrayOf(funCode)
                val startBytes = start.toByte2
                val numBytes = values.size.toByte2
                val valueBytes = values.toByteArrayFill0
                val countBytes = (valueBytes.size).toByte()
                return funCodeByte.plus(startBytes).plus(numBytes).plus(countBytes).plus(valueBytes)
            }
            else -> byteArrayOf()
        }
    }
internal val Request.adu: ByteArray
    get() = byteArrayOf(slaveId).plus(pdu).plusCrc16

internal fun Response.parse() {
    when (this) {
        is CoilResponse -> {
            pdu = raw?.elementAtOrNull(2)?.let { raw?.sliceArray(1..2 + it) }
            result = raw?.elementAtOrNull(2)?.let { raw?.sliceArray(3..2 + it) }?.toBooleanArray
        }
        is RegisterResponse -> {
            pdu = raw?.elementAtOrNull(2)?.let { raw?.sliceArray(1..2 + it) }
            result = raw?.elementAtOrNull(2)?.let { raw?.sliceArray(3..2 + it) }?.toShortArray
        }
        is WriteResponse -> {
            pdu = raw?.elementAtOrNull(2)?.let { raw?.sliceArray(1..2 + it) }
        }
    }
}

internal fun Response.parseTcp() {
    //000d 0000 0009 01 10 0304 0001 02 000d
    when (this) {
        is CoilResponse -> {
            pdu = raw?.sliceArray(6 until (raw?.size ?: 6))
            result = raw?.elementAtOrNull(2)?.let { raw?.sliceArray(3..2 + it) }?.toBooleanArray
            result = raw?.sliceArray(8 until (raw?.size ?: 8))?.toBooleanArray
        }
        is RegisterResponse -> {
            pdu = raw?.sliceArray(6 until (raw?.size ?: 6))
            result = raw?.sliceArray(8 until (raw?.size ?: 8))?.toShortArray
        }
        is WriteResponse -> {
            pdu = raw?.sliceArray(6 until (raw?.size ?: 6))
        }
    }
}


internal val Int.toByte2: ByteArray
    get() = byteArrayOf(this.shr(8).and(0xFF).toByte(), this.and(0xFF).toByte())
internal val ByteArray.crc16: ByteArray
    get() = getCrc16Str(this, false)
private val ByteArray.plusCrc16: ByteArray
    get() = this.plus(this.crc16)
private val ShortArray.toByteArray: ByteArray
    get() {
        val byteArray = ByteArray(this.size * 2)
        forEachIndexed { index, sh ->
            byteArray[(index * 2)] = sh.toInt().shr(8).and(0xFF).toByte()
            byteArray[(index * 2) + 1] = sh.and(0xFF).toByte()
        }
        return byteArray
    }
internal val ByteArray.toInt2: Int
    get() {
        return (this[1].toInt() and 0xFF) shl 8 or (this[0].toInt() and 0xFF)
    }

/** BooleanArray 按8位一组转成ByteArray，不足8位补0*/
private val BooleanArray.toByteArrayFill0: ByteArray
    get() {
        val result = ByteArray((size / 8) + if (size % 8 != 0) 1 else 0)
        for (i in indices) {
            if (this[i]) {
                result[i / 8] = result[i / 8] or (1 shl (i % 8)).toByte()
            }
        }
        return result
    }

/** 将 byteArray 转成 booleanArray*/
internal val ByteArray.toBooleanArray: BooleanArray
    get() {
        val result = BooleanArray(size * 8)
        for (i in indices) {
            for (j in 0 until 8) {
                result[i * 8 + j] = (this[i].toInt() shr j) and 0x1 == 1
            }
        }
        return result
    }
internal val ByteArray.toShortArray: ShortArray
    get() {
        val result = ShortArray(size / 2)
        for (i in 0 until size step 2) {
            result[i / 2] = ((this[i].toInt() and 0xFF) shl 8 or (this[i + 1].toInt() and 0xFF)).toShort()
        }
        return result
    }


private val UByte.toRadix16: String
    get() = if (toString(16).length < 2) "0${toString(16)}" else toString(16)
private val ByteArray.toRadix16: String
    get() = joinToString(separator = "") { it.toUByte().toRadix16 }
internal val ByteArray.toRadix16Separator: String
    get() = joinToString(separator = " ") { it.toUByte().toRadix16 }


private fun getCrc16Str(arrBuff: ByteArray, littleEndian: Boolean = true): ByteArray {
    val len = arrBuff.size
    var crc = 0xFFFF
    for (i in 0 until len) {
        crc = ((crc and 0xFF00) or (crc and 0x00FF) xor (arrBuff[i].toInt() and 0xFF))
        for (j in 0 until 8) {
            if ((crc and 0x0001) > 0) {
                crc = crc.shr(1)
                crc = crc xor 0xA001
            } else {
                crc = crc.shr(1)
            }
        }
    }
    val result = ByteArray(2)
    result[if (littleEndian) 0 else 1] = (crc.shr(8) and 0xFF).toByte()
    result[if (littleEndian) 1 else 0] = (crc and 0xFF).toByte()
    return result
}

internal fun ByteArray.sliceArray(indices: IntRange): ByteArray? {
    if (this.size <= indices.last) return null
    if (indices.isEmpty()) return null
    return copyOfRange(indices.first, indices.last + 1)
}
