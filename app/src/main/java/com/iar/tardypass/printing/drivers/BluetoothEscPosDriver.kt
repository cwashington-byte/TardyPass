package com.iar.tardypass.printing.drivers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.iar.tardypass.printing.AckLevel
import com.iar.tardypass.printing.PrintResult
import com.iar.tardypass.printing.PrinterCapabilities
import com.iar.tardypass.printing.PrinterDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Classic Bluetooth SPP (RFCOMM) driver for external ESC/POS printers.
 * Uses the configured MAC address, else the first bonded device whose class
 * looks like a printer/imaging device.
 */
class BluetoothEscPosDriver(
    private val configuredAddress: () -> String
) : PrinterDriver {

    override val id = "bluetooth"
    override val displayName = "Bluetooth printer"
    override val capabilities = PrinterCapabilities(ack = AckLevel.DELIVERED)

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    private fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Suppress("MissingPermission")
    private fun pickDevice(context: Context): BluetoothDevice? {
        if (!hasPermission(context)) return null
        val adapter = adapter(context) ?: return null
        if (!adapter.isEnabled) return null
        val bonded = try { adapter.bondedDevices } catch (_: SecurityException) { return null }
        val configured = configuredAddress()
        if (configured.isNotBlank()) return bonded.firstOrNull { it.address.equals(configured, true) }
        // 0x0600 = imaging major class (printers)
        return bonded.firstOrNull { (it.bluetoothClass?.majorDeviceClass ?: 0) == 0x0600 }
    }

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        pickDevice(context) != null
    }

    @Suppress("MissingPermission")
    override suspend fun print(context: Context, job: ByteArray): PrintResult = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext PrintResult.Failed("Bluetooth permission not granted")
        val device = pickDevice(context)
            ?: return@withContext PrintResult.Failed("No paired Bluetooth printer")
        try {
            device.createRfcommSocketToServiceRecord(sppUuid).use { socket ->
                socket.connect()
                socket.outputStream.write(job)
                socket.outputStream.flush()
                // Give the printer's buffer a moment before the socket drops.
                Thread.sleep(150)
            }
            PrintResult.Confirmed
        } catch (e: Exception) {
            PrintResult.Failed("Bluetooth print failed: ${e.message}", e)
        }
    }
}
