package org.ddosolitary.okcagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import org.openintents.ssh.authentication.SshAuthenticationApi
import org.openintents.ssh.authentication.request.KeySelectionRequest
import org.openintents.ssh.authentication.request.SigningRequest

private const val REQUEST_SELECT_PROVIDER = 0
private const val REQUEST_SELECT_KEY = 1

class MainActivity : Activity() {
	private lateinit var pref: SharedPreferences

	private fun updateProvider(packageId: String) {
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
			putString(getString(R.string.key_key_id), keyId)
			apply()
		}
		findViewById<TextView>(R.id.text_key).text = getString(R.string.text_key)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		pref = getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
		updateProvider(pref.getString(getString(R.string.key_provider_package), null) ?: "")
		findViewById<TextView>(R.id.text_key).text =
			if (pref.getString(getString(R.string.key_key_id), null) == null) {
				getString(R.string.text_no_key)
			} else {
				getString(R.string.text_key)
			}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (resultCode == RESULT_OK) {
			when (requestCode) {
				REQUEST_SELECT_PROVIDER -> {
					val pkg = data?.getStringExtra(EXTRA_PROVIDER_PACKAGE) ?: return
					pref.edit().apply {
						putString(getString(R.string.key_provider_package), pkg)
						apply()
					}
					updateProvider(pkg)
				}
				REQUEST_SELECT_KEY -> {
					val res = data?.getParcelableExtra<Intent>(EXTRA_SSH_RESPONSE) ?: return
					updateKeyId(res.getStringExtra(SshAuthenticationApi.EXTRA_KEY_ID) ?: return)
				}
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
		startActivityForResult(
			Intent(this, CallSshServiceActivity::class.java).apply {
				putExtra(EXTRA_SSH_REQUEST, KeySelectionRequest().toIntent())
			},
			REQUEST_SELECT_KEY
		)
	}
}
