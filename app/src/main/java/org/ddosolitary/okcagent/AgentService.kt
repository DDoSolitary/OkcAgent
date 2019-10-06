package org.ddosolitary.okcagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread

const val ACTION_RUN_AGENT = "org.ddosolitary.okcagent.action.RUN_AGENT"
const val ACTION_RESULT_CALLBACK = "org.ddosolitary.okcagent.action.RESULT_CALLBACK"
const val EXTRA_PROXY_PORT = "org.ddosolitary.okcagent.extra.PROXY_PORT"
const val RESULT_CODE_ERROR = 0
const val RESULT_CODE_SUCCESS = 1
const val RESULT_CODE_USER_INTERACTION_REQUIRED = 2
private const val EXTRA_RESULT_CODE = "result_code"
private const val EXTRA_PENDING_INTENT = "intent"

abstract class AgentService : Service() {
	private class ThreadContext(val thread: Thread, val queue: ArrayBlockingQueue<Intent?>)

	private var lastStartId = -1
	private val threadMap = mutableMapOf<Int, ThreadContext>()
	private var exited = false

	private fun checkServiceExit() {
		if (threadMap.isEmpty()) stopSelf(lastStartId)
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
					startActivity(Intent(this, IntentRunnerActivity::class.java).apply {
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
					})
					reqIntent = threadMap[port]!!.queue.take() ?: return null
				}
				RESULT_CODE_ERROR -> {
					showError(this, getErrorMessage(resIntent) ?: getString(R.string.error_api))
					return null
				}
			}
		}
	}

	override fun onBind(intent: Intent): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				getString(R.string.channel_id_service),
				this.getString(R.string.channel_service),
				NotificationManager.IMPORTANCE_MIN
			)
			(this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
				.createNotificationChannel(channel)
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
				?.put(intent.getParcelableExtra(EXTRA_RESULT_INTENT)) ?: checkServiceExit()
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
