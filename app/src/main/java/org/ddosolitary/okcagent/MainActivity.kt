package org.ddosolitary.okcagent

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ddosolitary.okcagent.gpg.GpgApi
import org.ddosolitary.okcagent.ssh.SshApi
import org.ddosolitary.okcagent.ssh.SshKeyInfo
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi.*
import org.openintents.ssh.authentication.request.KeySelectionRequest
import org.openintents.ssh.authentication.response.KeySelectionResponse


private const val REQUEST_SELECT_SSH_KEY = 1
private const val REQUEST_SELECT_GPG_KEY = 2

class MainActivity : AppCompatActivity() {
	private class SshKeyViewHolder(val view: View) : RecyclerView.ViewHolder(view)

	private class SshKeyListAdaptor : RecyclerView.Adapter<SshKeyViewHolder>() {
		var keys = mutableListOf<SshKeyInfo>()

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SshKeyViewHolder {
			val view = LayoutInflater.from(parent.context).inflate(R.layout.view_ssh_key, parent, false)
			return SshKeyViewHolder(view)
		}

		override fun getItemCount(): Int {
			return if (keys.size == 0) 1 else keys.size
		}

		override fun onBindViewHolder(holder: SshKeyViewHolder, position: Int) {
			val textView = holder.view.findViewById<TextView>(R.id.text_ssh_key)
			val button = holder.view.findViewById<ImageButton>(R.id.button_remove)
			if (keys.size == 0) {
				textView.text = holder.view.context.getString(R.string.text_no_ssh_key)
				button.visibility = View.GONE
				button.setOnClickListener(null)
			} else {
				textView.text = keys[position].description
				button.visibility = View.VISIBLE
				button.setOnClickListener {
					keys.removeAt(holder.adapterPosition)
					SshKeyInfo.save(keys, it.context)
					if (keys.size == 0) {
						notifyItemChanged(0)
					} else {
						notifyItemRemoved(holder.adapterPosition)
					}
				}
			}
		}
	}

	private val pref by lazy {
		getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
	}
	private var sshApi: SshApi? = null
	private var gpgApi: GpgApi? = null
	private val adaptor = SshKeyListAdaptor()

	private fun addSshKeyCallback(intent: Intent) {
		val res = KeySelectionResponse(intent)
		when (res.resultCode) {
			RESULT_CODE_SUCCESS -> addSshKey(SshKeyInfo(res.keyId, res.keyDescription))
			RESULT_CODE_ERROR -> showError(
				this@MainActivity,
				res.error?.message ?: getString(R.string.error_api)
			)
			RESULT_CODE_USER_INTERACTION_REQUIRED -> startIntentSenderForResult(
				res.pendingIntent.intentSender, REQUEST_SELECT_SSH_KEY,
				null, 0, 0, 0
			)
		}
	}

	private fun selectGpgKeyCallback(intent: Intent) {
		when (intent.getIntExtra(RESULT_CODE, -1)) {
			RESULT_CODE_SUCCESS -> updateGpgKeyId(intent.getLongExtra(EXTRA_SIGN_KEY_ID, -1))
			RESULT_CODE_ERROR -> showError(
				this@MainActivity,
				intent.getParcelableExtra<OpenPgpError>(RESULT_ERROR)?.message
					?: getString(R.string.error_api)
			)
			RESULT_CODE_USER_INTERACTION_REQUIRED -> startIntentSenderForResult(
				intent.getParcelableExtra<PendingIntent>(RESULT_INTENT)!!.intentSender,
				REQUEST_SELECT_GPG_KEY,
				null,
				0,
				0,
				0
			)
		}
	}

	private fun addSshKey(key: SshKeyInfo) {
		if (adaptor.keys.any { it.id == key.id }) {
			showError(this, R.string.error_duplicate_ssh_key)
			return
		}
		adaptor.keys.add(key)
		SshKeyInfo.save(adaptor.keys, this)
		if (adaptor.keys.size == 1) {
			adaptor.notifyItemChanged(0)
		} else {
			adaptor.notifyItemInserted(adaptor.keys.size - 1)
		}
	}

	private fun updateGpgKeyId(keyId: Long) {
		pref.edit().apply {
			putLong(getString(R.string.key_gpg_key), keyId)
			apply()
		}
		findViewById<TextView>(R.id.text_gpg_key).text = getString(R.string.text_has_gpg_key).format(keyId)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(findViewById(R.id.toolbar))
		adaptor.keys = SshKeyInfo.load(this).toMutableList()
		findViewById<RecyclerView>(R.id.recycler_ssh_keys).apply {
			layoutManager = LinearLayoutManager(this@MainActivity)
			adapter = adaptor
		}
		val textGpg = findViewById<TextView>(R.id.text_gpg_key)
		val gpgKeyId = pref.getLong(getString(R.string.key_gpg_key), -1)
		if (gpgKeyId == -1L) {
			textGpg.setText(R.string.text_no_gpg_key)
		} else {
			textGpg.text = getString(R.string.text_has_gpg_key).format(gpgKeyId)
		}
	}

	override fun onResume() {
		super.onResume()
		var found = true
		try {
			packageManager.getPackageInfo(getString(R.string.provider_package_id), 0)
		} catch (_: Exception) {
			found = false
		}
		if (found) {
			if (sshApi == null) {
				sshApi = SshApi(this) {
					if (!it) {
						close()
						showError(this@MainActivity, R.string.error_connect)
					}
				}.also { it.connect() }
			}
			if (gpgApi == null) {
				gpgApi = GpgApi(this) {
					if (!it) {
						close()
						showError(this@MainActivity, R.string.error_connect)
					}
				}.also { it.connect() }
			}
		}
		findViewById<Button>(R.id.button_ssh_key).isEnabled = found
		findViewById<Button>(R.id.button_gpg_key).isEnabled = found
		findViewById<View>(R.id.layout_no_provider).visibility =
			if (found) View.GONE else View.VISIBLE
	}

	override fun onDestroy() {
		sshApi?.close()
		gpgApi?.close()
		super.onDestroy()
	}


	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (resultCode == RESULT_OK) {
			when (requestCode) {
				REQUEST_SELECT_SSH_KEY -> addSshKeyCallback(sshApi?.executeApi(data!!) ?: return)
				REQUEST_SELECT_GPG_KEY -> selectGpgKeyCallback(
					gpgApi?.executeApi(data!!, null, null) ?: return
				)
			}
		}
	}

	fun addSshKey(@Suppress("UNUSED_PARAMETER") view: View) {
		addSshKeyCallback(sshApi?.executeApi(KeySelectionRequest().toIntent()) ?: return)
	}

	fun selectGpgKey(@Suppress("UNUSED_PARAMETER") view: View) {
		selectGpgKeyCallback(gpgApi?.executeApi(Intent().apply {
			action = ACTION_GET_SIGN_KEY_ID
		}, null, null) ?: return)
	}

	fun installProvider(@Suppress("UNUSED_PARAMETER") view: View) {
		val uri = "market://details?id=%s".format(getString(R.string.provider_package_id))
		try {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
		} catch (_: ActivityNotFoundException) {
			// Prevent the app from crashing when Play Store isn't installed.
		}
	}
}
