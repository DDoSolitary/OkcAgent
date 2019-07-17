package org.ddosolitary.okcagent

import java.nio.ByteBuffer
import java.nio.ByteOrder

class SshIdentitiesResponse(private val keyBlob: ByteArray) {
	fun toBytes(): ByteArray {
		val bytes = ByteArray(keyBlob.size + Int.SIZE_BYTES * 3)
		ByteBuffer.wrap(bytes).apply {
			order(ByteOrder.BIG_ENDIAN)
			putInt(1)
			putInt(keyBlob.size)
			put(keyBlob)
			putInt(0)
		}
		return bytes
	}
}
