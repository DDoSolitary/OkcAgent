package org.ddosolitary.okcagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.ddosolitary.okcagent.ssh.SshAgentService

class SshProxyReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		ContextCompat.startForegroundService(
			context,
			Intent(context, SshAgentService::class.java).apply {
				action = ACTION_RUN_AGENT
				putExtra(EXTRA_PROXY_PORT, intent.getIntExtra(EXTRA_PROXY_PORT, -1))
			}
		)
	}
}
