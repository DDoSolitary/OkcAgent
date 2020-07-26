package org.ddosolitary.okcagent.gpg

import org.ddosolitary.okcagent.writeString
import java.io.InputStream
import java.net.Socket
import kotlin.math.min

class GpgInputWrapper(
	private val port: Int,
	private val path: String
) : InputStream() {
	private var stream: InputStream? = null
	private var position: Int = 0

	override fun read(): Int {
		val buf = ByteArray(1)
		return if (read(buf, 0, 1) == -1) -1 else buf[0].toInt()
	}

	override fun read(b: ByteArray): Int {
		return read(b, 0, b.size)
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		ensureStreamOpened()
		return stream!!.read(b, off, len).also {
			if (it != -1) position += it
		}
	}

	override fun available(): Int {
		ensureStreamOpened()
		return stream!!.available()
	}

	override fun close() {
		stream?.close()
	}

	private fun ensureStreamOpened() {
		if (stream == null) {
			val socket = Socket("127.0.0.1", port)
			socket.getOutputStream().let {
				it.write(1)
				writeString(it, path)
				it.flush()
			}
			stream = socket.getInputStream()
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
