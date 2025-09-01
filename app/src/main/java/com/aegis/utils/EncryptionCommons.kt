package com.aegis.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// ------------------- AES HELPERS -------------------

fun generateAESKey(): SecretKeySpec {
    val keyBytes = ByteArray(16)
    SecureRandom().nextBytes(keyBytes)
    return SecretKeySpec(keyBytes, "AES")
}

fun generateIV(): ByteArray {
    val iv = ByteArray(16)
    SecureRandom().nextBytes(iv)
    return iv
}

// ------------------- ENCRYPTION -------------------
fun encryptChunk(data: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
    return cipher.doFinal(data)
}

// ------------------- DECRYPTION -------------------

fun decryptChunk(encrypted: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
    return cipher.doFinal(encrypted)
}

//Do not load all files into memory, or it will OOM
fun reassembleMultipleFilesByHash(context: Context, hash: String, aesKey: SecretKeySpec): List<File> {
    val dir = File(context.filesDir, "encrypted_chunks")
    val manifestFile = File(dir, "manifest.json")
    if (!manifestFile.exists()) return emptyList()

    val manifest = JSONObject(manifestFile.readText())
    val filesArray = manifest.getJSONArray("files")
    val outputFiles = mutableListOf<File>()

    for (i in 0 until filesArray.length()) {
        val fileMeta = filesArray.getJSONObject(i)
        val fileNameWithExt = fileMeta.getString("fileName")

        val outFile = File(context.cacheDir, fileNameWithExt)
        FileOutputStream(outFile).use { outputStream ->

            val chunkArray = fileMeta.getJSONArray("chunks")
            for (j in 0 until chunkArray.length()) {
                val chunkMeta = chunkArray.getJSONObject(j)
                val iv = Base64.decode(chunkMeta.getString("iv"), Base64.NO_WRAP)
                val chunkFile = File(dir, getChunkFileName(hash, i, j))

                val encryptedChunk = chunkFile.readBytes()
                val decryptedChunk = decryptChunk(encryptedChunk, aesKey, iv)

                // Write chunk directly to output file instead of keeping in memory
                outputStream.write(decryptedChunk)
            }
        }

        outputFiles.add(outFile)
    }

    return outputFiles
}

// ------------------- SAVE TO GALLERY -------------------

fun saveReassembledFile(context: Context, file: File, originalFileName: String) {
    val isVideo = isVideoFile(file)
    val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
    val displayName = if (file.name.contains('.')) file.name else "$originalFileName${if (isVideo) ".mp4" else ".jpg"}"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
        }
    }

    val collection =
        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val uri = context.contentResolver.insert(collection, contentValues) ?: return
    context.contentResolver.openOutputStream(uri)?.use { output ->
        file.inputStream().use { it.copyTo(output) }
    }
}

// ------------------- HELPERS -------------------

fun getChunkFileName(hash: String, fileIndex: Int, chunkIndex: Int): String {
    return "$hash-$fileIndex-$chunkIndex.chunk"
}

fun clearAllChunks(context: Context) {
    val dir = File(context.filesDir, "encrypted_chunks")
    if (dir.exists() && dir.isDirectory) {
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }
}
