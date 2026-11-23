package com.example.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Gestor Integral de Impresión Térmica Bluetooth Low Energy (BLE).
 * Compatible con impresoras Fun Print / Cat Printer (Modelos MX06, GB01, C9, C15, C19, POS, etc.)
 * Ancho fijo de cabezal térmico: 384 puntos (48 bytes por fila).
 */
class BlePrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "BlePrinterManager"

        // Ancho exacto del cabezal térmico en píxeles (58mm / 48mm útiles = 384 dots)
        const val THERMAL_PRINT_WIDTH_PX = 384
        const val BYTES_PER_LINE = THERMAL_PRINT_WIDTH_PX / 8 // 48 bytes

        // UUIDs estándar del protocolo Fun Print / Cat Printer
        val SERVICE_UUID_FUNPRINT: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val WRITE_CHAR_UUID_FUNPRINT: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

        // UUIDs alternativos para compatibilidad con otros microcontroladores BLE (Jerry/BK)
        val SERVICE_UUID_ALT: UUID = UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb")
        val WRITE_CHAR_UUID_ALT: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")

        // Tabla CRC-8 oficial para el empaquetado de tramas Fun Print
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

        /**
         * Calcula el CRC8 de un arreglo de bytes para el protocolo Fun Print.
         */
        fun calculateCrc8(data: ByteArray): Byte {
            var crc = 0
            for (b in data) {
                crc = CRC8_TABLE[(crc xor (b.toInt() and 0xFF)) and 0xFF]
            }
            return (crc and 0xFF).toByte()
        }

        /**
         * Construye un paquete binario Fun Print:
         * [0x51, 0x78, <cmd>, 0x00, <len_low>, <len_high>, <payload...>, <crc>, 0xFF]
         */
        fun buildFunPrintPacket(cmd: Byte, payload: ByteArray): ByteArray {
            val len = payload.size
            val lenLow = (len and 0xFF).toByte()
            val lenHigh = ((len shr 8) and 0xFF).toByte()
            val crc = calculateCrc8(payload)

            val packet = ByteArray(6 + len + 2)
            packet[0] = 0x51.toByte()
            packet[1] = 0x78.toByte()
            packet[2] = cmd
            packet[3] = 0x00.toByte()
            packet[4] = lenLow
            packet[5] = lenHigh
            System.arraycopy(payload, 0, packet, 6, len)
            packet[6 + len] = crc
            packet[6 + len + 1] = 0xFF.toByte()
            return packet
        }

        /**
         * Comprueba si un nombre de dispositivo BLE coincide con modelos comunes de impresoras térmicas.
         */
        fun isLikelyThermalPrinter(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val upper = name.uppercase(Locale.ROOT)
            val keywords = listOf(
                "MX06", "MX-06", "GB01", "GB-01", "C9", "C15", "C19", "C23",
                "FUNPRINT", "WALKPRINT", "MINI", "POS", "THERMAL", "PRINTER",
                "MTP", "RPP", "MPT", "CAT", "BLE_PRINT", "XP-", "MIPRINT"
            )
            return keywords.any { upper.contains(it) }
        }
    }

    data class DiscoveredDevice(
        val name: String,
        val address: String,
        val rssi: Int,
        val isLikelyPrinter: Boolean,
        val device: BluetoothDevice
    )

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private var scanCallback: ScanCallback? = null

    /**
     * Verifica si la app cuenta con los permisos necesarios para BLE según la versión de Android.
     */
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
     * Inicia el escaneo BLE buscando dispositivos e impresoras térmicas.
     */
    @SuppressLint("MissingPermission")
    fun startScan(onDeviceFound: ((DiscoveredDevice) -> Unit)? = null) {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth no disponible o sin permisos.")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner no disponible.")
            return
        }

        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val address = device.address ?: return@let
                    val name = device.name ?: "Dispositivo BLE Desconocido"
                    val rssi = result.rssi
                    val isPrinter = isLikelyThermalPrinter(name)

                    val discovered = DiscoveredDevice(
                        name = name,
                        address = address,
                        rssi = rssi,
                        isLikelyPrinter = isPrinter,
                        device = device
                    )

                    val currentList = _discoveredDevices.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.address == address }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = discovered
                    } else {
                        currentList.add(discovered)
                    }
                    // Priorizar impresoras probables y mejor señal RSSI
                    _discoveredDevices.value = currentList.sortedWith(
                        compareByDescending<DiscoveredDevice> { it.isLikelyPrinter }
                            .thenByDescending { it.rssi }
                    )
                    onDeviceFound?.invoke(discovered)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Fallo de escaneo BLE con código: $errorCode")
                _isScanning.value = false
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
    }

    /**
     * Detiene el escaneo activo de dispositivos BLE.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (scanCallback != null && bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        }
        scanCallback = null
        _isScanning.value = false
    }

    /**
     * Convierte un Bitmap al formato binario de impresión térmica Fun Print (384px monocromático).
     * Cada fila consta de exactamente 48 bytes (384 bits).
     */
    fun convertBitmapToFunPrintPayload(bitmap: Bitmap): ByteArray {
        // Asegurar que el ancho sea estrictamente 384 píxeles
        val scaledBitmap = if (bitmap.width != THERMAL_PRINT_WIDTH_PX) {
            val targetHeight = (bitmap.height.toFloat() * (THERMAL_PRINT_WIDTH_PX.toFloat() / bitmap.width.toFloat())).toInt()
            Bitmap.createScaledBitmap(bitmap, THERMAL_PRINT_WIDTH_PX, targetHeight.coerceAtLeast(1), true)
        } else {
            bitmap
        }

        val width = scaledBitmap.width
        val height = scaledBitmap.height

        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val output = mutableListOf<ByteArray>()

        // 1. Comando de calentamiento / energía de impresión (0xBE) -> Ajuste de densidad óptima
        val energyPayload = byteArrayOf(0x33.toByte()) // Nivel de energía estándar
        output.add(buildFunPrintPacket(0xBE.toByte(), energyPayload))

        // 2. Comando de velocidad / modo (0xBD)
        val speedPayload = byteArrayOf(0x00.toByte())
        output.add(buildFunPrintPacket(0xBD.toByte(), speedPayload))

        // 3. Procesar líneas del Bitmap a monocromo de 1 bit por píxel (48 bytes por fila)
        // En el protocolo Fun Print, 1 = punto negro (quemado), 0 = blanco (papel limpio)
        for (y in 0 until height) {
            val lineBytes = ByteArray(BYTES_PER_LINE)
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Luminancia perceptual
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val isBlack = luminance < 160 // Umbral de negro con contraste térmico nítido

                if (isBlack) {
                    val byteIndex = x / 8
                    val bitIndex = 7 - (x % 8) // MSB first
                    lineBytes[byteIndex] = (lineBytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }

            // Paquete de fila de bitmap (Comando 0xA2 con 48 bytes)
            val linePacket = buildFunPrintPacket(0xA2.toByte(), lineBytes)
            output.add(linePacket)
        }

        // 4. Comando de avance de papel final (0xA1) para permitir corte limpio
        val feedPayload = byteArrayOf(0x60.toByte(), 0x00.toByte()) // ~96 líneas de avance
        output.add(buildFunPrintPacket(0xA1.toByte(), feedPayload))

        // Combinar todos los paquetes binarios
        val totalSize = output.sumOf { it.size }
        val finalPayload = ByteArray(totalSize)
        var offset = 0
        for (packet in output) {
            System.arraycopy(packet, 0, finalPayload, offset, packet.size)
            offset += packet.size
        }

        return finalPayload
    }

    /**
     * Conecta en segundo plano vía Bluetooth GATT a la impresora seleccionada,
     * transmite las tramas binarias respetando la fragmentación MTU y cierra la conexión.
     */
    @SuppressLint("MissingPermission")
    suspend fun printPayload(
        macAddress: String,
        payload: ByteArray,
        onStatus: ((String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            return@withContext Result.failure(SecurityException("Permisos de Bluetooth no concedidos"))
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth apagado o no disponible"))
        }

        val device = try {
            bluetoothAdapter.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalArgumentException("Dirección MAC inválida: $macAddress"))
        }

        onStatus?.invoke("Conectando con ${device.name ?: macAddress}...")

        return@withContext suspendCoroutine { continuation ->
            var isResumed = false

            fun safeResume(result: Result<Unit>) {
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(result)
                }
            }

            val gattCallback = object : BluetoothGattCallback() {
                var writeChar: BluetoothGattCharacteristic? = null

                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "Error de conexión GATT: $status")
                        gatt?.close()
                        safeResume(Result.failure(Exception("Error de conexión con la impresora (Status: $status)")))
                        return
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        onStatus?.invoke("Conectado. Descubriendo servicios...")
                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt?.close()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                        gatt?.close()
                        safeResume(Result.failure(Exception("Fallo al descubrir servicios de la impresora")))
                        return
                    }

                    // Buscar característica de escritura
                    // 1. Probar UUID estándar Fun Print (0xFF02)
                    var foundChar = gatt.getService(SERVICE_UUID_FUNPRINT)?.getCharacteristic(WRITE_CHAR_UUID_FUNPRINT)
                    
                    // 2. Probar UUID alternativo (0xAE01)
                    if (foundChar == null) {
                        foundChar = gatt.getService(SERVICE_UUID_ALT)?.getCharacteristic(WRITE_CHAR_UUID_ALT)
                    }

                    // 3. Fallback: primera característica con propiedad WRITE / WRITE_NO_RESPONSE
                    if (foundChar == null) {
                        for (service in gatt.services) {
                            for (char in service.characteristics) {
                                val props = char.properties
                                if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                    (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                                ) {
                                    foundChar = char
                                    break
                                }
                            }
                            if (foundChar != null) break
                        }
                    }

                    if (foundChar == null) {
                        gatt.disconnect()
                        gatt.close()
                        safeResume(Result.failure(Exception("No se encontró canal de escritura en la impresora")))
                        return
                    }

                    writeChar = foundChar
                    onStatus?.invoke("Enviando datos de impresión...")

                    // Transmisión en fragmentos de 20 bytes (estándar BLE MTU)
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        try {
                            val chunkSize = 20
                            var offset = 0
                            val total = payload.size

                            while (offset < total) {
                                val end = (offset + chunkSize).coerceAtMost(total)
                                val chunk = payload.copyOfRange(offset, end)

                                foundChar.value = chunk
                                foundChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                gatt.writeCharacteristic(foundChar)

                                offset = end
                                // Pequeña pausa entre paquetes para evitar saturar el buffer BLE
                                delay(12)
                            }

                            // Pausa para asegurar que el buffer físico termine de imprimir
                            delay(500)
                            onStatus?.invoke("¡Impresión completada!")
                            gatt.disconnect()
                            gatt.close()
                            safeResume(Result.success(Unit))
                        } catch (e: Exception) {
                            gatt.disconnect()
                            gatt.close()
                            safeResume(Result.failure(e))
                        }
                    }
                }
            }

            // Conexión directa
            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                safeResume(Result.failure(Exception("No se pudo iniciar la conexión GATT")))
            }
        }
    }

    /**
     * Genera un Bitmap de prueba de 384px para verificar la conexión y calidad de impresión.
     */
    fun generateTestTicketBitmap(prefs: PreferencesManager): Bitmap {
        val width = THERMAL_PRINT_WIDTH_PX
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        var y = 35f
        val xCenter = width / 2f
        val xLeft = 16f
        val xRight = width - 16f

        // Encabezado
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(prefs.storeName, xCenter, y, paint)
        y += 26

        paint.textSize = 15f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("RIF: ${prefs.storeRif}", xCenter, y, paint)
        y += 22
        canvas.drawText("IMPRESIÓN DE PRUEBA BLE", xCenter, y, paint)
        y += 24

        paint.strokeWidth = 2f
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += 24

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 14f
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        canvas.drawText("Fecha: ${sdf.format(Date())}", xLeft, y, paint)
        y += 22
        canvas.drawText("Terminal / Caja: ${prefs.cashRegisterNumber}", xLeft, y, paint)
        y += 22
        canvas.drawText("Estado: CONECTADO OK", xLeft, y, paint)
        y += 22
        canvas.drawText("Protocolo: Fun Print (384 dots)", xLeft, y, paint)
        y += 26

        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += 26

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 16f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("¡Configuración Exitosa!", xCenter, y, paint)
        y += 22
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Sistema POS - Listo para facturar", xCenter, y, paint)
        y += 30

        return bitmap
    }
}
