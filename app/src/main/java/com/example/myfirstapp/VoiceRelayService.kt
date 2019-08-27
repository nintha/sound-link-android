package com.example.myfirstapp

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.myfirstapp.util.itag

class VoiceRelayService : Service() {
    private val binder = VoiceRelayBinder()
    var status = false
    var thread: VoiceRelayThread? = null

    inner class VoiceRelayBinder : Binder() {
        val service: VoiceRelayService
            get() = this@VoiceRelayService
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(itag(), "onBind")
        return binder
    }

    fun connectTo(ip: String, port: Int) {
        Log.i(itag(), "connect > ip=$ip, port=$port, status=$status, this=$this")
        if (!status) {
            return
        }
        thread?.close()
        thread = VoiceRelayThread(ip, port)
        Thread(thread).start()
    }

    fun disconnect() {
        Log.i(itag(), "disconnect > switch=$status, thread=$thread, this=$this")
        if (status) {
            return
        }
        thread?.close()
    }
}
