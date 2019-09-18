package org.ddosolitary.okcagent

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.Socket

abstract class InputStreamWrapper : InputStream() {
	abstract fun getWrappedStream(): InputStream

	override fun read(): Int {
		return getWrappedStream().read()
	}

	override fun read(b: ByteArray): Int {
		return getWrappedStream().read(b)
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		return getWrappedStream().read(b, off, len)
	}

	override fun available(): Int {
		return getWrappedStream().available()
	}

	override fun skip(n: Long): Long {
		return getWrappedStream().skip(n)
	}

	override fun close() {
		getWrappedStream().close()
	}
}

class GpgInputWrapper(port: Int, path: String?, context: Context) : InputStreamWrapper() {
	private val file =
		File.createTempFile("input-", null, context.cacheDir).apply { deleteOnExit() }

	init {
		Socket("127.0.0.1", port).use { socket ->
			socket.getOutputStream().let { output ->
				output.write(1)
				writeString(output, path ?: "")
				output.flush()
			}
			val input = socket.getInputStream()
			val buf = ByteArray(65536)
			file.outputStream().use { output ->
				while (true) {
					val cnt = input.read(buf)
					if (cnt == -1) break
					output.write(buf, 0, cnt)
				}
			}
		}
	}

	private var stream: InputStream? = null

	override fun getWrappedStream(): InputStream {
		if (stream == null) stream = file.inputStream()
		return stream!!
	}

	override fun close() {
		super.close()
		file.delete()
	}

	fun getAutoReopenStream(): InputStream {
		return object : InputStreamWrapper() {
			override fun getWrappedStream(): InputStream {
				return this@GpgInputWrapper
			}

			override fun close() {
				this@GpgInputWrapper.let {
					it.stream?.close()
					it.stream = null
				}
			}
		}
	}
}
