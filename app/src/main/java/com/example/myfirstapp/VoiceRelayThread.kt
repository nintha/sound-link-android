package com.example.myfirstapp

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.example.myfirstapp.util.itag
import java.io.InputStream
import java.net.Socket

class VoiceRelayThread(val ip: String, val port: Int) : Runnable {
    @Volatile
    private var running = false
    private val audioTrack: AudioTrack

    init {
        val sampleRate = 44100
        val channels = AudioFormat.CHANNEL_OUT_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channels,
            audioEncoding
        )
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channels,
            audioEncoding,
            bufferSize * 4,
            AudioTrack.MODE_STREAM
        )
        audioTrack.play()
        Log.i(itag(), "init audioTrack, bufferSize=$bufferSize")
    }

    fun close() {
        Log.i(itag(), "close")
        running = false
        audioTrack.stop()
    }

    override fun run() {
        running = true
        val socket: Socket = try {
            Socket(ip, port)
        } catch (e: Exception) {
            Log.e(itag(), "error", e)
            return
        }
        socket.use {
            socket.getOutputStream().write(0x00)
            val block: (InputStream) -> Unit = {
                val bytes = ByteArray(32)
                while (running && !Thread.currentThread().isInterrupted) {
                    try {
                        // 防止声音出现延迟
                        if (it.available() >= bytes.size * 256) {
                            val available = it.available()
                            Log.i(itag(), "block too many, available=$available")
                            audioTrack.stop()
                            it.read(ByteArray(available))
                            audioTrack.play()
                        }
                        it.read(bytes)
                        audioTrack.write(bytes, 0, bytes.size)
                        audioTrack.flush()
                    } catch (e: Exception) {
                        Log.e(itag(), "error", e)
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                socket.close()
            }
            socket.getInputStream().use(block)
        }
    }
}