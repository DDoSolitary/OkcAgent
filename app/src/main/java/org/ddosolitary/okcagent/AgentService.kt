package org.ddosolitary.okcagent

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import org.openintents.ssh.authentication.SshAuthenticationApi.*
import org.openintents.ssh.authentication.request.SigningRequest
import org.openintents.ssh.authentication.request.SshPublicKeyRequest
import java.net.Socket

private const val NOTIFICATION_SSH = 1
private const val CHANNEL_SERVICE = "agent_services"
const val ACTION_RUN_SSH_AGENT = "org.ddosolitary.okcagent.action.RUN_SSH_AGENT"
const val EXTRA_PROXY_PORT = "org.ddosolitary.okcagent.extra.PROXY_PORT"
const val EXTRA_BROADCAST_INTENT = "org.ddosolitary.okcagent.extra.BROADCAST_INTENT"

class AgentService : IntentService("AgentService") {
	override fun onHandleIntent(intent: Intent?) {
		val bcIntent = intent?.getParcelableExtra<Intent>(EXTRA_BROADCAST_INTENT) ?: return
		if (bcIntent.action != ACTION_RUN_SSH_AGENT) return
		val pref = getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
		val keyId = pref.getString(getString(R.string.key_key_id), null) ?: return
		startActivity(Intent(this, CallSshServiceActivity::class.java).apply {
			@Suppress("UsePropertyAccessSyntax")
			setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			putExtra(EXTRA_SSH_REQUEST, SshPublicKeyRequest(keyId).toIntent())
			putExtra(EXTRA_RETURN_AS_STATIC, true)
		})
		synchronized(CallSshServiceActivity.lock) { CallSshServiceActivity.lock.wait() }
		val pubKeyStr =
			CallSshServiceActivity.result?.getStringExtra(EXTRA_SSH_PUBLIC_KEY) ?: return
		val pubKey = Base64.decode(pubKeyStr.substring(pubKeyStr.indexOf(' ') + 1), Base64.DEFAULT)
		val sock = Socket("127.0.0.1", bcIntent.getIntExtra(EXTRA_PROXY_PORT, 0))
		val input = sock.getInputStream()
		val output = sock.getOutputStream()
		while (true) {
			val req = SshAgentMessage.readFromStream(input) ?: break
			when (req.type) {
				SSH_AGENTC_REQUEST_IDENTITIES -> {
					SshAgentMessage(
						SSH_AGENT_IDENTITIES_ANSWER,
						SshIdentitiesResponse(pubKey).toBytes()
					).writeToStream(output)
				}
				SSH_AGENTC_SIGN_REQUEST -> {
					val signReq = SshSignRequest(req.contents ?: TODO())
					if (!signReq.keyBlob.contentEquals(pubKey)) TODO()
					startActivity(Intent(this, CallSshServiceActivity::class.java).apply {
						@Suppress("UsePropertyAccessSyntax")
						setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						putExtra(
							EXTRA_SSH_REQUEST,
							SigningRequest(signReq.data, keyId, signReq.flags).toIntent()
						)
						putExtra(EXTRA_RETURN_AS_STATIC, true)
					})
					synchronized(CallSshServiceActivity.lock) { CallSshServiceActivity.lock.wait() }
					SshAgentMessage(
						SSH_AGENT_SIGN_RESPONSE,
						SshSignResponse(
							CallSshServiceActivity.result?.getByteArrayExtra(
								EXTRA_SIGNATURE
							) ?: TODO()
						).toBytes()
					).writeToStream(output)
				}
				else -> SshAgentMessage(SSH_AGENT_FAILURE, null).writeToStream(output)
			}
		}
		sock.shutdownOutput()
		sock.close()
	}

	override fun onCreate() {
		super.onCreate()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_SERVICE,
				getString(R.string.channel_service),
				NotificationManager.IMPORTANCE_MIN
			)
			(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
				.createNotificationChannel(channel)
		}
		val notification = NotificationCompat.Builder(this, CHANNEL_SERVICE)
			.setSmallIcon(R.mipmap.ic_launcher_round)
			.setContentTitle(getString(R.string.notification_ssh_title))
			.setContentText(getString(R.string.notification_ssh_content))
			.build()
		startForeground(NOTIFICATION_SSH, notification)
	}
}
