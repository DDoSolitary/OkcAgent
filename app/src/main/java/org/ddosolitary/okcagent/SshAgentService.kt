package org.ddosolitary.okcagent

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import org.openintents.ssh.authentication.SshAuthenticationApi.*
import org.openintents.ssh.authentication.SshAuthenticationApiError
import org.openintents.ssh.authentication.request.Request
import org.openintents.ssh.authentication.request.SigningRequest
import org.openintents.ssh.authentication.request.SshPublicKeyRequest
import org.openintents.ssh.authentication.response.SigningResponse
import org.openintents.ssh.authentication.response.SshPublicKeyResponse
import java.lang.Exception
import java.lang.IllegalStateException
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread

const val ACTION_RUN_SSH_AGENT = "org.ddosolitary.okcagent.action.RUN_SSH_AGENT"
const val ACTION_SSH_RESULT_CALLBACK = "org.ddosolitary.okcagent.action.SSH_RESULT_CALLBACK"
const val EXTRA_SSH_PROXY_PORT = "org.ddosolitary.okcagent.extra.SSH_PROXY_PORT"
private const val NOTIFICATION_ID_SSH = 1

class SshAgentService : Service() {
	private class ThreadContext(val thread: Thread, val queue: ArrayBlockingQueue<Intent?>)

	private var lastStartId = -1
	private val threadMap = mutableMapOf<Int, ThreadContext>()
	private var exited = false

	private fun checkExit() {
		if (threadMap.isEmpty()) stopSelf(lastStartId)
	}

	private fun callApi(api: SshApi, req: Request, port: Int): Intent? {
		var reqIntent = req.toIntent()
		while (true) {
			val resIntent = api.executeApi(reqIntent)!!
			when (resIntent.getIntExtra(EXTRA_RESULT_CODE, RESULT_CODE_ERROR)) {
				RESULT_CODE_SUCCESS -> return resIntent
				RESULT_CODE_USER_INTERACTION_REQUIRED -> {
					startActivity(Intent(this, IntentRunnerActivity::class.java).apply {
						action = ACTION_RUN_PENDING_INTENT
						flags = Intent.FLAG_ACTIVITY_NEW_TASK
						putExtra(
							EXTRA_API_INTENT,
							resIntent.getParcelableExtra<PendingIntent>(EXTRA_PENDING_INTENT)
						)
						putExtra(
							EXTRA_CALLBACK_INTENT,
							Intent(this@SshAgentService, SshAgentService::class.java).apply {
								action = ACTION_SSH_RESULT_CALLBACK
								putExtra(EXTRA_SSH_PROXY_PORT, port)
							}
						)
					})
					reqIntent = threadMap[port]!!.queue.take() ?: return null
				}
				RESULT_CODE_ERROR -> {
					showError(
						this,
						resIntent.getParcelableExtra<SshAuthenticationApiError>(EXTRA_ERROR)?.message
							?: getString(R.string.error_api)
					)
					return null
				}
			}
		}
	}

	private fun runAgent(port: Int) {
		var socket: Socket? = null
		var api: SshApi? = null
		try {
			socket = Socket("127.0.0.1", port)
			val input = socket.getInputStream()
			val output = socket.getOutputStream()
			val keyId = getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
				.getString(getString(R.string.key_ssh_key), "") ?: ""
			val lock = Object()
			var connRes = false
			api = SshApi(this) { res ->
				if (!res) showError(this@SshAgentService, R.string.error_connect)
				connRes = res
				synchronized(lock) { lock.notify() }
			}.also { it.connect() }
			synchronized(lock) { lock.wait() }
			if (!connRes) throw IllegalStateException()
			while (true) {
				val req = SshAgentMessage.readFromStream(input) ?: break
				val resMsg = when (req.type) {
					SSH_AGENTC_REQUEST_IDENTITIES -> {
						val resIntent = callApi(api, SshPublicKeyRequest(keyId), port)
						if (resIntent != null) {
							val pubKeyStr = SshPublicKeyResponse(resIntent).sshPublicKey
							SshAgentMessage(
								SSH_AGENT_IDENTITIES_ANSWER,
								SshIdentitiesResponse(
									Base64.decode(
										pubKeyStr.substring(pubKeyStr.indexOf(' ') + 1),
										Base64.DEFAULT
									)
								).toBytes()
							)
						} else null
					}
					SSH_AGENTC_SIGN_REQUEST -> {
						val signReq = SshSignRequest(req.contents!!)
						val resIntent =
							callApi(api, SigningRequest(signReq.data, keyId, signReq.flags), port)
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
		} catch (_: Exception) {
			socket?.setSoLinger(true, 0)
		} finally {
			api?.disconnect()
			socket?.close()
			if (!Thread.interrupted()) {
				Handler(mainLooper).post {
					threadMap.remove(port)
					if (!exited) checkExit()
				}
			}
		}
	}

	override fun onBind(intent: Intent): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		createNotificationChannel(this)
		val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_SERVICE)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentTitle(getString(R.string.notification_ssh_title))
			.setContentText(getString(R.string.notification_ssh_content))
			.build()
		startForeground(NOTIFICATION_ID_SSH, notification)
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
		val port = intent.getIntExtra(EXTRA_SSH_PROXY_PORT, -1)
		when (intent.action) {
			ACTION_RUN_SSH_AGENT -> {
				if (!threadMap.containsKey(port)) {
					threadMap[port] = ThreadContext(
						thread { runAgent(port) },
						ArrayBlockingQueue(1)
					)
				} else checkExit()
			}
			ACTION_SSH_RESULT_CALLBACK -> threadMap[port]?.queue
				?.put(intent.getParcelableExtra(EXTRA_RESULT_INTENT)) ?: checkExit()
		}
		lastStartId = startId
		return START_NOT_STICKY
	}

	override fun onDestroy() {
		exited = true
		startActivity(Intent(this, IntentRunnerActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
			action = ACTION_FINISH
		})
		for ((_, ctx) in threadMap) {
			ctx.thread.apply {
				interrupt()
				join()
			}
		}
		super.onDestroy()
	}
}
