package org.ddosolitary.okcagent

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.Socket


class GpgInputWrapper(port: Int, path: String?, context: Context) : InputStream() {
	var autoReopen = true
	private val file =
		File.createTempFile("input", null, context.cacheDir).also { it.deleteOnExit() }

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

	private var stream: InputStream = file.inputStream()

	override fun read(): Int {
		return stream.read()
	}

	override fun read(b: ByteArray): Int {
		return stream.read(b)
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		return stream.read(b, off, len)
	}

	override fun available(): Int {
		return stream.available()
	}

	override fun skip(n: Long): Long {
		return stream.skip(n)
	}

	override fun close() {
		stream.close()
		if (autoReopen) stream = file.inputStream()
		else file.delete()
	}
}
