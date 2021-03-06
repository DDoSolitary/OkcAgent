package org.ddosolitary.okcagent.gpg

import android.content.Context
import org.ddosolitary.okcagent.R

class GpgArguments(
	val options: Map<String, String?>,
	val arguments: List<String>,
	val warnings: List<String>
) {
	companion object {
		private class OptionInfo(
			val longName: String,
			val altLongName: String?,
			val shortName: Char?,
			val hasValue: Boolean,
			val notSupported: Boolean = false
		)

		@Suppress("BooleanLiteralArgument")
		private val OptionList = arrayOf(
			OptionInfo("sign", null, 's', false),
			OptionInfo("clear-sign", "clearsign", null, false),
			OptionInfo("detach-sign", null, 'b', false),
			OptionInfo("encrypt", null, 'e', false),
			OptionInfo("decrypt", null, 'd', false),
			OptionInfo("verify", null, null, false),
			OptionInfo("armor", null, 'a', false),
			OptionInfo("recipient", null, 'r', true),
			OptionInfo("compress-level", "bzip2-compress-level", null, true),
			OptionInfo("set-filename", null, null, true),
			OptionInfo("output", null, 'o', true),
			OptionInfo("local-user", null, 'u', true, true),
			OptionInfo("default-user", null, null, false, true),
			OptionInfo("status-fd", null, null, true, false),
			OptionInfo("keyid-format", null, null, true, true),
			OptionInfo("quiet", null, 'q', false),
			OptionInfo("yes", null, null, false),
			OptionInfo("no", null, null, false),
			OptionInfo("compress-algo", null, null, true, true),
			OptionInfo("batch", null, null, false, true),
			OptionInfo("no-batch", null, null, false, true),
			OptionInfo("no-encrypt-to", null, null, false),
			OptionInfo("use-agent", null, null, false),
			OptionInfo("no-use-agent", null, null, false),
			OptionInfo("verbose", null, 'v', false),
			OptionInfo("no-verbose", null, null, false),
			OptionInfo("no-secmem-warning", null, null, false),
			OptionInfo("no-permission-warning", null, null, false),
			OptionInfo("list-config", null, null, false),
			OptionInfo("with-colons", null, null, false)
		)

		private fun errorInvalidOption(context: Context, name: String): Nothing {
			throw Exception(context.getString(R.string.error_option).format(name))
		}

		private fun setOption(options: MutableMap<String, String?>, name: String, value: String?) {
			if (name == "recipient" && options.containsKey(name) && value != null) {
				options[name] = "%s\u0000%s".format(options[name], value)
			} else {
				options[name] = value
			}
		}

		fun parse(context: Context, args: Collection<String>?): GpgArguments {
			val options = mutableMapOf<String, String?>()
			val arguments = mutableListOf<String>()
			val warnings = mutableListOf<String>()
			if (args == null) {
				return GpgArguments(options, arguments, warnings)
			}
			var pendingArg: String? = null
			for (s in args) {
				val checkSupported = { name: String, option: OptionInfo ->
					if (option.notSupported) {
						warnings.add("[W] " + context.getString(R.string.msg_option_ignored).format(name))
					}
				}
				if (pendingArg != null) {
					setOption(options, pendingArg, s)
					pendingArg = null
				} else if (s.startsWith("--")) {
					var pos = s.indexOf('=')
					if (pos == -1) pos = s.length
					val name = s.substring(2, pos)
					val value = s.substring(minOf(s.length, pos + 1))
					val info = OptionList.find { it.longName == name || it.altLongName == name }
						?: errorInvalidOption(context, name)
					checkSupported(name, info)
					if (info.hasValue) {
						if (value.isEmpty()) {
							pendingArg = info.longName
						} else {
							setOption(options, info.longName, value)
						}
					} else {
						if (value.isEmpty()) setOption(options, info.longName, null)
						else errorInvalidOption(context, name)
					}
				} else if (s.startsWith('-') && s != "-") {
					for (i in 1 until s.length) {
						if (pendingArg != null) {
							setOption(options, pendingArg, s.substring(i))
							break
						}
						val info = OptionList.find { it.shortName == s[i] }
							?: errorInvalidOption(context, s[i].toString())
						checkSupported(s[i].toString(), info)
						if (info.hasValue) pendingArg = info.longName
						else setOption(options, info.longName, null)
					}
				} else {
					arguments.add(s)
				}
			}
			if (pendingArg != null) errorInvalidOption(context, pendingArg)
			return GpgArguments(options, arguments, warnings)
		}
	}
}
