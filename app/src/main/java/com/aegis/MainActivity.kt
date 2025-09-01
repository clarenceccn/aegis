package com.aegis

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.net.Uri
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
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.aegis.components.buttons.ClearAllFileBlocksBtn
import com.aegis.components.buttons.ShowFileBlocks
import com.aegis.components.views.MyFilesHeaderText
import com.aegis.components.views.VideoPlayerWithControls
import com.aegis.utils.clearAllChunks
import com.aegis.utils.encryptChunk
import com.aegis.utils.generateAESKey
import com.aegis.utils.generateIV
import com.aegis.utils.getChunkFileName
import com.aegis.utils.isVideoFile
import com.aegis.utils.reassembleMultipleFilesByHash
import com.aegis.utils.saveReassembledFile
import com.aegis.ui.theme.AegisTheme
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AegisTheme {
                MultiFileChunkEncryptScreen()
            }
        }
    }
}

@Preview
@Composable
fun PreviewMain() {
    AegisTheme {
        MultiFileChunkEncryptScreen()
    }
}

@Composable
fun ColorThemeWrapper(content: @Composable () -> Unit) {
// Dark / Modern Scheme
    val PrimaryColor = Color(0xFF1F2937)
    val OnPrimaryColor = Color(0xFFFFFFFF)
    val SecondaryColor = Color(0xFF2563EB)
    val OnSecondaryColor = Color(0xFFFFFFFF)
    val SurfaceColor = Color(0xFF111827)
    val OnSurfaceColor = Color(0xFFE5E7EB)
    val BackgroundColor = Color(0xFF0F172A)
    val OnBackgroundColor = Color(0xFFE5E7EB)
    val ErrorColor = Color(0xFFEF4444)
    val OnErrorColor = Color(0xFFFFFFFF)

    // Tertiary / accent colors
    val TertiaryGreen = Color(0xFF10B981)
    val TertiaryAmber = Color(0xFFFBBF24)
    val TertiaryPurple = Color(0xFF8B5CF6)
    val OnTertiary = Color(0xFFFFFFFF)

    val DarkColorScheme = darkColorScheme(
        primary = PrimaryColor,
        onPrimary = OnPrimaryColor,
        secondary = SecondaryColor,
        onSecondary = OnSecondaryColor,
        surface = SurfaceColor,
        onSurface = OnSurfaceColor,
        background = BackgroundColor,
        onBackground = OnBackgroundColor,
        error = ErrorColor,
        onError = OnErrorColor,
        tertiary = TertiaryGreen,
        onTertiary = OnTertiary
    )

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

// ----------------------------------------------
// Compose UI
// ----------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiFileChunkEncryptScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isVideoList by remember { mutableStateOf<List<Boolean>>(emptyList()) }

    var combinedKeyOutput by remember { mutableStateOf("") }

    var reconstructedFiles by remember { mutableStateOf<List<Triple<File, Boolean, String>>>(emptyList()) }
    var inputCombinedKey by remember { mutableStateOf("") }

    var isInputValid by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()
    var chunkList by remember { mutableStateOf<List<Pair<String, Date>>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> ->
            selectedUris = uris
            isVideoList = uris.map { uri ->
                context.contentResolver.getType(uri)?.startsWith("video") == true
            }
        }
    )

    Scaffold(
        topBar = { TopAppBar(title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Shield,
                    contentDescription = "Logo"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Project: AEGIS")
            }
        }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add FileBlock"
                )
            }
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ShowFileBlocks(
                    context,
                    chunkList,
                    onUpdateList = { newList -> chunkList = newList }
                )

                ClearAllFileBlocksBtn(
                    onConfirmClear = {
                        clearAllChunks(context)
                        Toast.makeText(context, "All chunks cleared!", Toast.LENGTH_SHORT).show()
                        // Reset state
                        selectedUris = emptyList()
                        isVideoList = emptyList()
                        combinedKeyOutput = ""
                        reconstructedFiles = emptyList()
                        chunkList = emptyList()
                    }
                )

                // ---------------- Reassemble Section ----------------
                OutlinedTextField(
                    value = inputCombinedKey,
                    onValueChange = {
                        inputCombinedKey = it
                        isInputValid = inputCombinedKey.contains(":") && inputCombinedKey.split(":").size == 2
                    },
                    label = { Text("Paste hash + AES key") },
                    isError = !isInputValid,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (inputCombinedKey.isNotEmpty()) {
                            IconButton(onClick = { inputCombinedKey = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear input"
                                )
                            }
                        }
                    }
                )

                if (!isInputValid) {
                    Text(
                        text = "Invalid format! Use <hash>:<aesKeyBase64>",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        if (!isInputValid) {
                            Toast.makeText(context, "Invalid hash + AES key!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val parts = inputCombinedKey.split(":")
                        val hash = parts[0]
                        val aesBase64 = parts[1]

                        try {
                            val aesKey = SecretKeySpec(Base64.decode(aesBase64, Base64.NO_WRAP), "AES")
                            val files = reassembleMultipleFilesByHash(context, hash, aesKey)

                            if (files.isEmpty()) {
                                Toast.makeText(context, "Invalid hash + AES key!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            reconstructedFiles = files.map { file ->
                                val isVideo = isVideoFile(file)
                                val originalName = file.nameWithoutExtension
                                Triple(file, isVideo, originalName)
                            }

                        } catch (e: Exception) {
                            Toast.makeText(context, "Invalid hash + AES key!", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Compute")
                    }
                }


                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )

                // ---------------- Reassembled Files Section ----------------
                if (reconstructedFiles.isNotEmpty()) {
                    MyFilesHeaderText()
                    Button(
                        onClick = {
                            reconstructedFiles.forEach { triple ->
                                val (file, isVideo, originalName) = triple
                                saveReassembledFile(context, file, originalName)
                            }
                            Toast.makeText(context, "All files saved", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save All")
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp) // limit height before scroll kicks in
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        reconstructedFiles.forEach { (file, isVideo, originalName) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp) // inner padding
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isVideo) {
                                        VideoPlayerWithControls(
                                            file.toUri(),
                                            context,
                                            modifier = Modifier.size(200.dp)
                                        )
                                    } else {
                                        AsyncImage(
                                            model = file.toUri(),
                                            contentDescription = null,
                                            modifier = Modifier.size(200.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            saveReassembledFile(context, file, originalName)
                                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download"
                                        )
                                    }
                                }
                                Text(
                                    text = file.toUri().path.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                        }
                    }

                }
                if (showDialog) {
                    AlertDialog(
                        containerColor = MaterialTheme.colorScheme.background,
                        onDismissRequest = { showDialog = false },
                        title = { Text("Upload Files") },
                        text = {
                            Column {
                                Text("Click the box below to select files:")
                                Spacer(modifier = Modifier.height(8.dp))

                                // Clickable box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceBright,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            launcher.launch("*/*") // pick all types, or "image/*" / "video/*"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Click to select files",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Display selected file names
                                if (selectedUris.isNotEmpty()) {
                                    Text("Selected files:", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp) // limit height before scroll kicks in
                                            .verticalScroll(rememberScrollState())
                                            .padding(20.dp)
                                    ) {
                                        selectedUris.forEachIndexed { i, uri ->
                                            if (isVideoList[i]) VideoPlayerWithControls(
                                                uri,
                                                context,
                                                modifier = Modifier.fillMaxWidth().height(200.dp)
                                            )
                                            else AsyncImage(
                                                model = uri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxWidth().height(200.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        selectedUris.forEach { uri ->
                                            val fileName = uri.lastPathSegment ?: uri.toString()
                                            Text(
                                                text = fileName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(
                                                    start = 8.dp,
                                                    top = 2.dp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary ),
                                onClick = {
                                if (selectedUris.isNotEmpty()) {
                                    val aesKey = generateAESKey()
                                    val aesKeyBase64 = Base64.encodeToString(aesKey.encoded, Base64.NO_WRAP)
                                    val hash = UUID.randomUUID().toString()
                                    val dir = File(context.filesDir, "encrypted_chunks").apply { if (!exists()) mkdir() }

                                    val manifest = JSONObject()
                                    val filesArray = JSONArray()

                                    selectedUris.forEachIndexed { fileIndex, uri ->
                                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                                        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
                                        val originalName = uri.lastPathSegment ?: "file_$fileIndex"
                                        val fileNameWithExt = "$originalName.$ext"

                                        val fileMeta = JSONObject()
                                        fileMeta.put("fileIndex", fileIndex)
                                        fileMeta.put("fileName", fileNameWithExt)

                                        val chunkArray = JSONArray()
                                        context.contentResolver.openInputStream(uri)?.use { stream ->
                                            val buffer = ByteArray(1024 * 1024) // 1 MB buffer
                                            var bytesRead: Int
                                            var chunkIndex = 0

                                            while (stream.read(buffer).also { bytesRead = it } != -1) {
                                                val chunk = if (bytesRead < buffer.size) buffer.copyOf(bytesRead) else buffer

                                                val iv = generateIV()
                                                val encrypted = encryptChunk(chunk, aesKey, iv)
                                                val chunkFile = File(dir, getChunkFileName(hash, fileIndex, chunkIndex))
                                                FileOutputStream(chunkFile).use { it.write(encrypted) }

                                                val chunkMeta = JSONObject().apply {
                                                    put("index", chunkIndex)
                                                    put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                                                }
                                                chunkArray.put(chunkMeta)

                                                chunkIndex++
                                            }
                                        }

                                        fileMeta.put("chunks", chunkArray)
                                        filesArray.put(fileMeta)
                                    }

                                    manifest.put("files", filesArray)
                                    File(dir, "manifest.json").apply { FileOutputStream(this).use { it.write(manifest.toString().toByteArray()) } }

                                    combinedKeyOutput = "$hash:$aesKeyBase64"
                                    clipboardManager.setText(AnnotatedString(combinedKeyOutput))
                                    Toast.makeText(context, "Hash + AES Key copied!", Toast.LENGTH_SHORT).show()
                                }
                                
                                //close dialog and clear
                                showDialog = false
                                selectedUris = emptyList()
                            }) { Text("Upload") }
                        }
                    )
                }
            }
        }
    )
}