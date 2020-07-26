package org.ddosolitary.okcagent.gpg

import android.content.Context
import org.ddosolitary.okcagent.writeString
import java.io.File
import java.io.OutputStream
import java.net.Socket

abstract class OutputStreamWrapper : OutputStream() {
	abstract fun getWrappedStream(): OutputStream

	override fun write(b: Int) {
		getWrappedStream().write(b)
	}

	override fun write(b: ByteArray) {
		getWrappedStream().write(b)
	}

	override fun write(b: ByteArray, off: Int, len: Int) {
		getWrappedStream().write(b, off, len)
	}

	override fun flush() {
		getWrappedStream().flush()
	}

	override fun close() {
		getWrappedStream().close()
	}
}

class GpgOutputWrapper(
	private val port: Int,
	private val path: String,
	private val context: Context
) : OutputStreamWrapper() {
	private var file: File? = null
	private var stream: OutputStream? = null

	override fun getWrappedStream(): OutputStream {
		if (stream == null) {
			file?.delete()
			file = File.createTempFile("output-", null, context.cacheDir).apply { deleteOnExit() }
			stream = file!!.outputStream()
		}
		return stream!!
	}

	override fun close() {
		stream?.close()
		file?.let {
			it.inputStream().use a@{ input ->
				Socket("127.0.0.1", port).apply { setSoLinger(true, 1) }.getOutputStream()
					.use { output ->
						output.write(2)
						writeString(output, path)
						val buf = ByteArray(65536)
						while (true) {
							val cnt = input.read(buf)
							if (cnt == -1) return@a
							output.write(buf, 0, cnt)
						}
					}
			}
			it.delete()
		}
	}

	fun getAutoReopenStream(): OutputStream {
		return object : OutputStreamWrapper() {
			override fun getWrappedStream(): OutputStream {
				return this@GpgOutputWrapper
			}

			override fun close() {
				this@GpgOutputWrapper.let {
					it.stream?.close()
					it.stream = null
				}
			}
		}
	}
}
