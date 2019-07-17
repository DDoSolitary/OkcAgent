package org.ddosolitary.okcagent

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import org.openintents.ssh.authentication.ISshAuthenticationService
import org.openintents.ssh.authentication.SshAuthenticationApi
import org.openintents.ssh.authentication.SshAuthenticationApi.*
import org.openintents.ssh.authentication.SshAuthenticationApiError
import org.openintents.ssh.authentication.SshAuthenticationConnection

private const val REQUEST_PENDING_INTENT = 0
const val EXTRA_SSH_REQUEST = "org.ddosolitary.okcagent.extra.SSH_REQUEST"
const val EXTRA_SSH_RESPONSE = "org.ddosolitary.okcagent.extra.SSH_RESPONSE"
const val EXTRA_RETURN_AS_STATIC = "org.ddosolitary.okcagent.extra.RETURN_AS_STATIC"


class CallSshServiceActivity : Activity() {
	companion object {
		var result: Intent? = null
		val lock = Object()
	}

	private lateinit var conn: SshAuthenticationConnection
	private lateinit var api: SshAuthenticationApi
	private var returnStatic = false

	private fun returnResult(res: Intent?) {
		if (returnStatic) {
			result = res
			synchronized(lock) { lock.notify() }
		} else {
			if (res == null) {
				setResult(RESULT_CANCELED)
			} else {
				setResult(RESULT_OK, Intent().apply {
					putExtra(EXTRA_SSH_RESPONSE, res)
				})
			}
		}
		finish()
	}


	private fun callApi(apiIntent: Intent) {
		val res = api.executeApi(apiIntent)
		when (res.getIntExtra(EXTRA_RESULT_CODE, RESULT_CODE_ERROR)) {
			RESULT_CODE_SUCCESS -> returnResult(res)
			RESULT_CODE_USER_INTERACTION_REQUIRED -> {
				val pi = res.getParcelableExtra<PendingIntent>(EXTRA_PENDING_INTENT)
				startIntentSenderForResult(
					pi?.intentSender ?: return,
					REQUEST_PENDING_INTENT,
					null, 0, 0, 0
				)
			}
			RESULT_CODE_ERROR -> {
				val error = res.getParcelableExtra<SshAuthenticationApiError>(EXTRA_ERROR)
				if (error != null) Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
				returnResult(null)
			}
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == REQUEST_PENDING_INTENT) {
			if (resultCode == RESULT_OK && data != null) {
				callApi(data)
			} else {
				returnResult(null)
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		setFinishOnTouchOutside(false)
		setContentView(R.layout.activity_call_ssh_service)

		returnStatic = intent?.getBooleanExtra(EXTRA_RETURN_AS_STATIC, false) ?: false
		val apiIntent = intent?.getParcelableExtra<Intent>(EXTRA_SSH_REQUEST)
		if (apiIntent == null) {
			TODO("conn not initialized")
			returnResult(null)
			return
		}
		val pref = getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
		val pkg = pref.getString(getString(R.string.key_provider_package), null)
		if (pkg == null) {
			Toast.makeText(this, R.string.text_no_provider, Toast.LENGTH_LONG).show()
			returnResult(null)
			return
		}
		val context = this
		conn = SshAuthenticationConnection(this, pkg)
		conn.connect(object : SshAuthenticationConnection.OnBound {
			override fun onBound(service: ISshAuthenticationService) {
				api = SshAuthenticationApi(context, service)
				callApi(apiIntent)
			}

			override fun onError() {
				TODO("not implemented")
			}
		})
	}

	override fun onDestroy() {
		if (conn.isConnected) conn.disconnect()
		super.onDestroy()
	}
}
