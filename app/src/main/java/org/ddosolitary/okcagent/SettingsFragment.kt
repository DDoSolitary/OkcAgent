package org.ddosolitary.okcagent

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.pref_screen_settings, rootKey)
		findPreference<ListPreference>(getString(R.string.key_provider))?.let { pref ->
			val providers = Provider.available(requireContext())
				.ifEmpty { listOf(Provider.DEFAULT_PACKAGE to Provider.DEFAULT_PACKAGE) }
			pref.entries = providers.map { it.second }.toTypedArray()
			pref.entryValues = providers.map { it.first }.toTypedArray()
			if (pref.value == null) pref.value = Provider.packageId(requireContext())
		}
	}
}
