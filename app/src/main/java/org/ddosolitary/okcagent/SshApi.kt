package org.ddosolitary.okcagent

import android.content.Context
import android.content.Intent
import org.openintents.ssh.authentication.ISshAuthenticationService
import org.openintents.ssh.authentication.SshAuthenticationApi
import org.openintents.ssh.authentication.SshAuthenticationConnection

class SshApi(
	private val context: Context,
	private val connectCallback: SshApi.(Boolean) -> Unit,
	private val packageId: String? = null
) {
	private var conn: SshAuthenticationConnection? = null
	private var api: SshAuthenticationApi? = null

	fun connect() {
		val pref = context.getSharedPreferences(
			context.getString(R.string.pref_main),
			Context.MODE_PRIVATE
		)
		val pkg = packageId ?: pref.getString(context.getString(R.string.key_provider_package), "") ?: ""
		conn = SshAuthenticationConnection(context, pkg).also {
			val connRes = it.connect(object : SshAuthenticationConnection.OnBound {
				override fun onBound(service: ISshAuthenticationService) {
					api = SshAuthenticationApi(context, service)
					connectCallback(true)
				}

				override fun onError() {
					connectCallback(false)
				}
			})
			if (!connRes) connectCallback(false)
		}
	}

	fun disconnect() {
		conn?.let {
			if (it.isConnected) it.disconnect()
		}
	}

	fun executeApi(intent: Intent): Intent? = api?.executeApi(intent)
}
