package org.ddosolitary.okcagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat


class ProxyReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		ContextCompat.startForegroundService(
			context,
			Intent(context, AgentService::class.java).apply {
				putExtra(EXTRA_BROADCAST_INTENT, intent)
			}
		)
	}
}
