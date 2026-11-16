package com.mora.shield

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.min

@Composable
fun EmbeddedVncViewerCard() {
    val context = LocalContext.current
    val surface = remember { VncSurface(context) }
    var password by remember { mutableStateOf("") }
    var keyboardText by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Belum terhubung · 127.0.0.1:5901") }

    DisposableEffect(surface) {
        surface.onStatus = { message, ready ->
            status = message
            connected = ready
        }
        onDispose { surface.disconnect() }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Desktop Viewer bawaan Mora", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(8) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password VNC") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !connected, onClick = {
                    surface.connect("127.0.0.1", 5901, password)
                    password = ""
                }) { Text("Hubungkan") }
                OutlinedButton(enabled = connected, onClick = { surface.disconnect() }) { Text("Putuskan") }
            }
            AndroidView(
                factory = { surface },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp)
            )
            OutlinedTextField(
                value = keyboardText,
                onValueChange = { next ->
                    if (next.length > keyboardText.length && next.startsWith(keyboardText)) {
                        surface.sendText(next.substring(keyboardText.length))
                    } else if (next.length < keyboardText.length && keyboardText.startsWith(next)) {
                        repeat(keyboardText.length - next.length) { surface.sendKey(0xff08) }
                    }
                    keyboardText = if (next.length > 80) next.takeLast(40) else next
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ketik ke desktop Linux") },
                placeholder = { Text("Teks dikirim ke jendela aktif") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    surface.sendText("\n")
                    keyboardText = ""
                })
            )
            Text("Sentuh untuk klik/drag. Gunakan kolom ketik untuk memasukkan teks. Viewer ini meminta encoding Raw agar kompatibel; performa bergantung pada resolusi desktop dan perangkat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private class VncSurface(context: android.content.Context) : View(context) {
    @Volatile var onStatus: ((String, Boolean) -> Unit)? = null
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var client: RfbClient? = null
    @Volatile private var frame: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var pointerDown = false

    fun connect(host: String, port: Int, password: String) {
        disconnect()
        onStatus?.invoke("Menghubungkan ke $host:$port…", false)
        worker.execute {
            try {
                val newClient = RfbClient(
                    host = host,
                    port = port,
                    password = password,
                    onFrame = { bitmap ->
                        frame = bitmap
                        postInvalidate()
                    },
                    onStatus = { message, ready -> post { onStatus?.invoke(message, ready) } }
                )
                client = newClient
                newClient.run()
            } catch (error: Exception) {
                post { onStatus?.invoke("Koneksi gagal: ${error.message ?: "kesalahan jaringan"}", false) }
            } finally {
                client = null
                post { onStatus?.invoke("Koneksi terputus", false) }
            }
        }
    }

    fun disconnect() {
        client?.close()
        client = null
    }

    fun sendText(value: String) {
        client?.sendText(value)
    }

    fun sendKey(keysym: Int) {
        client?.sendKey(keysym, true)
        client?.sendKey(keysym, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.rgb(18, 20, 26))
        val bitmap = frame ?: return
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + w, top + h), paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bitmap = frame ?: return false
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        if (scale <= 0f) return false
        val left = (width - bitmap.width * scale) / 2f
        val top = (height - bitmap.height * scale) / 2f
        val x = ((event.x - left) / scale).toInt().coerceIn(0, bitmap.width - 1)
        val y = ((event.y - top) / scale).toInt().coerceIn(0, bitmap.height - 1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerDown = true
                client?.pointer(x, y, 1)
            }
            MotionEvent.ACTION_MOVE -> if (pointerDown) client?.pointer(x, y, 1)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerDown = false
                client?.pointer(x, y, 0)
            }
        }
        return true
    }
}

