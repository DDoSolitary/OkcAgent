package org.ddosolitary.okcagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

private val NOTIFICATION_ID_COUNTER = AtomicInteger(100000)
private const val NOTIFICATION_GROUP_ERROR = "org.ddosolitary.okcagent.group.ERROR"

fun showError(context: Context, msg: String) {
	if (context is Service) {
		val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			mgr.createNotificationChannel(
				NotificationChannel(
					context.getString(R.string.channel_id_error),
					context.getString(R.string.channel_error),
					NotificationManager.IMPORTANCE_HIGH
				)
			)
		}
		val notification = NotificationCompat.Builder(context, context.getString(R.string.channel_id_error))
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setSmallIcon(R.drawable.ic_error)
			.setContentTitle(context.getString(R.string.text_error))
			.setContentText(msg)
			.setGroup(NOTIFICATION_GROUP_ERROR)
			.build()
		mgr.notify(NOTIFICATION_ID_COUNTER.getAndIncrement(), notification)
	} else {
		context.startActivity(Intent(context, ErrorDialogActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
			putExtra(EXTRA_ERROR_MESSAGE, msg)
		})
	}
}

fun showError(context: Context, resId: Int) = showError(context, context.getString(resId))

fun writeString(output: OutputStream, str: String) {
	val strBuf = str.toByteArray(Charsets.UTF_8)
	val lenBuf = byteArrayOf(
		(strBuf.size shr 8).toByte(),
		(strBuf.size and Byte.MAX_VALUE.toInt()).toByte()
	)
	output.write(lenBuf)
	output.write(strBuf)
	output.flush()
}
