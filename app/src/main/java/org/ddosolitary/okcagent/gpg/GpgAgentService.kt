package org.ddosolitary.okcagent.gpg

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.bugsnag.android.Bugsnag
import org.ddosolitary.okcagent.AgentService
import org.ddosolitary.okcagent.R
import org.ddosolitary.okcagent.showError
import org.ddosolitary.okcagent.writeString
import org.openintents.openpgp.OpenPgpDecryptionResult
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.OpenPgpSignatureResult
import org.openintents.openpgp.util.OpenPgpApi.*
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Semaphore

const val EXTRA_GPG_ARGS = "org.ddosolitary.okcagent.extra.GPG_ARGS"
private const val LOG_TAG = "GpgAgentService"

class GpgAgentService : AgentService() {
	private fun handleSigResult(res: OpenPgpSignatureResult, output: OutputStream): Boolean {
		val resStr = when (res.result) {
			-1 -> "RESULT_NO_SIGNATURE"
			0 -> "RESULT_INVALID_SIGNATURE"
			1 -> "RESULT_VALID_KEY_CONFIRMED"
			2 -> "RESULT_KEY_MISSING"
			3 -> "RESULT_VALID_KEY_UNCONFIRMED"
			4 -> "RESULT_INVALID_KEY_REVOKED"
			5 -> "RESULT_INVALID_KEY_EXPIRED"
			6 -> "RESULT_INVALID_KEY_INSECURE"
			else -> "N/A"
		}
		if (res.primaryUserId != null) {
			writeString(output, getString(R.string.msg_signature_from).format(res.primaryUserId))
			writeString(output, getString(R.string.msg_signature_time).format(res.signatureTimestamp.toString()))
		}
		writeString(output, getString(R.string.msg_signature_result).format(resStr))
		return res.result == -1 || res.result == 1 || res.result == 3
	}

	override fun getErrorMessage(intent: Intent): String? {
		return intent.getParcelableExtra<OpenPgpError>(RESULT_ERROR)?.message
	}

