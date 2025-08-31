package com.aegis

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileChunkEncryptScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileChunkEncryptScreen() {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // File picker launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            isVideo = context.contentResolver.getType(it)?.startsWith("video") ?: false
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            bytes?.let { data ->
                Log.d("FileInfo", "Original size: ${data.size} bytes")

                // Chunk bytes
                val chunks = chunkBytes(data, 1024 * 1024)
                Log.d("FileInfo", "Total chunks: ${chunks.size}")

                // Prepare directory
                val dir = File(context.filesDir, "encrypted_chunks")
                if (!dir.exists()) dir.mkdir()

                // Manifest JSON
                val manifest = JSONArray()

                chunks.forEachIndexed { index, chunk ->
                    val key = generateAESKey()
                    val iv = generateIV()
                    val encryptedChunk = encryptChunk(chunk, key, iv)

                    // Save encrypted chunk
                    val chunkFile = File(dir, "chunk_$index.enc")
                    FileOutputStream(chunkFile).use { it.write(encryptedChunk) }

                    // Add metadata to manifest
                    val chunkMeta = JSONObject()
                    chunkMeta.put("index", index)
                    chunkMeta.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    chunkMeta.put("fileName", chunkFile.name)
                    manifest.put(chunkMeta)

                    Log.d("FileInfo", "Encrypted Chunk $index saved: ${chunkFile.absolutePath}")
                }

                // Save manifest
                val manifestFile = File(dir, "manifest.json")
                FileOutputStream(manifestFile).use { it.write(manifest.toString().toByteArray()) }
                Log.d("FileInfo", "Manifest saved: ${manifestFile.absolutePath}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("File Chunk & Encrypt") })
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { launcher.launch("*/*") }) {
                    Text("Select Photo/Video")
                }

                selectedUri?.let { uri ->
                    if (isVideo) {
                        VideoPlayer(uri = uri, context = context)
                    } else {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(300.dp)
                        )
                    }
                } ?: Text("No file selected")
            }
        }
    )
}

// Chunking
fun chunkBytes(data: ByteArray, chunkSize: Int): List<ByteArray> {
    val chunks = mutableListOf<ByteArray>()
    var start = 0
    while (start < data.size) {
        val end = (start + chunkSize).coerceAtMost(data.size)
        chunks.add(data.copyOfRange(start, end))
        start = end
    }
    return chunks
}

// AES Encryption
fun encryptChunk(chunk: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
    return cipher.doFinal(chunk)
}

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

// Video Player
@Composable
fun VideoPlayer(uri: Uri, context: android.content.Context) {
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply { player = exoPlayer }
        },
        modifier = Modifier.fillMaxWidth().height(300.dp)
    )
}
