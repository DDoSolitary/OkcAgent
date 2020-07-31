package org.ddosolitary.okcagent.ssh

import java.nio.ByteBuffer
import java.nio.ByteOrder

class SshSignResponse(private val signature: ByteArray) {
	fun toBytes(): ByteArray {
		return ByteBuffer.allocate(Int.SIZE_BYTES + signature.size).apply {
			order(ByteOrder.BIG_ENDIAN)
			putInt(signature.size)
			put(signature)
		}.array()
	}
}
