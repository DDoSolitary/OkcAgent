package org.ddosolitary.okcagent.ssh

import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("UsePropertyAccessSyntax")
class SshSignRequest constructor(bytes: ByteArray) {
	@Suppress("MemberVisibilityCanBePrivate")
	val keyBlob: ByteArray
	val data: ByteArray
	val flags: Int

	init {
		val buf = ByteBuffer.wrap(bytes).apply { order(ByteOrder.BIG_ENDIAN) }
		keyBlob = ByteArray(buf.getInt())
		buf.get(keyBlob)
		data = ByteArray(buf.getInt())
		buf.get(data)
		flags = buf.getInt()
	}
}
