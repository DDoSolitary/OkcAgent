package org.ddosolitary.okcagent

import android.content.Context
import android.content.Intent
import java.io.OutputStream

fun showError(context: Context, msg: String) {
	context.startActivity(Intent(context, ErrorDialogActivity::class.java).apply {
		flags = Intent.FLAG_ACTIVITY_NEW_TASK
		putExtra(EXTRA_ERROR_MESSAGE, msg)
	})
}

fun showError(context: Context, resId: Int) = showError(context, context.getString(resId))

fun writeString(output: OutputStream, str: String) {
	val strBuf = str.toByteArray(Charsets.UTF_8)
	val lenBuf = byteArrayOf(
		(strBuf.size shr 8).toByte(),
		(strBuf.size and Byte.MAX_VALUE.toInt()).toByte()
	)
	output.write(lenBuf)
	output.write(strBuf)
	output.flush()
}
