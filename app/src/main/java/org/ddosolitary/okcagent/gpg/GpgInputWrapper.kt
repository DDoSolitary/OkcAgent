package org.ddosolitary.okcagent.gpg

import org.ddosolitary.okcagent.AgentService
import org.ddosolitary.okcagent.readExact
import org.ddosolitary.okcagent.writeString
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GpgInputWrapper(
	private val port: Int,
	private val path: String,
	private val stat: AgentService.StreamStatus?
) : InputStream() {
	private val tmpFileDelegate = lazy {
		File.createTempFile("gpg-input-", null).apply { deleteOnExit() }
	}
	private val tmpFile by tmpFileDelegate
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
	private var position: Long = 0
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

	override fun close() {
		if (streamDelegate.isInitialized()) {
			stream.close()
		}
		if (tmpFileDelegate.isInitialized()) {
			tmpFile.delete()
		}
	}

	fun getAutoReopenStream(): InputStream {
		return object : InputStream() {
			private var tmpInput: FileInputStream? = null
			private var tmpOutput: FileOutputStream? = tmpFile.outputStream()
			private var position: Long = 0
			private var reopened = false

			override fun read(): Int {
				val buf = ByteArray(1)
				return if (read(buf, 0, 1) == -1) -1 else buf[0].toInt()
			}

			override fun read(b: ByteArray): Int {
				return read(b, 0, b.size)
			}

			override fun read(b: ByteArray, off: Int, len: Int): Int {
				if (reopened && tmpInput == null) tmpInput = tmpFile.inputStream()
				return if (position < tmpInput?.channel?.size() ?: 0) {
					check(reopened)
					val ret = tmpInput!!.read(b, off, len)
					check(ret != -1)
					position += ret
					ret
				} else {
					check(position == this@GpgInputWrapper.position)
					val ret = this@GpgInputWrapper.read(b, off, len)
					if (ret != -1) {
						if (!reopened) {
							tmpOutput!!.let {
								check(position == it.channel.position())
								it.write(b, off, ret)
							}
						}
						position += ret
					}
					ret
				}
			}

			override fun close() {
				position = 0
				if (!reopened) {
					tmpOutput!!.close()
					tmpOutput = null
					reopened = true
				} else {
					tmpInput?.close()
					tmpInput = null
				}
			}
		}
	}
}
