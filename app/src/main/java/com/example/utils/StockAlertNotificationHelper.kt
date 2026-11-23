package com.example.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.Product
import java.util.Locale

object StockAlertNotificationHelper {

    private const val CHANNEL_ID = "inventory_low_stock_channel"
    private const val CHANNEL_NAME = "Alertas de Stock Mínimo"
    private const val CHANNEL_DESC = "Notifica automáticamente cuando un producto alcanza su cantidad mínima en inventario."
    private const val NOTIFICATION_ID = 28601

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun formatStockQuantity(value: Double): String {
        return if (kotlin.math.abs(value - value.toLong()) < 0.001) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    fun notifyLowStockProducts(
        context: Context,
        triggeredProducts: List<Product>,
        allProducts: List<Product>
    ) {
        if (triggeredProducts.isEmpty()) return
        if (!hasPostNotificationPermission(context)) return

        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val body: String
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_inventory_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (triggeredProducts.size == 1) {
            val p = triggeredProducts.first()
            val eff = p.effectiveStockForAlert(allProducts).coerceAtLeast(0.0)
            val min = p.minStockAlertQuantity
            title = if (eff <= 0.0001) {
                "Producto agotado: ${p.name}"
            } else {
                "Stock mínimo: ${p.name}"
            }
            body = "Existencias: ${formatStockQuantity(eff)} u. (Mínimo definido: ${formatStockQuantity(min)} u.)"
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        } else {
            title = "${triggeredProducts.size} productos alcanzaron su stock mínimo"
            val firstLine = triggeredProducts.first().let { p ->
                val eff = p.effectiveStockForAlert(allProducts).coerceAtLeast(0.0)
                "${p.name} (${formatStockQuantity(eff)}/${formatStockQuantity(p.minStockAlertQuantity)} u.)"
            }
            body = "$firstLine y ${triggeredProducts.size - 1} más en inventario."
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
            triggeredProducts.take(6).forEach { p ->
                val eff = p.effectiveStockForAlert(allProducts).coerceAtLeast(0.0)
                val statusTag = if (eff <= 0.0001) "AGOTADO" else "Stock ${formatStockQuantity(eff)} u."
                inboxStyle.addLine("${p.name}: $statusTag (Mín: ${formatStockQuantity(p.minStockAlertQuantity)})")
            }
            if (triggeredProducts.size > 6) {
                inboxStyle.setSummaryText("+${triggeredProducts.size - 6} productos adicionales")
            }
            builder.setStyle(inboxStyle)
        }

        builder.setContentTitle(title).setContentText(body)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }
}
