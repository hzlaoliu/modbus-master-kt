package ink.itwo.lib.modbus

import android.os.Bundle
import android.util.Log
import android.view.View
import android_serialport_api.SerialPort
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private var modeBus: ModbusMaster? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.async(Dispatchers.IO) {
            //RTU模式
            val serialPort = SerialPort(File("/dev/ttyS1"), 115200, 1, 8, 0, 0, 0)
            modeBus = ModbusMaster.init(ModBusMaserConfig(ModBusMode.RTU), serialPort.inputStream, serialPort.outputStream)
            modeBus?.onReceive()

            //TCP模式
//            val socket = Socket("192.168.0.1", 5020)
//            modeBus = ModbusMaster.init(ModBusMaserConfig(ModBusMode.TCP), socket.getInputStream(), socket.getOutputStream())
//            modeBus?.onReceive()
        }
        findViewById<View>(R.id.tv_read_01)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                //读取从站 =1 的线圈，从0开始读取100个
                val response = modeBus?.read(ModbusMaster.createRequestRead01(1, 0, 100))
                Log.d("TAG", "返回的原始数据 ${response?.raw}")
                Log.d("TAG", "返回的 BooleanArray 类型的 result ${response?.result}")
            }
        }

        findViewById<View>(R.id.tv_read_03)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                //读取从站 =1 的保持寄存器，从0开始读20个
                val response = modeBus?.read(ModbusMaster.createRequestRead03(1, 0, 20))
                Log.d("TAG", "返回的原始数据 ${response?.raw}")
                Log.d("TAG", "返回的 ShortArray 类型的 result ${response?.result}")
            }
        }


        findViewById<View>(R.id.tv_write_10)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                //写从站 =1 的多个寄存器，从200开始写入 [98,25,33] 这三个数据
                val response = modeBus?.write(ModbusMaster.createRequestWrite10(1, 200, shortArrayOf(98, 25, 33)))
                Log.d("TAG", "返回的原始数据 ${response?.raw}")
                Log.d("TAG", "返回的 ByteArray 类型的 pdu ${response?.pdu}")
            }
        }

    }
}


