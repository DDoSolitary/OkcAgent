package org.ddosolitary.okcagent.gpg

import org.ddosolitary.okcagent.AgentService
import org.ddosolitary.okcagent.readExact
import org.ddosolitary.okcagent.writeString
import java.io.EOFException
import java.io.InputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class GpgInputWrapper(
	private val port: Int,
	private val path: String,
	private val stat: AgentService.StreamStatus?
) : InputStream() {
	private val streamDelegate = lazy {
		val socket = Socket("127.0.0.1", port)
		socket.getOutputStream().let {
			it.write(1)
			writeString(it, path)
			it.flush()
		}
		socket.getInputStream()
	}
	private val stream by streamDelegate
	private var position: Int = 0
	private var buffer: ByteArray? = null
	private var bufferPosition = 0
	private var eofReceived = false
	private var eofExceptionThrown = false

	override fun read(): Int {
		val buf = ByteArray(1)
		return if (read(buf, 0, 1) == -1) -1 else buf[0].toInt()
	}

	override fun read(b: ByteArray): Int {
		return read(b, 0, b.size)
	}

	@Suppress("UsePropertyAccessSyntax")
	override fun read(b: ByteArray, off: Int, len: Int): Int {
		if (eofReceived) return -1
		if (eofExceptionThrown) throw EOFException()
		if (buffer == null) {
			val lenBuf = readExact(stream, Short.SIZE_BYTES)
			if (lenBuf == null) {
				eofExceptionThrown = true
				stat?.exception = EOFException()
				throw EOFException()
			}
			val recvLen = ByteBuffer.wrap(lenBuf).apply {
				order(ByteOrder.BIG_ENDIAN)
			}.getShort().toUShort().toInt()
			if (recvLen == 0) {
				eofReceived = true
				return -1
			}
			buffer = readExact(stream, recvLen)
			bufferPosition = 0
		}
		buffer!!.let {
			val copyLen = minOf(len, it.size - bufferPosition)
			it.copyInto(b, off, bufferPosition, bufferPosition + copyLen)
			bufferPosition += copyLen
			position += copyLen
			if (bufferPosition == it.size) {
				buffer = null
			}
			return copyLen
		}
	}

	override fun available(): Int {
		return stream.available()
	}

	override fun close() {
		if (streamDelegate.isInitialized()) {
			stream.close()
		}
	}

	fun getAutoReopenStream(): InputStream {
		return object : InputStream() {
			val buffer = ByteArray(1048576)
			var position: Int = 0
			var bufferPosition: Int = 0

			override fun read(): Int {
				val buf = ByteArray(1)
				return if (read(buf, 0, 1) == -1) -1 else buf[0].toInt()
			}

			override fun read(b: ByteArray): Int {
				return read(b, 0, b.size)
			}

			override fun read(b: ByteArray, off: Int, len: Int): Int {
				return if (position < bufferPosition) {
					val copyLen = min(len, bufferPosition - position)
					buffer.copyInto(b, off, position, position + copyLen)
					position += copyLen
					copyLen
				} else {
					check(position == this@GpgInputWrapper.position)
					val ret = this@GpgInputWrapper.read(b, off, len)
					if (ret != -1) {
						if (position < buffer.size) {
							val copyLen = min(ret, buffer.size - bufferPosition)
							b.copyInto(buffer, bufferPosition, off, off + copyLen)
							bufferPosition += copyLen
						}
						position += ret
					}
					ret
				}
			}

			override fun close() {
				position = 0
			}
		}
	}
}
