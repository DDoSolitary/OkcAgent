package org.ddosolitary.okcagent

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val NOTIFICATION_CHANNEL_SERVICE = "agent_services"

fun showError(context: Context, msg: String) {
	AlertDialog.Builder(context)
		.setTitle(R.string.text_error)
		.setMessage(msg)
		.setPositiveButton(R.string.button_ok) { dialog, _ -> dialog.dismiss() }
		.create()
		.show()
}

fun showError(context: Context, resId: Int) = showError(context, context.getString(resId))

fun createNotificationChannel(context: Context) {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		val channel = NotificationChannel(
			NOTIFICATION_CHANNEL_SERVICE,
			context.getString(R.string.channel_service),
			NotificationManager.IMPORTANCE_MIN
		)
		(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
			.createNotificationChannel(channel)
	}
}
