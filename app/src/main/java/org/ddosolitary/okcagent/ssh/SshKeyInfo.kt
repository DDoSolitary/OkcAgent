package org.ddosolitary.okcagent.ssh

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.ddosolitary.okcagent.R

@Serializable
data class SshKeyInfo(val id: String, val description: String) {
	companion object {
		fun load(context: Context): List<SshKeyInfo> {
			val pref = context.getSharedPreferences(context.getString(R.string.pref_main), Context.MODE_PRIVATE)
			val json = pref.getString(context.getString(R.string.key_ssh_keys), null) ?: "[]"
			return Json.decodeFromString(ListSerializer(serializer()), json)
		}

		fun save(list: List<SshKeyInfo>, context: Context) {
			val pref = context.getSharedPreferences(context.getString(R.string.pref_main), Context.MODE_PRIVATE)
			val json = Json.encodeToString(ListSerializer(serializer()), list)
			pref.edit().apply {
				putString(context.getString(R.string.key_ssh_keys), json)
				apply()
			}
		}
	}
}
