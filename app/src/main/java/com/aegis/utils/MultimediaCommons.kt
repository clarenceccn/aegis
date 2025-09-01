package com.aegis.utils

import java.io.File

fun isVideoFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext in listOf("mp4", "mov", "mkv", "avi", "webm")
}