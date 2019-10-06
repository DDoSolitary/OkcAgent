package org.ddosolitary.okcagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.ddosolitary.okcagent.gpg.EXTRA_GPG_ARGS
import org.ddosolitary.okcagent.gpg.GpgAgentService

class GpgProxyReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		ContextCompat.startForegroundService(
			context,
			Intent(context, GpgAgentService::class.java).apply {
				action = ACTION_RUN_AGENT
				putExtra(EXTRA_PROXY_PORT, intent.getIntExtra(EXTRA_PROXY_PORT, -1))
				putExtra(EXTRA_GPG_ARGS, intent.getStringArrayExtra(EXTRA_GPG_ARGS))
			}
		)
	}
}