	override fun runAgent(port: Int, intent: Intent) {
		var controlOutput: OutputStream? = null
		var success = true
		try {
			controlOutput = Socket("127.0.0.1", port).getOutputStream().also { it.write(0) }
			val args = GpgArguments.parse(
				this,
				intent.getStringArrayExtra(EXTRA_GPG_ARGS)
					?.map { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
			)
			for (msg in args.warnings) {
				writeString(controlOutput, msg)
			}
			val keyId = getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
				.getLong(getString(R.string.key_gpg_key), -1)
			val inputStat = StreamStatus(null)
			GpgInputWrapper(
				port,
				if (args.arguments.isNotEmpty()) args.arguments.last() else "-",
				inputStat
			).use { input ->
				val wrappedInput = input.getAutoReopenStream()
				GpgOutputWrapper(port, args.options["output"] ?: "-").use { output ->
					val lock = Semaphore(0)
					var connRes = false
					GpgApi(this) { res ->
						if (!res) showError(this@GpgAgentService, R.string.error_connect)
						connRes = res
						lock.release()
					}.use { api ->
						api.connect()
						lock.acquire()
						check(connRes)
						val reqIntent = Intent()
						reqIntent.putExtra(
							EXTRA_REQUEST_ASCII_ARMOR,
							args.options.containsKey("armor") || args.options.containsKey("clear-sign")
						)
						args.options["compress-level"]?.let {
							reqIntent.putExtra(EXTRA_ENABLE_COMPRESSION, it.toInt() > 0)
						}
						args.options["set-filename"]?.let {
							reqIntent.putExtra(EXTRA_ORIGINAL_FILENAME, it)
						}
						when {
							args.options.containsKey("list-config") -> {
								// no-op
							}
							args.options.containsKey("clear-sign") -> {
								reqIntent.action = ACTION_CLEARTEXT_SIGN
								reqIntent.putExtra(EXTRA_SIGN_KEY_ID, keyId)
								val resIntent = callApi(
									{ api.executeApi(it, wrappedInput, output) },
									reqIntent, port, inputStat
								)
								if (resIntent == null) {
									success = false
									return@runAgent
								}
								Unit
							}
							args.options.containsKey("detach-sign") -> {
								reqIntent.action = ACTION_DETACHED_SIGN
								reqIntent.putExtra(EXTRA_SIGN_KEY_ID, keyId)
								val resIntent = callApi(
									{ api.executeApi(it, wrappedInput, null) },
									reqIntent, port, inputStat
								)
								if (resIntent == null) {
									success = false
									return@runAgent
								}
								output.write(resIntent.getByteArrayExtra(RESULT_DETACHED_SIGNATURE)!!)
							}
							args.options.containsKey("encrypt") || args.options.containsKey("sign") -> {
								if (args.options.containsKey("encrypt")) {
									val recipient = args.options["recipient"]
									if (recipient == null) {
										throw Exception(
											getString(R.string.error_required_option).format("recipient")
										)
									} else {
										val keyIds = mutableListOf<Long>()
										val userIds = mutableListOf<String>()
										for (r in recipient.split('\u0000')) {
											if (Regex("^[0-9a-zA-Z]{16}$").matches(r)) {
												keyIds.add(r.toULong(16).toLong())
											} else {
												userIds.add(r)
											}
										}
										if (keyIds.isNotEmpty()) {
											reqIntent.putExtra(EXTRA_KEY_IDS, keyIds.toLongArray())
										}
										if (userIds.isNotEmpty()) {
											reqIntent.putExtra(EXTRA_USER_IDS, userIds.toTypedArray())
										}
									}
								}
								if (args.options.containsKey("sign")) {
									reqIntent.action = ACTION_SIGN_AND_ENCRYPT
									reqIntent.putExtra(EXTRA_SIGN_KEY_ID, keyId)
								} else {
									reqIntent.action = ACTION_ENCRYPT
								}
								val resIntent = callApi(
									{ api.executeApi(it, wrappedInput, output) },
									reqIntent, port, inputStat
								)
								if (resIntent == null) {
									success = false
									return@runAgent
								}
								Unit
							}
							args.options.containsKey("verify") || args.options.containsKey("decrypt") -> {
								reqIntent.action = ACTION_DECRYPT_VERIFY
								if (args.options.containsKey("verify") && args.arguments.size > 1) {
									reqIntent.putExtra(
										EXTRA_DETACHED_SIGNATURE,
										GpgInputWrapper(port, args.arguments[0], null).use { it.readBytes() }
									)
								}
								val apiOutput = if (args.options.containsKey("decrypt")) output else null
								val resIntent = callApi(
									{ api.executeApi(it, wrappedInput, apiOutput) },
									reqIntent, port, inputStat
								)
								if (resIntent == null) {
									success = false
									return@runAgent
								}
								if (!handleSigResult(
										resIntent.getParcelableExtra(RESULT_SIGNATURE)!!,
										controlOutput
									)
								) success = false
								if (args.options.containsKey("decrypt")) {
									val decRes: OpenPgpDecryptionResult =
										resIntent.getParcelableExtra(RESULT_DECRYPTION)!!
									val resStr = when (decRes.result) {
										-1 -> "RESULT_NOT_ENCRYPTED"
										0 -> "RESULT_INSECURE"
										1 -> "RESULT_ENCRYPTED"
										else -> "N/A"
									}
									writeString(
										controlOutput,
										getString(R.string.msg_decryption_result).format(resStr)
									)
								}
							}
							else -> throw Exception(getString(R.string.error_gpg_no_action))
						}
						if (reqIntent.hasExtra(EXTRA_SIGN_KEY_ID)) {
							when (args.options["status-fd"]) {
								null -> Unit
								"1" -> throw Exception(getString(R.string.error_invalid_status_fd))
								"2" -> writeString(
									controlOutput,
									"[GNUPG:] SIG_CREATED This line is only used to make git happy!"
								)
							}
						}
					}
				}
			}
		} catch (e: Exception) {
			Bugsnag.notify(e)
			Log.e(LOG_TAG, Log.getStackTraceString(e))
			success = false
			try {
				controlOutput?.let {
					writeString(it, "[E] %s".format(e))
				}
			} catch (e2: Exception) {
				Log.w(LOG_TAG, "Failed to send error message for the exception: %s".format(e2))
				showError(this, e.toString())
			}
		} finally {
			try {
				controlOutput?.write(byteArrayOf(0, 0, if (success) 0 else 1))
				controlOutput?.close()
			} catch (e: Exception) {
				Log.w(LOG_TAG, "Failed to send status code on exit: %s".format(e))
			}
			checkThreadExit(port)
		}
	}

	override fun onCreate() {
		super.onCreate()
		startForeground(
			R.string.notification_gpg_title,
			R.string.notification_gpg_content,
			R.integer.notification_id_gpg
		)
	}
}
