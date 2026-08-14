package com.iar.tardypass.printing.drivers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import com.iar.tardypass.printing.AckLevel
import com.iar.tardypass.printing.PrintResult
import com.iar.tardypass.printing.PrinterCapabilities
import com.iar.tardypass.printing.PrinterDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Built-in thermal printer on SENRAISE handhelds (model H10 and relatives).
 *
 * The preinstalled SRPrinter app (`recieptservice.com.recieptservice`, runs as
 * uid.system) owns /dev/ttyS1 — SELinux blocks third-party apps from touching
 * the port directly — and exposes an *exported, permission-free* AIDL service.
 * We bind it and call `printEpson(byte[])`, which takes raw ESC/POS bytes, so
 * the receipt bytes are identical to every other driver.
 *
 * Interface facts verified by decompiling SRPrinter.apk v8.3.6 (see
 * docs/hardware-h10.md): descriptor and transaction codes below.
 */
class VendorSdkDriver : PrinterDriver {

    override val id = "vendor"
    override val displayName = "Built-in printer (SENRAISE)"

    // The service accepts the job on a render Handler and exposes no paper
    // status, so delivery is confirmable but "paper came out" is not.
    override val capabilities = PrinterCapabilities(ack = AckLevel.DELIVERED)

    private val bindLock = Mutex()
    @Volatile private var binder: IBinder? = null
    @Volatile private var connection: ServiceConnection? = null

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        // Cheap, side-effect-free: does the printer service component exist?
        val intent = Intent().setComponent(ComponentName(PKG, SVC))
        context.packageManager.resolveService(intent, 0) != null
    }

    override suspend fun print(context: Context, job: ByteArray): PrintResult =
        withContext(Dispatchers.IO) {
            val service = try {
                connect(context)
            } catch (e: TimeoutCancellationException) {
                return@withContext PrintResult.Failed("Printer service did not respond to bind")
            } catch (e: Exception) {
                return@withContext PrintResult.Failed("Cannot bind printer service: ${e.message}", e)
            } ?: return@withContext PrintResult.Failed("Printer service unavailable")

            try {
                transactPrintEpson(service, job)
                PrintResult.Sent
            } catch (e: Exception) {
                // A dead binder usually means the service restarted; drop it so the
                // next attempt rebinds instead of reusing a stale handle.
                binder = null
                PrintResult.Failed("Printer service rejected the job: ${e.message}", e)
            }
        }

    /** Reads the vendor service version — handy for Settings diagnostics. */
    suspend fun serviceVersion(context: Context): String? = withContext(Dispatchers.IO) {
        val service = runCatching { connect(context) }.getOrNull() ?: return@withContext null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            service.transact(TX_GET_SERVICE_VERSION, data, reply, 0)
            reply.readException()
            reply.readString()
        } catch (_: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun transactPrintEpson(service: IBinder, job: ByteArray) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeByteArray(job)
            service.transact(TX_PRINT_EPSON, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private suspend fun connect(context: Context): IBinder? {
        binder?.takeIf { it.isBinderAlive }?.let { return it }
        return bindLock.withLock {
            binder?.takeIf { it.isBinderAlive }?.let { return@withLock it }
            withTimeout(BIND_TIMEOUT_MS) { bindSuspending(context) }
        }
    }

    private suspend fun bindSuspending(context: Context): IBinder? =
        suspendCancellableCoroutine { cont ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    binder = service
                    if (cont.isActive) cont.resume(service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    binder = null
                }
            }
            val intent = Intent().setComponent(ComponentName(PKG, SVC))
            val bound = try {
                context.applicationContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
            if (!bound) {
                if (cont.isActive) cont.resume(null)
            } else {
                connection = conn
            }
        }

    private companion object {
        const val PKG = "recieptservice.com.recieptservice"
        const val SVC = "recieptservice.com.recieptservice.service.PrinterService"
        const val DESCRIPTOR = "recieptservice.com.recieptservice.PrinterInterface"

        // Transaction codes read from PrinterInterface$Stub in SRPrinter.apk.
        const val TX_PRINT_EPSON = 1          // printEpson(byte[]) — raw ESC/POS
        const val TX_GET_SERVICE_VERSION = 2  // getServiceVersion() -> String

        const val BIND_TIMEOUT_MS = 5_000L
    }
}
