package com.example.brama

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import java.util.Calendar

object Scheduler {
    const val PREFS = "brama"
    const val ACTION_OPEN = "brama.OPEN"
    const val ACTION_CLOSE = "brama.CLOSE"

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun pending(ctx: Context, action: String, code: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(ctx: Context, action: String, code: Int, hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 5000) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, action, code)
        try {
            if (canExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun scheduleAll(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("auto", false)) {
            cancelAll(ctx)
            return
        }
        schedule(ctx, ACTION_OPEN, 1, p.getInt("open_h", 5), p.getInt("open_m", 0))
        schedule(ctx, ACTION_CLOSE, 2, p.getInt("close_h", 23), p.getInt("close_m", 0))
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, ACTION_OPEN, 1))
        am.cancel(pending(ctx, ACTION_CLOSE, 2))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = ctx.getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean("auto", false)) {
            val key = if (intent.action == Scheduler.ACTION_OPEN) "open" else "close"
            val number = p.getString(key, "")?.trim().orEmpty()
            if (number.isNotEmpty() &&
                ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        Scheduler.scheduleAll(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleAll(ctx)
    }
}
