package org.ddosolitary.okcagent.gpg

import org.ddosolitary.okcagent.writeString
import java.io.OutputStream
import java.net.Socket

class GpgOutputWrapper(
	private val port: Int,
	private val path: String
) : OutputStream() {
	private var stream: OutputStream? = null

	override fun write(b: Int) {
		write(byteArrayOf(b.toByte()))
	}

	override fun write(b: ByteArray) {
		write(b, 0, b.size)
	}

	override fun write(b: ByteArray, off: Int, len: Int) {
		if (stream == null) {
			val socketStream = Socket("127.0.0.1", port)
				.apply { setSoLinger(true, 1) }.getOutputStream()
			socketStream.write(2)
			writeString(socketStream, path)
			stream = socketStream
		}
		stream!!.write(b, off, len)
	}

	override fun flush() {
		stream?.flush()
	}

	override fun close() {
		stream?.close()
	}
}
