package org.ddosolitary.okcagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import org.openintents.ssh.authentication.SshAuthenticationApi.*
import org.openintents.ssh.authentication.request.KeySelectionRequest
import org.openintents.ssh.authentication.response.KeySelectionResponse

private const val REQUEST_SELECT_PROVIDER = 0
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

	private fun updateProvider(packageId: String) {
		pref.edit().apply {
			putString(getString(R.string.key_provider_package), packageId)
			apply()
		}
		sshApi?.disconnect()
		sshApi = SshApi(this, {
			if (!it) {
				disconnect()
				showError(this@MainActivity, R.string.error_connect)
			}
		}, packageId).also { it.connect() }
		val info = try {
			packageManager.getApplicationInfo(packageId, 0)
		} catch (e: PackageManager.NameNotFoundException) {
			null
		}
		findViewById<TextView>(R.id.text_selected_provider).apply {
			if (info == null) {
				text = getString(R.string.text_no_provider)
			} else {
				text = info.loadLabel(packageManager)
				setCompoundDrawablesWithIntrinsicBounds(
					info.loadIcon(packageManager),
					null, null, null
				)
			}

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
		updateProvider(pref.getString(getString(R.string.key_provider_package), "") ?: "")
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
				REQUEST_SELECT_PROVIDER ->
					updateProvider(data!!.getStringExtra(EXTRA_PROVIDER_PACKAGE)!!)
				REQUEST_SELECT_KEY -> selectKeyCallback(sshApi?.executeApi(data!!) ?: return)
			}
		}
	}

	fun selectProvider(@Suppress("UNUSED_PARAMETER") view: View) {
		startActivityForResult(
			Intent(this, SelectProviderActivity::class.java),
			REQUEST_SELECT_PROVIDER
		)
	}

	fun selectKey(@Suppress("UNUSED_PARAMETER") view: View) {
		selectKeyCallback(sshApi?.executeApi(KeySelectionRequest().toIntent()) ?: return)
	}
}
