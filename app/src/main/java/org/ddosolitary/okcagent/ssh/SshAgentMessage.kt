package org.ddosolitary.okcagent.ssh

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SSH_AGENT_FAILURE: Byte = 5
const val SSH_AGENTC_REQUEST_IDENTITIES: Byte = 11
const val SSH_AGENT_IDENTITIES_ANSWER: Byte = 12
const val SSH_AGENTC_SIGN_REQUEST: Byte = 13
const val SSH_AGENT_SIGN_RESPONSE: Byte = 14

class SshAgentMessage(val type: Byte, val contents: ByteArray?) {
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
				else -> x.toByte()
			}
			val contents = if (len > 1) readFull(stream, len - 1) ?: throw EOFException() else null
			return SshAgentMessage(type, contents)
		}
	}

	fun writeToStream(stream: OutputStream) {
		val bytes = ByteArray(Int.SIZE_BYTES + Byte.SIZE_BYTES + (contents?.size ?: 0))
		ByteBuffer.wrap(bytes).apply {
			order(ByteOrder.BIG_ENDIAN)
			putInt(bytes.size - Int.SIZE_BYTES)
			put(type)
			put(contents ?: return@apply)
		}
		stream.write(bytes)
		stream.flush()
	}
}
