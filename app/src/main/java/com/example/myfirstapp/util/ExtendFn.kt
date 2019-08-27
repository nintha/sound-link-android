package com.example.myfirstapp.util

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*


val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
private fun nowTimeString(): String {
    return dateFormat.format(Date())
}

fun <T> T.itag(): String where T : Any =
    nowTimeString() + " - " + this::class.java.simpleName