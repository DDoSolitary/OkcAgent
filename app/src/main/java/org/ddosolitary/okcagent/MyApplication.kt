package org.ddosolitary.okcagent

import android.app.Application
import com.bugsnag.android.Bugsnag

class MyApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		Bugsnag.start(this)
	}
}
