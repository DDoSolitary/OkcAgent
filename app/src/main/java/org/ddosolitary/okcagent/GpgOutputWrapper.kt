package org.ddosolitary.okcagent

import java.io.OutputStream
import java.net.Socket

class GpgOutputWrapper(
	private val port: Int,
	private val path: String?,
	private val translateLf: Boolean
) : OutputStream() {
	private var stream: OutputStream? = null

	override fun write(b: Int) {
		write(byteArrayOf(b.toByte()), 0, 1)
	}

	override fun write(b: ByteArray, off: Int, len: Int) {
		if (len == 0) return
		if (stream == null) {
			stream = Socket("127.0.0.1", port).getOutputStream().also {
				it.write(2)
				writeString(it, path ?: "")
			}
		}
		stream!!.let {
			if (translateLf) {
				var last = off
				for (i in off..off + len) {
					if (i == off + len) {
						if (last < i) it.write(b, last, i - last)
					} else if (b[i] == '\n'.toByte()) {
						if (last < i) it.write(b, last, i - last)
						it.write(byteArrayOf('\r'.toByte(), '\n'.toByte()))
						last = i + 1
					}
				}
			} else it.write(b, off, len)
		}
	}

	override fun flush() {
		stream?.flush()
	}

	override fun close() {
		stream?.close()
	}
}
