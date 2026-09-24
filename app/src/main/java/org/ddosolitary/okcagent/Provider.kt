package org.ddosolitary.okcagent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager

/**
 * The crypto provider app OkcAgent talks to. OpenKeychain by default; any
 * installed app that implements the SSH authentication API or the OpenPGP API
 * can be chosen in Settings.
 */
object Provider {
	const val DEFAULT_PACKAGE = "org.sufficientlysecure.keychain"
	const val ACTION_SSH = "org.openintents.ssh.authentication.ISshAuthenticationService"
	const val ACTION_OPENPGP = "org.openintents.openpgp.IOpenPgpService2"

	fun packageId(context: Context): String =
		PreferenceManager.getDefaultSharedPreferences(context)
			.getString(context.getString(R.string.key_provider), null)
			?.takeIf { it.isNotEmpty() }
			?: DEFAULT_PACKAGE

	fun isInstalled(context: Context, pkg: String = packageId(context)): Boolean = try {
		context.packageManager.getPackageInfo(pkg, 0)
		true
	} catch (_: PackageManager.NameNotFoundException) {
		false
	}

	/** Installed apps offering either API, as (package, label), OpenKeychain first. */
	fun available(context: Context): List<Pair<String, CharSequence>> {
		val pm = context.packageManager
		val pkgs = linkedSetOf<String>()
		for (action in listOf(ACTION_SSH, ACTION_OPENPGP)) {
			@Suppress("DEPRECATION")
			pm.queryIntentServices(Intent(action), 0).forEach { pkgs.add(it.serviceInfo.packageName) }
		}
		return pkgs.map { pkg ->
			val label = try {
				pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
			} catch (_: PackageManager.NameNotFoundException) {
				pkg
			}
			pkg to label
		}.sortedWith(compareBy({ it.first != DEFAULT_PACKAGE }, { it.second.toString().lowercase() }))
	}
}
