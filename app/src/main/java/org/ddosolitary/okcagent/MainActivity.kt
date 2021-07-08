package org.ddosolitary.okcagent

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.ddosolitary.okcagent.gpg.GpgApi
import org.ddosolitary.okcagent.ssh.SshApi
import org.ddosolitary.okcagent.ssh.SshKeyInfo
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpApi.ACTION_GET_SIGN_KEY_ID
import org.openintents.openpgp.util.OpenPgpApi.EXTRA_SIGN_KEY_ID
import org.openintents.openpgp.util.OpenPgpApi.RESULT_CODE
import org.openintents.openpgp.util.OpenPgpApi.RESULT_ERROR
import org.openintents.openpgp.util.OpenPgpApi.RESULT_INTENT
import org.openintents.ssh.authentication.SshAuthenticationApi
import org.openintents.ssh.authentication.request.KeySelectionRequest
import org.openintents.ssh.authentication.response.KeySelectionResponse
import java.util.*


class MainActivity : AppCompatActivity() {
	companion object {
		private const val REQUEST_SELECT_SSH_KEY = 1
		private const val REQUEST_SELECT_GPG_KEY = 2
	}

	private class SshKeyViewHolder(val view: View) : RecyclerView.ViewHolder(view)

	private class SshKeyListAdaptor(
		private val touchHelper: ItemTouchHelper,
		var keys: MutableList<SshKeyInfo>,
	) : RecyclerView.Adapter<SshKeyViewHolder>() {

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SshKeyViewHolder {
			val view = LayoutInflater.from(parent.context).inflate(R.layout.view_ssh_key, parent, false)
			return SshKeyViewHolder(view)
		}

		override fun getItemCount(): Int {
			return if (keys.size == 0) 1 else keys.size
		}

		@SuppressLint("ClickableViewAccessibility")
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
					keys.removeAt(holder.bindingAdapterPosition)
					SshKeyInfo.save(keys, it.context)
					if (keys.size == 0) {
						notifyItemChanged(0)
					} else {
						notifyItemRemoved(holder.bindingAdapterPosition)
					}
				}
			}
			holder.view.findViewById<ImageView>(R.id.img_drag_handle).setOnTouchListener { _, event ->
				if (event.actionMasked == MotionEvent.ACTION_DOWN) {
					touchHelper.startDrag(holder)
				}
				false
			}
		}
	}

	private val pref by lazy {
		getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
	}
	private var sshApi: SshApi? = null
	private var gpgApi: GpgApi? = null
	private lateinit var adaptor: SshKeyListAdaptor

	private fun addSshKeyCallback(intent: Intent) {
		val res = KeySelectionResponse(intent)
		when (res.resultCode) {
			SshAuthenticationApi.RESULT_CODE_SUCCESS -> addSshKey(SshKeyInfo(res.keyId, res.keyDescription))
			SshAuthenticationApi.RESULT_CODE_ERROR -> showError(
				this@MainActivity,
				res.error?.message ?: getString(R.string.error_api)
			)
			SshAuthenticationApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> startIntentSenderForResult(
				res.pendingIntent.intentSender, REQUEST_SELECT_SSH_KEY,
				null, 0, 0, 0
			)
		}
	}

	private fun selectGpgKeyCallback(intent: Intent) {
		when (intent.getIntExtra(RESULT_CODE, -1)) {
			OpenPgpApi.RESULT_CODE_SUCCESS -> updateGpgKeyId(intent.getLongExtra(EXTRA_SIGN_KEY_ID, -1))
			OpenPgpApi.RESULT_CODE_ERROR -> showError(
				this@MainActivity,
				intent.getParcelableExtra<OpenPgpError>(RESULT_ERROR)?.message
					?: getString(R.string.error_api)
			)
			OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> startIntentSenderForResult(
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
		val prefEditor = pref.edit()
		val gpgText = findViewById<TextView>(R.id.text_gpg_key)
		if (keyId == 0L) {
			prefEditor.remove(getString(R.string.key_gpg_key))
			gpgText.text = getString(R.string.text_no_gpg_key)
		} else {
			prefEditor.putLong(getString(R.string.key_gpg_key), keyId)
			gpgText.text = getString(R.string.text_has_gpg_key).format(keyId)
		}
		prefEditor.apply()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(findViewById(R.id.toolbar))

		val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
			override fun isItemViewSwipeEnabled(): Boolean = false
			override fun isLongPressDragEnabled(): Boolean = false
			override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
				makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

			override fun onMove(
				recyclerView: RecyclerView,
				viewHolder: RecyclerView.ViewHolder,
				target: RecyclerView.ViewHolder
			): Boolean {
				val srcPos = viewHolder.bindingAdapterPosition
				val dstPos = target.bindingAdapterPosition
				Collections.swap(adaptor.keys, srcPos, dstPos)
				SshKeyInfo.save(adaptor.keys, this@MainActivity)
				adaptor.notifyItemMoved(srcPos, dstPos)
				return true
			}

			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
		})
		adaptor = SshKeyListAdaptor(touchHelper, SshKeyInfo.load(this).toMutableList())
		findViewById<RecyclerView>(R.id.recycler_ssh_keys).apply {
			layoutManager = LinearLayoutManager(this@MainActivity)
			adapter = adaptor
			touchHelper.attachToRecyclerView(this)
		}

		val textGpg = findViewById<TextView>(R.id.text_gpg_key)
		val gpgKeyId = pref.getLong(getString(R.string.key_gpg_key), -1)
		if (gpgKeyId == -1L) {
			textGpg.setText(R.string.text_no_gpg_key)
		} else {
			textGpg.text = getString(R.string.text_has_gpg_key).format(gpgKeyId)
		}

		val settingsPref = PreferenceManager.getDefaultSharedPreferences(this)
		if (!settingsPref.contains(getString(R.string.key_error_reporting))) {
			MaterialAlertDialogBuilder(this)
				.setTitle(R.string.text_error_reporting)
				.setMessage(R.string.text_error_reporting_message)
				.setPositiveButton(R.string.button_yes) { _, _ ->
					settingsPref.edit().run {
						putBoolean(getString(R.string.key_error_reporting), true)
						apply()
					}
				}
				.setNegativeButton(R.string.button_no) { _, _ ->
					settingsPref.edit().run {
						putBoolean(getString(R.string.key_error_reporting), false)
						apply()
					}
				}
				.show()
		}
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.item_settings -> startActivity(Intent(this, SettingsActivity::class.java))
		}
		return true
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
		super.onActivityResult(requestCode, resultCode, data)
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
