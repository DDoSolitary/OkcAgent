package org.ddosolitary.okcagent

import android.content.Context
import android.content.Intent
import org.openintents.ssh.authentication.ISshAuthenticationService
import org.openintents.ssh.authentication.SshAuthenticationApi
import org.openintents.ssh.authentication.SshAuthenticationConnection

class SshApi(
	private val context: Context,
	private val connectCallback: SshApi.(Boolean) -> Unit
) {
	private var conn: SshAuthenticationConnection? = null
	private var api: SshAuthenticationApi? = null

	fun connect() {
		val pkg = context.getString(R.string.provider_package_id)
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
