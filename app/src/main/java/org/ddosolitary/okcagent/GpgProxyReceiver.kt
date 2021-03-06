package org.ddosolitary.okcagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.ddosolitary.okcagent.gpg.GpgAgentService

class GpgProxyReceiver : BroadcastReceiver() {
	companion object {
		private const val EXTRA_GPG_PROTO_VER = "org.ddosolitary.okcagent.extra.GPG_PROTO_VER"
		private const val PROTO_VER = 1
	}

	override fun onReceive(context: Context, intent: Intent) {
		val clientProto = intent.getIntExtra(EXTRA_GPG_PROTO_VER, 0)
		if (clientProto != PROTO_VER) {
			showError(
				context,
				context.getString(R.string.error_incompatible_gpg_proto_ver).format(clientProto, PROTO_VER)
			)
			return
		}
		ContextCompat.startForegroundService(
			context,
			Intent(context, GpgAgentService::class.java).apply {
				action = AgentService.ACTION_RUN_AGENT
				putExtra(AgentService.EXTRA_PROXY_PORT, intent.getIntExtra(AgentService.EXTRA_PROXY_PORT, -1))
				putExtra(GpgAgentService.EXTRA_GPG_ARGS, intent.getStringArrayExtra(GpgAgentService.EXTRA_GPG_ARGS))
			}
		)
	}
}
