package com.example.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class PrinterConnectionType(val id: String, val title: String, val description: String) {
    BLE_FUNPRINT("BLE_FUNPRINT", "Bluetooth BLE (Fun Print / MX06 / C9)", "Mini impresoras portátiles 384px"),
    BLUETOOTH_CLASSIC("BLUETOOTH_CLASSIC", "Bluetooth Clásico (ESC/POS 58/80mm)", "Impresoras térmicas estándar emparejadas"),
    USB("USB", "Impresora USB (Cable OTG)", "Impresoras térmicas de mostrador 58mm / 80mm")
}

enum class PaperWidthOption(val id: String, val label: String, val widthPx: Int, val charsPerLine: Int) {
    WIDTH_58MM("58mm", "58 mm (Estándar / Portátil)", 384, 32),
    WIDTH_80MM("80mm", "80 mm (Mostrador / Ancho)", 576, 48)
}

data class DiscoveredPrinterDevice(
    val connectionType: PrinterConnectionType,
    val name: String,
    val addressOrId: String,
    val details: String,
    val isLikelyPrinter: Boolean,
    val rssi: Int = 0,
    val usbVendorId: Int = -1,
    val usbProductId: Int = -1,
    val usbDevice: UsbDevice? = null,
    val bluetoothDevice: BluetoothDevice? = null
)

/**
 * Gestor Universal de Impresión Térmica para POS:
 * 1. Bluetooth Low Energy (BLE - Fun Print protocol MX06, GB01, C9)
 * 2. Bluetooth Clásico (ESC/POS SPP - RFCOMM 58mm / 80mm)
 * 3. USB OTG (ESC/POS Bulk Transfer 58mm / 80mm)
 */
class ThermalPrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "ThermalPrinterManager"
        const val ACTION_USB_PERMISSION = "com.example.pos.USB_PERMISSION"

        // UUID estándar SPP (Serial Port Profile) para Bluetooth Clásico ESC/POS
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // UUIDs para BLE Fun Print
        val SERVICE_UUID_FUNPRINT: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val WRITE_CHAR_UUID_FUNPRINT: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        val SERVICE_UUID_ALT: UUID = UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb")
        val WRITE_CHAR_UUID_ALT: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")

        private val CRC8_TABLE = intArrayOf(
            0x00, 0x07, 0x0e, 0x09, 0x1c, 0x1b, 0x12, 0x15, 0x38, 0x3f, 0x36, 0x31, 0x24, 0x23, 0x2a, 0x2d,
            0x70, 0x77, 0x7e, 0x79, 0x6c, 0x6b, 0x62, 0x65, 0x48, 0x4f, 0x46, 0x41, 0x54, 0x53, 0x5a, 0x5d,
            0xe0, 0xe7, 0xee, 0xe9, 0xfc, 0xfb, 0xf2, 0xf5, 0xd8, 0xdf, 0xd6, 0xd1, 0xc4, 0xc3, 0xca, 0xcd,
            0x90, 0x97, 0x9e, 0x99, 0x8c, 0x8b, 0x82, 0x85, 0xa8, 0xaf, 0xa6, 0xa1, 0xb4, 0xb3, 0xba, 0xbd,
            0xc7, 0xc0, 0xc9, 0xce, 0xdb, 0xdc, 0xd5, 0xd2, 0xff, 0xf8, 0xf1, 0xf6, 0xe3, 0xe4, 0xed, 0xea,
            0xb7, 0xb0, 0xb9, 0xbe, 0xab, 0xac, 0xa5, 0xa2, 0x8f, 0x88, 0x81, 0x86, 0x93, 0x94, 0x9d, 0x9a,
            0x27, 0x20, 0x29, 0x2e, 0x3b, 0x3c, 0x35, 0x32, 0x1f, 0x18, 0x11, 0x16, 0x03, 0x04, 0x0d, 0x0a,
            0x57, 0x50, 0x59, 0x5e, 0x4b, 0x4c, 0x45, 0x42, 0x6f, 0x68, 0x61, 0x66, 0x73, 0x74, 0x7d, 0x7a,
            0x89, 0x8e, 0x87, 0x80, 0x95, 0x92, 0x9b, 0x9c, 0xb1, 0xb6, 0xbf, 0xb8, 0xad, 0xaa, 0xa3, 0xa4,
            0xf9, 0xfe, 0xf7, 0xf0, 0xe5, 0xe2, 0xeb, 0xec, 0xc1, 0xc6, 0xcf, 0xc8, 0xdd, 0xda, 0xd3, 0xd4,
            0x69, 0x6e, 0x67, 0x60, 0x75, 0x72, 0x7b, 0x7c, 0x51, 0x56, 0x5f, 0x58, 0x4d, 0x4a, 0x43, 0x44,
            0x19, 0x1e, 0x17, 0x10, 0x05, 0x02, 0x0b, 0x0c, 0x21, 0x26, 0x2f, 0x28, 0x3d, 0x3a, 0x33, 0x34,
            0x4e, 0x49, 0x40, 0x47, 0x52, 0x55, 0x5c, 0x5b, 0x76, 0x71, 0x78, 0x7f, 0x6a, 0x6d, 0x64, 0x63,
            0x3e, 0x39, 0x30, 0x37, 0x22, 0x25, 0x2c, 0x2b, 0x06, 0x01, 0x08, 0x0f, 0x1a, 0x1d, 0x14, 0x13,
            0xae, 0xa9, 0xa0, 0xa7, 0xb2, 0xb5, 0xbc, 0xbb, 0x96, 0x91, 0x98, 0x9f, 0x8a, 0x8d, 0x84, 0x83,
            0xde, 0xd9, 0xd0, 0xd7, 0xc2, 0xc5, 0xcc, 0xcb, 0xe6, 0xe1, 0xe8, 0xef, 0xfa, 0xfd, 0xf4, 0xf3
        )

        fun calculateCrc8(data: ByteArray): Byte {
            var crc = 0
            for (b in data) {
                crc = CRC8_TABLE[(crc xor (b.toInt() and 0xFF)) and 0xFF]
            }
            return (crc and 0xFF).toByte()
        }

        fun buildFunPrintPacket(cmd: Byte, payload: ByteArray): ByteArray {
            val len = payload.size
            val lenLow = (len and 0xFF).toByte()
            val lenHigh = ((len shr 8) and 0xFF).toByte()
            val header = byteArrayOf(0x51, 0x78, cmd, 0x00, lenLow, lenHigh)
            val crcData = header + payload
            val crc = calculateCrc8(crcData)
            return header + payload + byteArrayOf(crc, 0xFF.toByte())
        }
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredPrinterDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val knownPrintersMap = mutableMapOf<String, DiscoveredPrinterDevice>()

    // BLE Scan Callback
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = (device.name ?: result.scanRecord?.deviceName ?: "").trim()
            val address = device.address

            val isLikely = isPrinterDeviceName(name)
            val displayName = name.ifBlank { "Dispositivo BLE ($address)" }

            val item = DiscoveredPrinterDevice(
                connectionType = PrinterConnectionType.BLE_FUNPRINT,
                name = displayName,
                addressOrId = address,
                details = "BLE • Señal: ${result.rssi} dBm",
                isLikelyPrinter = isLikely,
                rssi = result.rssi,
                bluetoothDevice = device
            )

            knownPrintersMap["BLE_$address"] = item
            _discoveredDevices.value = knownPrintersMap.values.sortedWith(
                compareByDescending<DiscoveredPrinterDevice> { it.isLikelyPrinter }
                    .thenByDescending { it.rssi }
            )
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan falló con código: $errorCode")
            _isScanning.value = false
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Escanea e identifica impresoras en todas las vías disponibles:
     * 1. USB conectados físicamente
     * 2. Bluetooth Clásico emparejados (Bonded)
     * 3. Bluetooth BLE activos en el entorno
     */
    @SuppressLint("MissingPermission")
    fun startScan(targetType: PrinterConnectionType? = null) {
        knownPrintersMap.clear()
        _discoveredDevices.value = emptyList()

        // 1. Detección USB OTG
        scanUsbDevices()

        // 2. Detección Bluetooth Clásico (Emparejados en el sistema)
        if (hasBluetoothPermissions() && bluetoothAdapter?.isEnabled == true) {
            try {
                bluetoothAdapter?.bondedDevices?.forEach { dev ->
                    val name = dev.name ?: "Dispositivo Bluetooth"
                    val isLikely = isPrinterDeviceName(name)
                    val item = DiscoveredPrinterDevice(
                        connectionType = PrinterConnectionType.BLUETOOTH_CLASSIC,
                        name = name,
                        addressOrId = dev.address,
                        details = "Bluetooth Clásico (Emparejado)",
                        isLikelyPrinter = isLikely,
                        rssi = -50,
                        bluetoothDevice = dev
                    )
                    knownPrintersMap["BT_${dev.address}"] = item
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error listando dispositivos emparejados: ${e.message}")
            }
        }

        // 3. Detección BLE activa
        if (hasBluetoothPermissions() && bluetoothAdapter?.isEnabled == true) {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner != null) {
                _isScanning.value = true
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                try {
                    scanner.startScan(null, settings, bleScanCallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al iniciar BLE Scan: ${e.message}")
                    _isScanning.value = false
                }
            }
        }

        _discoveredDevices.value = knownPrintersMap.values.sortedWith(
            compareByDescending<DiscoveredPrinterDevice> { it.isLikelyPrinter }
                .thenBy { it.connectionType.ordinal }
        )
    }

    /**
     * Escanea dispositivos USB conectados directamente por cable OTG
     */
    fun scanUsbDevices() {
        val mgr = usbManager ?: return
        try {
            val deviceList = mgr.deviceList
            for ((_, device) in deviceList) {
                val isPrinter = isUsbPrinter(device)
                val vendorName = device.manufacturerName ?: "Dispositivo USB"
                val prodName = device.productName ?: "Impresora Térmica USB"
                val displayName = "$prodName ($vendorName)"

                val item = DiscoveredPrinterDevice(
                    connectionType = PrinterConnectionType.USB,
                    name = displayName,
                    addressOrId = "USB_${device.vendorId}_${device.productId}",
                    details = "USB OTG • VID: ${device.vendorId} PID: ${device.productId}",
                    isLikelyPrinter = isPrinter,
                    usbVendorId = device.vendorId,
                    usbProductId = device.productId,
                    usbDevice = device
                )
                knownPrintersMap["USB_${device.deviceId}"] = item
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando USB: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_isScanning.value) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error deteniendo scan: ${e.message}")
            }
            _isScanning.value = false
        }
    }

    private fun isUsbPrinter(device: UsbDevice): Boolean {
        // USB Class 7 = Printer
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER ||
                intf.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                intf.interfaceClass == UsbConstants.USB_CLASS_PER_INTERFACE ||
                intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                return true
            }
        }
        val name = ((device.productName ?: "") + " " + (device.manufacturerName ?: "")).lowercase()
        return isPrinterDeviceName(name)
    }

    private fun isPrinterDeviceName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.contains("print") ||
                lower.contains("mx0") ||
                lower.contains("gb0") ||
                lower.contains("pos") ||
                lower.contains("c9") ||
                lower.contains("c15") ||
                lower.contains("c19") ||
                lower.contains("mpt") ||
                lower.contains("mtp") ||
                lower.contains("rp") ||
                lower.contains("xp") ||
                lower.contains("epson") ||
                lower.contains("zebra") ||
                lower.contains("bixolon") ||
                lower.contains("cat") ||
                lower.contains("thermal") ||
                lower.contains("bluetooth printer") ||
                lower.contains("58") ||
                lower.contains("80")
    }

    // =========================================================================
    // CONVERSIÓN DE BITMAP A PROTOCOLOS TÉRMICOS (FUN PRINT Y ESC/POS)
    // =========================================================================

    /**
     * Convierte un bitmap en bytes monocromáticos de 1 bit por pixel.
     * Si invert = true, 1 = negro (imprimir) y 0 = blanco.
     */
    fun convertBitmapTo1BitMono(bitmap: Bitmap, targetWidth: Int): Pair<ByteArray, Int> {
        val height = bitmap.height
        val bytesPerRow = targetWidth / 8
        val monoBytes = ByteArray(bytesPerRow * height)

        val threshold = 180 // Umbral de binarización térmica

        for (y in 0 until height) {
            for (x in 0 until targetWidth) {
                val pixel = if (x < bitmap.width) bitmap.getPixel(x, y) else Color.WHITE
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                val isBlack = luminance < threshold
                if (isBlack) {
                    val byteIndex = y * bytesPerRow + (x / 8)
                    val bitPos = 7 - (x % 8)
                    monoBytes[byteIndex] = (monoBytes[byteIndex].toInt() or (1 shl bitPos)).toByte()
                }
            }
        }

        return Pair(monoBytes, height)
    }

    /**
     * Convierte Bitmap a comando estándar ESC/POS Raster (GS v 0) compatible con 58mm y 80mm.
     */
    fun convertBitmapToEscPosRaster(bitmap: Bitmap, widthPx: Int): ByteArray {
        val (monoData, height) = convertBitmapTo1BitMono(bitmap, widthPx)
        val bytesPerRow = widthPx / 8

        val xL = (bytesPerRow and 0xFF).toByte()
        val xH = ((bytesPerRow shr 8) and 0xFF).toByte()
        val yL = (height and 0xFF).toByte()
        val yH = ((height shr 8) and 0xFF).toByte()

        // Encabezado GS v 0 (Raster bit image normal mode: 0)
        val header = byteArrayOf(
            0x1B, 0x40, // ESC @ (Initialize printer)
            0x1B, 0x61, 0x01, // ESC a 1 (Align Center)
            0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH
        )

        // Pie de impresión: avance de papel y corte automático si la impresora lo soporta
        val footer = byteArrayOf(
            0x1B, 0x64, 0x04, // ESC d 4 (Feed 4 lines)
            0x1D, 0x56, 0x42, 0x00 // GS V 66 0 (Cut paper feed)
        )

        return header + monoData + footer
    }

    /**
     * Convierte Bitmap a trama Fun Print BLE (384px)
     */
    fun convertBitmapToFunPrintPayload(bitmap: Bitmap): ByteArray {
        val (monoData, height) = convertBitmapTo1BitMono(bitmap, 384)
        val bytesPerRow = 48
        val out = mutableListOf<Byte>()

        // 1. Comando de velocidad / energía térmica
        val speedPacket = buildFunPrintPacket(0xBD.toByte(), byteArrayOf(0x00.toByte()))
        out.addAll(speedPacket.toList())

        // 2. Envío de líneas térmicas en bloques de 1 a 4 líneas (código 0xA2)
        val linesPerBlock = 1
        var y = 0
        while (y < height) {
            val linesInThisBlock = minOf(linesPerBlock, height - y)
            val blockBytes = ByteArray(linesInThisBlock * bytesPerRow)
            System.arraycopy(monoData, y * bytesPerRow, blockBytes, 0, blockBytes.size)

            val linePacket = buildFunPrintPacket(0xA2.toByte(), blockBytes)
            out.addAll(linePacket.toList())
            y += linesInThisBlock
        }

        // 3. Avance de papel final (código 0xA1)
        val feedPayload = byteArrayOf(0x50, 0x00) // 80 dots feed
        val feedPacket = buildFunPrintPacket(0xA1.toByte(), feedPayload)
        out.addAll(feedPacket.toList())

        return out.toByteArray()
    }

    // =========================================================================
    // IMPRESIÓN UNIVERSAL (DISPATCHER)
    // =========================================================================

    suspend fun printUniversal(
        prefs: PreferencesManager,
        bitmap: Bitmap,
        onStatus: ((String) -> Unit)? = null
    ): Result<Unit> {
        val connTypeStr = prefs.printerConnectionType
        val paperWidthStr = prefs.printerPaperWidth
        val paperWidth = if (paperWidthStr.contains("80")) PaperWidthOption.WIDTH_80MM else PaperWidthOption.WIDTH_58MM

        val connType = try {
            PrinterConnectionType.valueOf(connTypeStr)
        } catch (e: Exception) {
            if (prefs.printerUsbVendorId != -1) PrinterConnectionType.USB
            else PrinterConnectionType.BLE_FUNPRINT
        }

        return when (connType) {
            PrinterConnectionType.BLE_FUNPRINT -> {
                val mac = prefs.printerMacAddress.trim()
                if (mac.isBlank()) {
                    Result.failure(Exception("No se ha configurado la dirección MAC de la impresora BLE"))
                } else {
                    val payload = convertBitmapToFunPrintPayload(bitmap)
                    printBlePayload(mac, payload, onStatus)
                }
            }
            PrinterConnectionType.BLUETOOTH_CLASSIC -> {
                val mac = prefs.printerMacAddress.trim()
                if (mac.isBlank()) {
                    Result.failure(Exception("No se ha configurado la impresora Bluetooth Clásico"))
                } else {
                    val payload = convertBitmapToEscPosRaster(bitmap, paperWidth.widthPx)
                    printBluetoothClassicPayload(mac, payload, onStatus)
                }
            }
            PrinterConnectionType.USB -> {
                val payload = convertBitmapToEscPosRaster(bitmap, paperWidth.widthPx)
                printUsbPayload(prefs.printerUsbVendorId, prefs.printerUsbProductId, payload, onStatus)
            }
        }
    }

    suspend fun printFiscalCommands(
        prefs: PreferencesManager,
        fiscalPayload: ByteArray,
        onStatus: ((String) -> Unit)? = null
    ): Result<Unit> {
        val connTypeStr = prefs.printerConnectionType
        val connType = try {
            PrinterConnectionType.valueOf(connTypeStr)
        } catch (e: Exception) {
            if (prefs.printerUsbVendorId != -1) PrinterConnectionType.USB
            else PrinterConnectionType.BLUETOOTH_CLASSIC
        }

        return when (connType) {
            PrinterConnectionType.BLUETOOTH_CLASSIC -> {
                val mac = prefs.printerMacAddress.trim()
                if (mac.isBlank()) {
                    Result.failure(Exception("No se configuró la dirección MAC de la impresora fiscal Bluetooth"))
                } else {
                    printBluetoothClassicPayload(mac, fiscalPayload, onStatus)
                }
            }
            PrinterConnectionType.USB -> {
                printUsbPayload(prefs.printerUsbVendorId, prefs.printerUsbProductId, fiscalPayload, onStatus)
            }
            else -> {
                val mac = prefs.printerMacAddress.trim()
                if (mac.isNotBlank()) printBluetoothClassicPayload(mac, fiscalPayload, onStatus)
                else printUsbPayload(prefs.printerUsbVendorId, prefs.printerUsbProductId, fiscalPayload, onStatus)
            }
        }
    }

    // =========================================================================
    // IMPRESIÓN BLUETOOTH CLÁSICO (SPP / ESC/POS)
    // =========================================================================

    @SuppressLint("MissingPermission")
    suspend fun printBluetoothClassicPayload(
        macAddress: String,
        payload: ByteArray,
        onStatus: ((String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            return@withContext Result.failure(Exception("Permisos de Bluetooth no concedidos"))
        }

        val adapter = bluetoothAdapter ?: return@withContext Result.failure(Exception("Adaptador Bluetooth no disponible"))
        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("El Bluetooth está apagado"))
        }

        var socket: BluetoothSocket? = null
        try {
            onStatus?.invoke("Buscando dispositivo Bluetooth...")
            val device = adapter.getRemoteDevice(macAddress)
                ?: return@withContext Result.failure(Exception("Dispositivo con MAC $macAddress no encontrado"))

            onStatus?.invoke("Conectando con ${device.name ?: "Impresora"}...")
            // Intento con SPP UUID estándar
            socket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                // Fallback de reflexión si el método estándar falla en ciertos chipsets Android
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            }

            // Cancelar discovery para acelerar la conexión y reducir latencia
            try {
                adapter.cancelDiscovery()
            } catch (_: Exception) {}

            socket.connect()

            onStatus?.invoke("Enviando factura (${payload.size} bytes)...")
            val outputStream: OutputStream = socket.outputStream

            // Transmisión en bloques de 512 bytes para evitar saturar el buffer serial
            val chunkSize = 512
            var offset = 0
            while (offset < payload.size) {
                val len = minOf(chunkSize, payload.size - offset)
                outputStream.write(payload, offset, len)
                outputStream.flush()
                offset += len
                delay(15)
            }

            // Pausa breve para que el cabezal térmico complete la tracción
            delay(150)
            onStatus?.invoke("¡Impresión finalizada!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error en Bluetooth Clásico: ${e.message}", e)
            Result.failure(Exception("Error al conectar e imprimir por Bluetooth: ${e.message}"))
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // IMPRESIÓN USB OTG (ESC/POS)
    // =========================================================================

    suspend fun printUsbPayload(
        targetVendorId: Int,
        targetProductId: Int,
        payload: ByteArray,
        onStatus: ((String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val mgr = usbManager ?: return@withContext Result.failure(Exception("USB Manager no disponible en este dispositivo"))
        val deviceList = mgr.deviceList

        if (deviceList.isEmpty()) {
            return@withContext Result.failure(Exception("No hay ningún dispositivo USB conectado al teléfono"))
        }

        // Buscar dispositivo por VID/PID o tomar la primera impresora detectada
        val targetDevice: UsbDevice = if (targetVendorId != -1 && targetProductId != -1) {
            deviceList.values.firstOrNull { it.vendorId == targetVendorId && it.productId == targetProductId }
                ?: deviceList.values.firstOrNull { isUsbPrinter(it) }
                ?: deviceList.values.first()
        } else {
            deviceList.values.firstOrNull { isUsbPrinter(it) } ?: deviceList.values.first()
        }

        if (!mgr.hasPermission(targetDevice)) {
            return@withContext Result.failure(Exception("Permiso USB no otorgado para ${targetDevice.productName ?: "Impresora USB"}. Desconecte y vuelva a conectar el cable OTG."))
        }

        onStatus?.invoke("Conectando con impresora USB...")

        var connection: UsbDeviceConnection? = null
        var usbInterface: UsbInterface? = null
        var endpointOut: UsbEndpoint? = null

        try {
            // Localizar interfaz y Endpoint OUT (Bulk)
            for (i in 0 until targetDevice.interfaceCount) {
                val intf = targetDevice.getInterface(i)
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        usbInterface = intf
                        endpointOut = ep
                        break
                    }
                }
                if (endpointOut != null) break
            }

            if (usbInterface == null || endpointOut == null) {
                // Fallback: intentar primer endpoint out disponible
                val intf = targetDevice.getInterface(0)
                usbInterface = intf
                endpointOut = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
                    .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
            }

            if (usbInterface == null || endpointOut == null) {
                return@withContext Result.failure(Exception("No se encontró canal de salida (Bulk OUT) en la impresora USB"))
            }

            connection = mgr.openDevice(targetDevice)
                ?: return@withContext Result.failure(Exception("No se pudo abrir la conexión con la impresora USB"))

            if (!connection.claimInterface(usbInterface, true)) {
                return@withContext Result.failure(Exception("No se pudo reclamar la interfaz de la impresora USB"))
            }

            onStatus?.invoke("Transmitiendo ticket a USB (${payload.size} bytes)...")

            // Transmisión en bloques de 1024 bytes
            val chunkSize = 1024
            var offset = 0
            while (offset < payload.size) {
                val len = minOf(chunkSize, payload.size - offset)
                val chunk = ByteArray(len)
                System.arraycopy(payload, offset, chunk, 0, len)
                val sent = connection.bulkTransfer(endpointOut, chunk, len, 5000)
                if (sent < 0) {
                    return@withContext Result.failure(Exception("Error durante la transferencia USB (código: $sent)"))
                }
                offset += len
            }

            delay(150)
            onStatus?.invoke("¡Impresión finalizada!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error imprimiendo por USB: ${e.message}", e)
            Result.failure(Exception("Error al imprimir por USB: ${e.message}"))
        } finally {
            try {
                if (usbInterface != null) connection?.releaseInterface(usbInterface)
                connection?.close()
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // IMPRESIÓN BLE FUN PRINT (MX06 / GB01 / C9 / POS)
    // =========================================================================

    @SuppressLint("MissingPermission")
    suspend fun printBlePayload(
        macAddress: String,
        payload: ByteArray,
        onStatus: ((String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            return@withContext Result.failure(Exception("Permisos de Bluetooth no concedidos"))
        }

        val adapter = bluetoothAdapter ?: return@withContext Result.failure(Exception("Bluetooth no disponible"))
        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("El Bluetooth está apagado"))
        }

        onStatus?.invoke("Conectando con impresora BLE...")

        val result = suspendCoroutine<Result<Unit>> { continuation ->
            var gattRef: BluetoothGatt? = null
            var resumed = false

            fun finish(res: Result<Unit>) {
                if (!resumed) {
                    resumed = true
                    try {
                        gattRef?.disconnect()
                        gattRef?.close()
                    } catch (_: Exception) {}
                    continuation.resume(res)
                }
            }

            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        onStatus?.invoke("Conectado. Descubriendo servicios...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (!resumed) {
                            finish(Result.failure(Exception("Desconectado de la impresora BLE (status $status)")))
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.failure(Exception("Error al descubrir servicios GATT: $status")))
                        return
                    }

                    var targetChar: BluetoothGattCharacteristic? = null
                    val serviceFun = gatt.getService(SERVICE_UUID_FUNPRINT)
                    if (serviceFun != null) {
                        targetChar = serviceFun.getCharacteristic(WRITE_CHAR_UUID_FUNPRINT)
                    }

                    if (targetChar == null) {
                        val serviceAlt = gatt.getService(SERVICE_UUID_ALT)
                        if (serviceAlt != null) {
                            targetChar = serviceAlt.getCharacteristic(WRITE_CHAR_UUID_ALT)
                        }
                    }

                    if (targetChar == null) {
                        for (svc in gatt.services) {
                            for (ch in svc.characteristics) {
                                val props = ch.properties
                                if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                    (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                    targetChar = ch
                                    break
                                }
                            }
                            if (targetChar != null) break
                        }
                    }

                    if (targetChar == null) {
                        finish(Result.failure(Exception("No se encontró característica de escritura BLE en la impresora")))
                        return
                    }

                    onStatus?.invoke("Enviando datos térmicos...")

                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        try {
                            val chunkSize = 20
                            var offset = 0
                            val writeType = if ((targetChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            } else {
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            }

                            while (offset < payload.size) {
                                val len = minOf(chunkSize, payload.size - offset)
                                val chunk = ByteArray(len)
                                System.arraycopy(payload, offset, chunk, 0, len)

                                targetChar.writeType = writeType
                                targetChar.value = chunk
                                val success = gatt.writeCharacteristic(targetChar)

                                if (!success) {
                                    delay(10)
                                    gatt.writeCharacteristic(targetChar)
                                }

                                offset += len
                                delay(7)
                            }

                            delay(300)
                            onStatus?.invoke("¡Impresión finalizada!")
                            finish(Result.success(Unit))
                        } catch (e: Exception) {
                            finish(Result.failure(Exception("Error durante la transmisión BLE: ${e.message}")))
                        }
                    }
                }
            }

            try {
                val device = adapter.getRemoteDevice(macAddress)
                gattRef = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } catch (e: Exception) {
                finish(Result.failure(Exception("No se pudo iniciar conexión BLE con $macAddress: ${e.message}")))
            }
        }

        return@withContext result
    }

    // =========================================================================
    // TICKET DE PRUEBA DE DIAGNÓSTICO ADAPTABLE
    // =========================================================================

    fun generateTestTicketBitmap(prefs: PreferencesManager): Bitmap {
        val paperWidthStr = prefs.printerPaperWidth
        val is80mm = paperWidthStr.contains("80")
        val width = if (is80mm) 576 else 384
        val height = if (is80mm) 580 else 500

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = if (is80mm) 18f else 14f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }

        var y = if (is80mm) 48f else 36f
        val xCenter = width / 2f
        val xLeft = if (is80mm) 30f else 20f
        val xRight = width - (if (is80mm) 30f else 20f)

        // Store Header
        paint.textSize = if (is80mm) 24f else 18f
        paint.isFakeBoldText = true
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }, xCenter, y, paint)
        y += if (is80mm) 30f else 24f

        paint.textSize = if (is80mm) 16f else 13f
        paint.isFakeBoldText = false
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", xCenter, y, paint)
        y += if (is80mm) 24f else 18f
        canvas.drawText("SISTEMA DE FACTURACIÓN POS", xCenter, y, paint)
        y += if (is80mm) 28f else 22f

        // Separator
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 28f else 22f

        paint.textSize = if (is80mm) 20f else 15f
        paint.isFakeBoldText = true
        canvas.drawText("¡PRUEBA DE IMPRESIÓN EXITOSA!", xCenter, y, paint)
        y += if (is80mm) 26f else 20f

        paint.textSize = if (is80mm) 15f else 12f
        paint.isFakeBoldText = false
        val connTitle = when (prefs.printerConnectionType) {
            "USB" -> "Conexión: USB OTG Directo"
            "BLUETOOTH_CLASSIC" -> "Conexión: Bluetooth Clásico ESC/POS"
            else -> "Conexión: Bluetooth BLE (Fun Print)"
        }
        canvas.drawText(connTitle, xCenter, y, paint)
        y += if (is80mm) 22f else 18f
        canvas.drawText("Formato de Papel: ${if (is80mm) "80 mm (576 px)" else "58 mm (384 px)"}", xCenter, y, paint)
        y += if (is80mm) 22f else 18f

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        canvas.drawText("Fecha y Hora: ${sdf.format(Date())}", xCenter, y, paint)
        y += if (is80mm) 24f else 18f

        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 26f else 20f

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = if (is80mm) 15f else 12f
        canvas.drawText("1x Café Espresso Doble", xLeft, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$2.50", xRight, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("1x Torta de Chocolate (Porción)", xLeft, y, paint)
        canvas.drawText("$3.00", xRight, y, paint)
        y += if (is80mm) 22f else 18f

        paint.textAlign = Paint.Align.RIGHT
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL: $5.50 USD", xRight, y, paint)
        y += if (is80mm) 30f else 24f

        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = false
        canvas.drawText("*** IMPRESORA LISTA PARA VENTAS ***", xCenter, y, paint)

        return Bitmap.createBitmap(bitmap, 0, 0, width, (y + 30).toInt())
    }
}