private class RfbClient(
    private val host: String,
    private val port: Int,
    password: String,
    private val onFrame: (Bitmap) -> Unit,
    private val onStatus: (String, Boolean) -> Unit
) {
    private val socket = Socket()
    private lateinit var input: DataInputStream
    private lateinit var output: BufferedOutputStream
    @Volatile private var closed = false
    @Volatile private var width = 0
    @Volatile private var height = 0
    private var bitmap: Bitmap? = null
    private val ioLock = Any()
    private val secret = password

    fun run() {
        try { runConnection() } finally { close() }
    }

    private fun runConnection() {
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(host, port), 5000)
        socket.soTimeout = 0
        input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        output = BufferedOutputStream(socket.getOutputStream())

        val version = ByteArray(12)
        input.readFully(version)
        write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))

        val securityCount = input.readUnsignedByte()
        if (securityCount == 0) throw IllegalStateException("Server menolak koneksi: ${readString(input)}")
        val securityTypes = ByteArray(securityCount)
        input.readFully(securityTypes)
        if (!securityTypes.contains(2.toByte())) throw IllegalStateException("Server tidak menawarkan autentikasi VNC password")
        write(byteArrayOf(2))

        val challenge = ByteArray(16)
        input.readFully(challenge)
        val response = encryptChallenge(challenge, secret)
        write(response)
        val securityResult = input.readInt()
        if (securityResult != 0) throw SecurityException(if (securityResult == 1) "Password VNC salah" else "Autentikasi VNC gagal")

        write(byteArrayOf(1)) // ClientInit: shared
        width = input.readUnsignedShort()
        height = input.readUnsignedShort()
        val pixelFormat = ByteArray(16)
        input.readFully(pixelFormat)
        val nameLength = input.readInt().coerceIn(0, 65536)
        val desktopName = ByteArray(nameLength)
        input.readFully(desktopName)

        if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > 16_000_000L) throw IllegalStateException("Resolusi desktop terlalu besar; turunkan resolusi VNC")
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        setPixelFormat()
        setEncodings()
        requestUpdate(false)
        onStatus("Terhubung · ${String(desktopName, Charsets.UTF_8)} · ${width}×${height}", true)

        while (!closed && !socket.isClosed) {
            when (input.readUnsignedByte()) {
                0 -> readFramebufferUpdate()
                1 -> skipColourMap()
                2 -> Unit // Bell
                3 -> readServerCutText()
                else -> throw IllegalStateException("Pesan RFB tidak dikenal")
            }
        }
    }

    private fun readFramebufferUpdate() {
        input.readUnsignedByte()
        val count = input.readUnsignedShort()
        repeat(count) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            val encoding = input.readInt()
            when (encoding) {
                0 -> {
                    if (w == 0 || h == 0 || w.toLong() * h.toLong() > 16_000_000L) throw IllegalStateException("Ukuran frame tidak valid")
                    val bytes = ByteArray(w * h * 4)
                    input.readFully(bytes)
                    val pixels = IntArray(w * h)
                    var p = 0
                    for (i in pixels.indices) {
                        val b = bytes[p++].toInt() and 0xff
                        val g = bytes[p++].toInt() and 0xff
                        val r = bytes[p++].toInt() and 0xff
                        p++ // padding
                        pixels[i] = android.graphics.Color.rgb(r, g, b)
                    }
                    val target = bitmap ?: return@repeat
                    if (x + w <= target.width && y + h <= target.height) target.setPixels(pixels, 0, w, x, y, w, h)
                }
                -223 -> resizeDesktop(w, h)
                -239 -> {
                    val header = ByteArray(4)
                    input.readFully(header)
                    val dataLength = input.readUnsignedShort()
                    input.skipFully(dataLength.toLong())
                }
                else -> throw IllegalStateException("Encoding server tidak didukung: $encoding")
            }
        }
        bitmap?.let(onFrame)
        requestUpdate(true)
    }

    private fun resizeDesktop(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || w.toLong() * h.toLong() > 16_000_000L) return
        width = w
        height = h
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun setPixelFormat() {
        val data = ByteArray(20)
        data[0] = 0
        data[4] = 32
        data[5] = 24
        data[6] = 0
        data[7] = 1
        data[8] = 0
        data[9] = -1
        data[10] = 0
        data[11] = -1
        data[12] = 0
        data[13] = -1
        data[14] = 16
        data[15] = 8
        data[16] = 0
        write(data)
    }

    private fun setEncodings() {
        val data = ByteArray(12)
        data[0] = 2
        data[2] = 0
        data[3] = 2
        putInt(data, 4, 0)
        putInt(data, 8, -223)
        write(data)
    }

    private fun requestUpdate(incremental: Boolean) {
        val data = ByteArray(10)
        data[0] = 3
        data[1] = if (incremental) 1 else 0
        putShort(data, 6, width)
        putShort(data, 8, height)
        write(data)
    }

    fun pointer(x: Int, y: Int, buttons: Int) {
        val data = ByteArray(6)
        data[0] = 5
        data[1] = buttons.toByte()
        putShort(data, 2, x)
        putShort(data, 4, y)
        writeSafe(data)
    }

    fun sendText(text: String) {
        text.forEach { char ->
            val keysym = when (char) {
                '\n', '\r' -> 0xff0d
                '\t' -> 0xff09
                '\b' -> 0xff08
                else -> char.code.takeIf { it in 32..126 } ?: return@forEach
            }
            sendKey(keysym, true)
            sendKey(keysym, false)
        }
    }

    fun sendKey(keysym: Int, down: Boolean) {
        val data = ByteArray(8)
        data[0] = 4
        data[1] = if (down) 1 else 0
        putInt(data, 4, keysym)
        writeSafe(data)
    }

    private fun skipColourMap() {
        input.readUnsignedByte()
        input.readUnsignedShort()
        val count = input.readUnsignedShort()
        input.skipFully(count * 6L)
    }

    private fun readServerCutText() {
        input.skipFully(3)
        val length = input.readInt().coerceIn(0, 1_048_576)
        input.skipFully(length.toLong())
    }

    private fun write(bytes: ByteArray) {
        synchronized(ioLock) {
            output.write(bytes)
            output.flush()
        }
    }

    private fun writeSafe(bytes: ByteArray) {
        try { if (!closed && ::output.isInitialized) write(bytes) } catch (_: Exception) { }
    }

    fun close() {
        closed = true
        try { socket.close() } catch (_: Exception) { }
    }

    private fun encryptChallenge(challenge: ByteArray, password: String): ByteArray {
        val key = ByteArray(8)
        val source = password.toByteArray(Charsets.ISO_8859_1)
        for (i in 0 until min(8, source.size)) key[i] = reverseBits(source[i])
        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DES"))
        return cipher.doFinal(challenge)
    }

    private fun reverseBits(value: Byte): Byte {
        var v = value.toInt() and 0xff
        var reversed = 0
        repeat(8) { reversed = (reversed shl 1) or (v and 1); v = v shr 1 }
        return reversed.toByte()
    }

    private fun putShort(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value shr 8).toByte()
        target[offset + 1] = value.toByte()
    }

    private fun putInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value shr 24).toByte()
        target[offset + 1] = (value shr 16).toByte()
        target[offset + 2] = (value shr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readString(input: DataInputStream): String {
        val length = input.readInt().coerceIn(0, 65536)
        val data = ByteArray(length)
        input.readFully(data)
        return String(data, Charsets.UTF_8)
    }

    private fun DataInputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skipBytes(min(remaining, Int.MAX_VALUE.toLong()).toInt())
            if (skipped <= 0) { readByte(); remaining-- } else remaining -= skipped
        }
    }
}
