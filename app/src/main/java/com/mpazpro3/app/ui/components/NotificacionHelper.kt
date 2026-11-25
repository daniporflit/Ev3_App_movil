package com.mpazpro3.app.ui.components

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mpazpro3.app.R

object NotificacionHelper {
    private const val CANAL_ID = "informes_docente"

    fun mostrarInformeValidado(contexto: Context, nombreEstudiante: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                "Validación de informes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos cuando el docente valida un informe pedagógico"
            }
            val manager = contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(canal)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificacion = NotificationCompat.Builder(contexto, CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Informe pedagógico validado")
            .setContentText("Se validó correctamente el informe de $nombreEstudiante.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(contexto).notify(nombreEstudiante.hashCode(), notificacion)
    }
}