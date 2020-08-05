package org.ddosolitary.okcagent.ssh

import android.content.Context
import com.beust.klaxon.Klaxon
import org.ddosolitary.okcagent.R

class SshKeyInfo(val id: String, val description: String) {
	companion object {
		fun load(context: Context): List<SshKeyInfo> {
			val pref = context.getSharedPreferences(context.getString(R.string.pref_main), Context.MODE_PRIVATE)
			val json = pref.getString(context.getString(R.string.key_ssh_keys), null) ?: "[]"
			return Klaxon().parseArray(json)!!
		}

		fun save(list: List<SshKeyInfo>, context: Context) {
			val pref = context.getSharedPreferences(context.getString(R.string.pref_main), Context.MODE_PRIVATE)
			val json = Klaxon().toJsonString(list)
			pref.edit().apply {
				putString(context.getString(R.string.key_ssh_keys), json)
				apply()
			}
		}
	}
}
