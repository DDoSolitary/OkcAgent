package org.ddosolitary.okcagent.ssh

import java.nio.ByteBuffer
import java.nio.ByteOrder

class SshIdentitiesResponse(private val keyBlob: ByteArray) {
	fun toBytes(): ByteArray {
		return ByteBuffer.allocate(keyBlob.size + Int.SIZE_BYTES * 3).apply {
			order(ByteOrder.BIG_ENDIAN)
			putInt(1)
			putInt(keyBlob.size)
			put(keyBlob)
			putInt(0)
		}.array()
	}
}
