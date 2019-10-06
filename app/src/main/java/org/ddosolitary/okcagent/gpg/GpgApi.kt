package org.ddosolitary.okcagent.gpg

import android.content.Context
import android.content.Intent
import org.ddosolitary.okcagent.R
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception

class GpgApi(
	private val context: Context,
	private val connectCallback: GpgApi.(Boolean) -> Unit
) : Closeable {
	private var conn: OpenPgpServiceConnection? = null
	private var api: OpenPgpApi? = null

	fun connect() {
		val pkg = context.getString(R.string.provider_package_id)
		conn = OpenPgpServiceConnection(context, pkg, object : OpenPgpServiceConnection.OnBound {
			override fun onBound(service: IOpenPgpService2) {
				api = OpenPgpApi(context, service)
				connectCallback(true)
			}

			override fun onError(e: Exception) {
				connectCallback(false)
			}
		}).also { it.bindToService() }
	}

	fun executeApi(intent: Intent, input: InputStream?, output: OutputStream?): Intent?
		= api?.executeApi(intent, input, output)

	override fun close() {
		conn?.let {
			if (it.isBound) it.unbindFromService()
		}
	}
}
