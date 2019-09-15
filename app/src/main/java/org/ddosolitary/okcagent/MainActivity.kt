package org.ddosolitary.okcagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import org.openintents.ssh.authentication.SshAuthenticationApi.*
import org.openintents.ssh.authentication.request.KeySelectionRequest
import org.openintents.ssh.authentication.response.KeySelectionResponse

private const val REQUEST_SELECT_KEY = 1

class MainActivity : Activity() {
	private val pref by lazy {
		getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
	}
	private var sshApi: SshApi? = null

	private fun selectKeyCallback(intent: Intent) {
		val res = KeySelectionResponse(intent)
		when (res.resultCode) {
			RESULT_CODE_SUCCESS -> updateKeyId(res.keyId)
			RESULT_CODE_ERROR -> showError(
				this@MainActivity,
				res.error?.message ?: getString(R.string.error_api)
			)
			RESULT_CODE_USER_INTERACTION_REQUIRED -> startIntentSenderForResult(
				res.pendingIntent.intentSender, REQUEST_SELECT_KEY,
				null, 0, 0, 0
			)
		}
	}

	private fun updateKeyId(keyId: String) {
		pref.edit().apply {
			putString(getString(R.string.key_ssh_key), keyId)
			apply()
		}
		findViewById<TextView>(R.id.text_key).text = getString(R.string.text_ssh_key)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		sshApi = SshApi(this) {
			if (!it) {
				disconnect()
				showError(this@MainActivity, R.string.error_connect)
			}
		}.also { it.connect() }
		findViewById<TextView>(R.id.text_key).setText(
			if (pref.getString(getString(R.string.key_ssh_key), null) == null) {
				R.string.text_no_ssh_key
			} else {
				R.string.text_ssh_key
			}

		)
	}

	override fun onDestroy() {
		sshApi?.disconnect()
		super.onDestroy()
	}


	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (resultCode == RESULT_OK) {
			when (requestCode) {
				REQUEST_SELECT_KEY -> selectKeyCallback(sshApi?.executeApi(data!!) ?: return)
			}
		}
	}

	fun selectKey(@Suppress("UNUSED_PARAMETER") view: View) {
		selectKeyCallback(sshApi?.executeApi(KeySelectionRequest().toIntent()) ?: return)
	}
}
