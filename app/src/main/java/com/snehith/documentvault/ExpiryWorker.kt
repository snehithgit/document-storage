package com.snehith.documentvault

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ExpiryWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        return try {
            val store=VaultRepository(applicationContext)
            val today=LocalDate.now()
            val days=applicationContext.getSharedPreferences("vault_options",Context.MODE_PRIVATE).getInt("remindDays",30)
            val expiring=store.cards.mapNotNull { card ->
                try {
                    val date=LocalDate.parse(card.expiry)
                    val remaining=ChronoUnit.DAYS.between(today,date)
                    if(remaining<=days)card to remaining else null
                } catch(_:Exception){null}
            }
            if(expiring.isNotEmpty()) {
                val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(NotificationChannel("expiry","Document expiry",NotificationManager.IMPORTANCE_DEFAULT))
                val intent=PendingIntent.getActivity(applicationContext,0,
                    Intent(applicationContext,MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val body=expiring.take(3).joinToString("; "){(card,days)->
                    card.title+": "+if(days<0)"expired" else days.toString()+"d left"
                }
                val notification=NotificationCompat.Builder(applicationContext,"expiry")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(expiring.size.toString()+" documents need attention")
                    .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(intent).setAutoCancel(true).build()
                if(Build.VERSION.SDK_INT<33 || applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED){
                    manager.notify(7001,notification)
                }
            }
            Result.success()
        } catch(_:Exception){Result.retry()}
    }
    companion object {
        fun schedule(context:Context) {
            val request=PeriodicWorkRequestBuilder<ExpiryWorker>(1,TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("document-expiry",ExistingPeriodicWorkPolicy.KEEP,request)
        }
    }
}
