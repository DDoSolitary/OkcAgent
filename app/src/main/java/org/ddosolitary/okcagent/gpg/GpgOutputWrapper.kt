package org.ddosolitary.okcagent.gpg

import org.ddosolitary.okcagent.writeString
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GpgOutputWrapper(port: Int, path: String) : OutputStream() {
	private val streamDelegate = lazy {
		Socket("127.0.0.1", port).apply { setSoLinger(true, 1) }.getOutputStream().also {
			it.write(2)
			writeString(it, path)
		}
	}
	private val stream by streamDelegate
	private var closed = false

	override fun write(b: Int) {
		write(byteArrayOf(b.toByte()))
	}

	override fun write(b: ByteArray) {
		write(b, 0, b.size)
	}

	override fun write(b: ByteArray, off: Int, len: Int) {
		var cur = off
		while (cur < off + len) {
			val frameLen = minOf(UShort.MAX_VALUE.toInt(), off + len - cur)
			val lenBuf = ByteBuffer.allocate(Short.SIZE_BYTES).apply {
				order(ByteOrder.BIG_ENDIAN)
				putShort(frameLen.toUShort().toShort())
			}.array()
			stream.write(lenBuf)
			stream.write(b, cur, frameLen)
			cur += frameLen
		}
	}

	override fun flush() {
		stream.flush()
	}

	override fun close() {
		if (streamDelegate.isInitialized() && !closed) {
			stream.write(byteArrayOf(0, 0))
			stream.close()
			closed = true
		}
	}
}
