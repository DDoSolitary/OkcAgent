package org.ddosolitary.okcagent.ssh

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SSH_AGENT_FAILURE: UByte = 5u
const val SSH_AGENTC_REQUEST_IDENTITIES: UByte = 11u
const val SSH_AGENT_IDENTITIES_ANSWER: UByte = 12u
const val SSH_AGENTC_SIGN_REQUEST: UByte = 13u
const val SSH_AGENT_SIGN_RESPONSE: UByte = 14u

class SshAgentMessage(val type: UByte, val contents: ByteArray?) {
	companion object {
		private fun readFull(stream: InputStream, size: Int): ByteArray? {
			val buf = ByteArray(size)
			var off = 0
			while (off < size) {
				val cnt = stream.read(buf, off, size - off)
				if (cnt == -1) {
					if (off == 0) return null
					else throw EOFException()
				}
				off += cnt
			}
			return buf
		}

		@Suppress("UsePropertyAccessSyntax")
		fun readFromStream(stream: InputStream): SshAgentMessage? {
			val len = ByteBuffer.wrap(readFull(stream, Int.SIZE_BYTES) ?: return null).apply {
				order(ByteOrder.BIG_ENDIAN)
			}.getInt()
			val type = when (val x = stream.read()) {
				-1 -> throw EOFException()
				else -> x.toUByte()
			}
			val contents = if (len > 1) readFull(stream, len - 1) ?: throw EOFException() else null
			return SshAgentMessage(type, contents)
		}
	}

	fun writeToStream(stream: OutputStream) {
		val bufSize = Int.SIZE_BYTES + Byte.SIZE_BYTES + (contents?.size ?: 0)
		val buf = ByteBuffer.allocate(bufSize).apply {
			order(ByteOrder.BIG_ENDIAN)
			putInt(bufSize - Int.SIZE_BYTES)
			put(type.toByte())
			put(contents ?: return@apply)
		}.array()
		stream.write(buf)
		stream.flush()
	}
}
