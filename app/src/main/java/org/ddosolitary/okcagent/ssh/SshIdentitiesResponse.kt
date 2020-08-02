package org.ddosolitary.okcagent.ssh

import java.nio.ByteBuffer
import java.nio.ByteOrder

class SshPublicKeyInfo(val publicKey: ByteArray, val description: ByteArray)

class SshIdentitiesResponse(private val keys: List<SshPublicKeyInfo>) {
	fun toBytes(): ByteArray {
		val bufLen = keys.sumBy { it.publicKey.size + it.description.size + Int.SIZE_BYTES * 2 } + Int.SIZE_BYTES
		return ByteBuffer.allocate(bufLen).apply {
			order(ByteOrder.BIG_ENDIAN)
			putInt(keys.size)
			for (keyInfo in keys) {
				putInt(keyInfo.publicKey.size)
				put(keyInfo.publicKey)
				putInt(keyInfo.description.size)
				put(keyInfo.description)
			}
		}.array()
	}
}
