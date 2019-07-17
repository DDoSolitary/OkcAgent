package org.ddosolitary.okcagent

import java.nio.ByteBuffer
import java.nio.ByteOrder

class SshSignResponse(private val signature: ByteArray) {
	fun toBytes(): ByteArray {
		val bytes = ByteArray(Int.SIZE_BYTES + signature.size)
		ByteBuffer.wrap(bytes).apply {
			order(ByteOrder.BIG_ENDIAN)
			putInt(signature.size)
			put(signature)
		}
		return bytes
	}
}
