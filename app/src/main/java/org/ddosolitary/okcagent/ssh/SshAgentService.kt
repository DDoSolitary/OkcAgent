package org.ddosolitary.okcagent.ssh

import android.content.Intent
import android.util.Base64
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.ddosolitary.okcagent.AgentService
import org.ddosolitary.okcagent.R
import org.ddosolitary.okcagent.showError
import org.openintents.ssh.authentication.SshAuthenticationApi.EXTRA_ERROR
import org.openintents.ssh.authentication.SshAuthenticationApiError
import org.openintents.ssh.authentication.request.SigningRequest
import org.openintents.ssh.authentication.request.SshPublicKeyRequest
import org.openintents.ssh.authentication.response.SigningResponse
import org.openintents.ssh.authentication.response.SshPublicKeyResponse
import java.net.Socket
import java.util.concurrent.Semaphore

private const val LOG_TAG = "SshAgentService"

class SshAgentService : AgentService() {
	override fun getErrorMessage(intent: Intent): String? {
		return intent.getParcelableExtra<SshAuthenticationApiError>(EXTRA_ERROR)?.message
	}

	override fun runAgent(port: Int, intent: Intent) {
		var socket: Socket? = null
		try {
			socket = Socket("127.0.0.1", port)
			val input = socket.getInputStream()
			val output = socket.getOutputStream()
			val keys = SshKeyInfo.load(this)
			val lock = Semaphore(0)
			var connRes = false
			SshApi(this) { res ->
				if (!res) showError(this@SshAgentService, R.string.error_connect)
				connRes = res
				lock.release()
			}.use { api ->
				api.connect()
				lock.acquire()
				check(connRes)
				val executeApi = { reqIntent: Intent -> api.executeApi(reqIntent) }
				val publicKeys = mutableListOf<SshPublicKeyInfo>()
				for (key in keys) {
					val resIntent = callApi(executeApi, SshPublicKeyRequest(key.id).toIntent(), port, null) ?: return
					val pubKeyStr = SshPublicKeyResponse(resIntent).sshPublicKey
					val info = SshPublicKeyInfo(
						Base64.decode(
							pubKeyStr.substring(pubKeyStr.indexOf(' ') + 1),
							Base64.DEFAULT
						),
						key.description.toByteArray(Charsets.UTF_8)
					)
					publicKeys.add(info)
				}
				while (true) {
					val req = SshAgentMessage.readFromStream(input) ?: break
					val resMsg = when (req.type) {
						SSH_AGENTC_REQUEST_IDENTITIES -> {
							SshAgentMessage(
								SSH_AGENT_IDENTITIES_ANSWER,
								SshIdentitiesResponse(publicKeys).toBytes()
							)
						}
						SSH_AGENTC_SIGN_REQUEST -> {
							val signReq = SshSignRequest(req.contents!!)
							val keyId = keys[publicKeys.indexOfFirst { it.publicKey contentEquals signReq.keyBlob }].id
							val resIntent = callApi(
								executeApi,
								SigningRequest(signReq.data, keyId, signReq.flags).toIntent(),
								port,
								null
							)
							if (resIntent != null) {
								SshAgentMessage(
									SSH_AGENT_SIGN_RESPONSE,
									SshSignResponse(SigningResponse(resIntent).signature).toBytes()
								)
							} else null
						}
						else -> null
					}
					(resMsg ?: SshAgentMessage(SSH_AGENT_FAILURE, null)).writeToStream(output)
				}
			}
		} catch (e: Exception) {
			FirebaseCrashlytics.getInstance().recordException(e)
			Log.e(LOG_TAG, Log.getStackTraceString(e))
			try {
				socket?.setSoLinger(true, 0)
			} catch (e: Exception) {
				Log.w(LOG_TAG, "Failed to set linger option on exception: %s".format(e))
			}
		} finally {
			try {
				socket?.close()
			} catch (e: Exception) {
				Log.w(LOG_TAG, "Failed to close the socket on exit: %s".format(e))
			}
			checkThreadExit(port)
		}
	}

	override fun onCreate() {
		super.onCreate()
		startForeground(
			R.string.notification_ssh_title,
			R.string.notification_ssh_content,
			R.integer.notification_id_ssh
		)
	}
}
