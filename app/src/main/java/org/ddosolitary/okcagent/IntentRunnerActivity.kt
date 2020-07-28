package org.ddosolitary.okcagent

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

const val ACTION_RUN_PENDING_INTENT = "org.ddosolitary.okcagent.action.RUN_PENDING_INTENT"
const val ACTION_FINISH = "org.ddosolitary.okcagent.action.FINISH"
const val EXTRA_API_INTENT = "org.ddosolitary.okcagent.extra.API_INTENT"
const val EXTRA_CALLBACK_INTENT = "org.ddosolitary.okcagent.extra.CALLBACK_INTENT"
const val EXTRA_RESULT_INTENT = "org.ddosolitary.okcagent.extra.RESULT_INTENT"

class IntentRunnerActivity : AppCompatActivity() {
	class RequestsViewModel : ViewModel() {
		var requestCode = 0
		val requestMap = mutableMapOf<Int, Intent>()
	}

	private val vm by lazy { ViewModelProvider(this)[RequestsViewModel::class.java] }

	private fun processIntent(intent: Intent) {
		when (intent.action) {
			ACTION_FINISH -> finish()
			ACTION_RUN_PENDING_INTENT -> {
				vm.requestMap[vm.requestCode] = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT)!!
				startIntentSenderForResult(
					intent.getParcelableExtra<PendingIntent>(EXTRA_API_INTENT)!!.intentSender,
					vm.requestCode++, null, 0, 0, 0
				)
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		processIntent(intent)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_intent_runner)
		processIntent(intent)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		vm.requestMap[requestCode]?.let {
			val res = if (resultCode == Activity.RESULT_OK) data!! else null
			startService(it.apply { putExtra(EXTRA_RESULT_INTENT, res) })
		}
		vm.requestMap.remove(requestCode)
		if (vm.requestMap.isEmpty()) finish()
	}

	override fun onDestroy() {
		if (!isChangingConfigurations) {
			for ((_, intent) in vm.requestMap) {
				startService(intent.apply { putExtra(EXTRA_RESULT_INTENT, null as Intent?) })
			}
			vm.requestCode = 0
			vm.requestMap.clear()
		}
		super.onDestroy()
	}
}
