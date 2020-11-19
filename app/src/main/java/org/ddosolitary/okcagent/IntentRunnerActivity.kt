package org.ddosolitary.okcagent

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class IntentRunnerActivity : AppCompatActivity() {
	companion object {
		const val ACTION_RUN_PENDING_INTENT = "org.ddosolitary.okcagent.action.RUN_PENDING_INTENT"
		const val ACTION_FINISH = "org.ddosolitary.okcagent.action.FINISH"
		const val EXTRA_API_INTENT = "org.ddosolitary.okcagent.extra.API_INTENT"
		const val EXTRA_CALLBACK_INTENT = "org.ddosolitary.okcagent.extra.CALLBACK_INTENT"
		const val EXTRA_RESULT_INTENT = "org.ddosolitary.okcagent.extra.RESULT_INTENT"
	}

	class RequestsViewModel : ViewModel() {
		var reqIntent: Intent? = null
	}

	private val vm by lazy { ViewModelProvider(this)[RequestsViewModel::class.java] }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_intent_runner)
		when (intent.action) {
			ACTION_RUN_PENDING_INTENT -> {
				vm.reqIntent = intent
				startIntentSenderForResult(
					intent.getParcelableExtra<PendingIntent>(EXTRA_API_INTENT)!!.intentSender,
					0, null, 0, 0, 0
				)
			}
			else -> finish()
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		val res = if (resultCode == Activity.RESULT_OK) data!! else null
		val cbIntent = vm.reqIntent!!.getParcelableExtra<Intent>(EXTRA_CALLBACK_INTENT)!!
		startService(cbIntent.apply { putExtra(EXTRA_RESULT_INTENT, res) })
		vm.reqIntent = null
		finish()
	}

	override fun onDestroy() {
		if (!isChangingConfigurations) {
			vm.reqIntent?.let {
				val cbIntent = it.getParcelableExtra<Intent>(EXTRA_CALLBACK_INTENT)!!
				startService(cbIntent.apply { putExtra(EXTRA_RESULT_INTENT, null as Intent?) })
			}
		}
		super.onDestroy()
	}
}
