package org.ddosolitary.okcagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.ddosolitary.okcagent.ssh.SshAgentService

private const val EXTRA_SSH_PROTO_VER = "org.ddosolitary.okcagent.extra.SSH_PROTO_VER"
private const val PROTO_VER = 0

class SshProxyReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val clientProto = intent.getIntExtra(EXTRA_SSH_PROTO_VER, 0)
		if (clientProto != PROTO_VER) {
			showError(
				context,
				context.getString(R.string.error_incompatible_ssh_proto_ver).format(clientProto, PROTO_VER)
			)
			return
		}
		ContextCompat.startForegroundService(
			context,
			Intent(context, SshAgentService::class.java).apply {
				action = ACTION_RUN_AGENT
				putExtra(EXTRA_PROXY_PORT, intent.getIntExtra(EXTRA_PROXY_PORT, -1))
			}
		)
	}
}
