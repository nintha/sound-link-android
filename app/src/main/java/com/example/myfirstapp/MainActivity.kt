package com.example.myfirstapp

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.myfirstapp.util.itag
import java.net.Socket


class MainActivity : AppCompatActivity() {
    private lateinit var voiceRelayService: VoiceRelayService
    private var mBound = false
    @Volatile
    private var speakerStatus: Boolean = false
    @Volatile
    private var micStatus: Boolean = false
    private var audioRecord: AudioRecord? = null
    private var enhanceLevel: Int = 100
    /** Defines callbacks for service binding, passed to bindService()  */
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as VoiceRelayService.VoiceRelayBinder
            voiceRelayService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Bind to LocalService
        val intent = Intent(this, VoiceRelayService::class.java)
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE)

        val enhanceValue: TextView = findViewById(R.id.tvEnhanceValue)
        enhanceValue.text = "100 %"
        val seekBar: SeekBar = findViewById(R.id.seekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                enhanceLevel = progress * 3 + 100
                enhanceValue.text = "$enhanceLevel %"
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection)
            mBound = false
        }
    }

    fun toggleConnection(view: View) {
        if (!mBound) {
            Toast.makeText(this, "not bind service", Toast.LENGTH_SHORT).show()
            return
        }

        val speaker: Switch = findViewById(R.id.swConnection)
        speakerStatus = speaker.isChecked
        Log.i(itag(), "speaker=$speakerStatus, this=$this")
        val serverIpText: EditText = findViewById<EditText>(R.id.serverIp)
        val serverPortText: EditText = findViewById<EditText>(R.id.serverPort)

        val ip = serverIpText.text.toString()
        val port = serverPortText.text.toString().toInt()
        voiceRelayService.status = speakerStatus
        if (speakerStatus) {
            voiceRelayService.connectTo(ip, port)
            Toast.makeText(this, "connect to $ip:$port", Toast.LENGTH_SHORT).show()
        } else {
            voiceRelayService.disconnect()
            Toast.makeText(this, "disconnect to $ip:$port", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleMicrophone(view: View) {
        val serverIpText: EditText = findViewById<EditText>(R.id.serverIp)
        val serverPortText: EditText = findViewById<EditText>(R.id.serverPort)

        val ip = serverIpText.text.toString()
        val port = serverPortText.text.toString().toInt()

        micStatus = findViewById<Switch>(R.id.swMicrophone).isChecked
        val filename = Environment.getExternalStorageDirectory().path + "/out.pcm"
        val sampleRate = 44100
        val channels = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeInBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            channels,
            audioEncoding
        )
        if (micStatus) {
            Log.i(itag(), "bufferSizeInBytes=$bufferSizeInBytes")
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channels, audioEncoding,
                bufferSizeInBytes
            )
            this.audioRecord = audioRecord
            audioRecord.startRecording()

            val block: () -> Unit = {
                val socket = Socket(ip, port)
                socket.getOutputStream().use { os ->
                    os.write(0x01)
                    val data = ByteArray(bufferSizeInBytes)
                    while (micStatus) {
                        val read = audioRecord.read(data, 0, bufferSizeInBytes)
                        if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                            enhanceVolume(data, enhanceLevel / 10)
                            os.write(data)
                            os.flush()
                        }
                    }
                    Log.i(itag(), "mic end loop")
                    socket.close()
                }
            }

            Thread {
                try {
                    block()
                } catch (e: Exception) {
                    Log.e(itag(), "mic error", e)
                }
            }.start()
        } else {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    private fun enhanceVolume(data: ByteArray, level: Int) {
        var i = 0
        while (i < data.size) {
            data[i] = data[i].times(level).div(100).toByte()
            if (i % 2 == 1 && data[i].toInt() > 0xE0) {
                data[i] = 0xE0.toByte()
            }
            i++
        }
    }
}
