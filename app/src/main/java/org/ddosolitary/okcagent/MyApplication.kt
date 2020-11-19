package org.ddosolitary.okcagent

import android.app.Application
import androidx.preference.PreferenceManager
import com.bugsnag.android.Bugsnag

class MyApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		Bugsnag.start(this).addOnError {
			try {
				val pref = PreferenceManager.getDefaultSharedPreferences(this)
				return@addOnError pref.getBoolean(getString(R.string.key_error_reporting), false)
			} catch(e: Exception) {
				return@addOnError false
			}
		}
	}
}
