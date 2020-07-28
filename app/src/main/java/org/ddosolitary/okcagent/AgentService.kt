package org.ddosolitary.okcagent

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread

const val ACTION_RUN_AGENT = "org.ddosolitary.okcagent.action.RUN_AGENT"
const val EXTRA_PROXY_PORT = "org.ddosolitary.okcagent.extra.PROXY_PORT"
const val RESULT_CODE_ERROR = 0
const val RESULT_CODE_SUCCESS = 1
const val RESULT_CODE_USER_INTERACTION_REQUIRED = 2
private const val ACTION_RESULT_CALLBACK = "org.ddosolitary.okcagent.action.RESULT_CALLBACK"
private const val ACTION_TERMINATE_SERVICE = "org.ddosolitary.okcagent.action.TERMINATE_SERVICE"
private const val EXTRA_RESULT_CODE = "result_code"
private const val EXTRA_PENDING_INTENT = "intent"

abstract class AgentService : Service() {
	private class NullableIntentHolder(val intent: Intent?)
	private class ThreadContext(val thread: Thread, val queue: ArrayBlockingQueue<NullableIntentHolder>)

	private val threadMap = mutableMapOf<Int, ThreadContext>()
	private var exited = false

	private fun checkServiceExit() {
		if (threadMap.isEmpty()) stopSelf()
	}

	protected fun checkThreadExit(port: Int) {
		if (!Thread.interrupted()) {
			Handler(mainLooper).post {
				threadMap.remove(port)
				if (!exited) checkServiceExit()
			}
		}
	}

	protected abstract fun getErrorMessage(intent: Intent): String?

	protected abstract fun runAgent(port: Int, intent: Intent)

	protected fun callApi(executeApi: (Intent) -> Intent?, req: Intent, port: Int): Intent? {
		var reqIntent = req
		while (true) {
			val resIntent = executeApi(reqIntent)!!
			when (resIntent.getIntExtra(EXTRA_RESULT_CODE, RESULT_CODE_ERROR)) {
				RESULT_CODE_SUCCESS -> return resIntent
				RESULT_CODE_USER_INTERACTION_REQUIRED -> {
					val runnerIntent = Intent(this, IntentRunnerActivity::class.java).apply {
						action = ACTION_RUN_PENDING_INTENT
						flags = Intent.FLAG_ACTIVITY_NEW_TASK
						putExtra(
							EXTRA_API_INTENT,
							resIntent.getParcelableExtra<PendingIntent>(EXTRA_PENDING_INTENT)
						)
						putExtra(
							EXTRA_CALLBACK_INTENT,
							Intent(this@AgentService, this@AgentService.javaClass).apply {
								action = ACTION_RESULT_CALLBACK
								putExtra(EXTRA_PROXY_PORT, port)
							}
						)
					}
					val pi = PendingIntent.getActivity(this, port, runnerIntent, PendingIntent.FLAG_UPDATE_CURRENT)
					val notification = NotificationCompat.Builder(this, getString(R.string.channel_id_auth))
						.setPriority(NotificationCompat.PRIORITY_HIGH)
						.setSmallIcon(R.drawable.ic_key)
						.setContentTitle(getString(R.string.notification_auth_title))
						.setContentText(getString(R.string.notification_auth_content))
						.setAutoCancel(true)
						.setOngoing(true)
						.setContentIntent(pi)
						.build()
					(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(port, notification)
					reqIntent = threadMap[port]!!.queue.take().intent ?: return null
				}
				RESULT_CODE_ERROR -> {
					showError(this, getErrorMessage(resIntent) ?: getString(R.string.error_api))
					return null
				}
			}
		}
	}

	protected fun buildServiceNotification(title: Int, text: Int): Notification {
		val intent = Intent(this, this.javaClass).apply { action = ACTION_TERMINATE_SERVICE }
		val pi = PendingIntent.getService(this, 0, intent, 0)
		return NotificationCompat.Builder(this, getString(R.string.channel_id_service))
			.setPriority(NotificationCompat.PRIORITY_MIN)
			.setSmallIcon(R.drawable.ic_key)
			.setContentTitle(getString(title))
			.setContentText(getString(text))
			.addAction(R.drawable.ic_stop, getString(R.string.text_terminate), pi)
			.build()
	}

	override fun onBind(intent: Intent): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val serviceChannel = NotificationChannel(
				getString(R.string.channel_id_service),
				getString(R.string.channel_service),
				NotificationManager.IMPORTANCE_MIN
			)
			val authChannel = NotificationChannel(
				getString(R.string.channel_id_auth),
				getString(R.string.channel_auth),
				NotificationManager.IMPORTANCE_HIGH
			);
			val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			mgr.createNotificationChannel(serviceChannel)
			mgr.createNotificationChannel(authChannel)
		}
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
		val port = intent.getIntExtra(EXTRA_PROXY_PORT, -1)
		when (intent.action) {
			ACTION_RUN_AGENT -> {
				if (!threadMap.containsKey(port)) {
					threadMap[port] = ThreadContext(
						thread { runAgent(port, intent) },
						ArrayBlockingQueue(1)
					)
				} else checkServiceExit()
			}
			ACTION_RESULT_CALLBACK -> threadMap[port]?.queue
				?.put(NullableIntentHolder(intent.getParcelableExtra(EXTRA_RESULT_INTENT))) ?: checkServiceExit()
			ACTION_TERMINATE_SERVICE -> stopSelf()
		}
		return START_NOT_STICKY
	}

	override fun onDestroy() {
		exited = true
		startActivity(Intent(this, IntentRunnerActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
			action = ACTION_FINISH
		})
		for ((_, ctx) in threadMap) {
			ctx.thread.interrupt()
		}
		for ((_, ctx) in threadMap) {
			ctx.thread.join(3000)
		}
		super.onDestroy()
	}
}
