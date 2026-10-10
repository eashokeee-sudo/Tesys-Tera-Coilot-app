package com.tesys.lab

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object UsbHelper {
    private const val ACTION = "com.tesys.lab.USB_PERMISSION"
    const val SCHNEIDER_VID = 0x16DE

    fun manager(ctx: Context): UsbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

    fun devices(ctx: Context): List<UsbDevice> = manager(ctx).deviceList.values.toList()

    fun label(d: UsbDevice): String =
        "%04X:%04X %s".format(d.vendorId, d.productId, d.productName ?: d.deviceName)

    /** Asks Android for USB permission (shows the system dialog) and suspends until answered. */
    suspend fun requestPermission(ctx: Context, device: UsbDevice): Boolean {
        val um = manager(ctx)
        if (um.hasPermission(device)) return true
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.action != ACTION) return
                    runCatching { ctx.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
            ContextCompat.registerReceiver(ctx, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ACTION).setPackage(ctx.packageName), flags)
            um.requestPermission(device, pi)
            cont.invokeOnCancellation { runCatching { ctx.unregisterReceiver(receiver) } }
        }
    }
}
