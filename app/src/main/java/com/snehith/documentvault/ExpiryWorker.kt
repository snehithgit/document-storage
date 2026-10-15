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
            val renewedIds=store.cards.map{it.previousId}.filter{it.isNotBlank()}.toSet()
            val invalid=store.cards.filter { it.expiry.isNotBlank() &&
                runCatching { LocalDate.parse(it.expiry) }.isFailure }
            val expiring=store.cards.filter{it.id !in renewedIds}.mapNotNull { card ->
                try {
                    val date=LocalDate.parse(card.expiry)
                    val remaining=ChronoUnit.DAYS.between(today,date)
                    if(remaining in -30L..days.toLong())card to remaining else null
                } catch(_:Exception){null}
            }.sortedBy{it.second}
            if(expiring.isNotEmpty()||invalid.isNotEmpty()) {
                val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(NotificationChannel("expiry","Document expiry",NotificationManager.IMPORTANCE_DEFAULT))
                val intent=PendingIntent.getActivity(applicationContext,0,
                    Intent(applicationContext,MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val body=expiring.take(3).joinToString("; "){(card,days)->
                    card.title+": "+if(days<0)"expired" else days.toString()+"d left"
                }
                val fullBody=body+(if(invalid.isNotEmpty())
                    (if(body.isNotBlank())"; " else "")+
                        VaultCounts.format(invalid.size,"invalid expiry date")+" — correct in card details" else "")
                val notification=NotificationCompat.Builder(applicationContext,"expiry")
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setSmallIcon(R.drawable.ic_notification)
                                        .setContentTitle(VaultCounts.format(expiring.size+invalid.size,"document")+" need attention")
                    .setContentText(fullBody).setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
                    .setContentIntent(intent).setAutoCancel(true).build()
                if(Build.VERSION.SDK_INT<33 || applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED){
                    manager.notify(7001,notification)
                }
            }
            Result.success()
        } catch(_:Exception){if(runAttemptCount>=2)Result.failure()else Result.retry()}
    }
    companion object {
        fun schedule(context:Context) {
            val request=PeriodicWorkRequestBuilder<ExpiryWorker>(1,TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("document-expiry",ExistingPeriodicWorkPolicy.KEEP,request)
        }
    }
}
