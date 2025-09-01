package com.aegis.components.buttons

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowFileBlocks(
    context: Context,
    chunkList: List<Pair<String, Date>>,
    onUpdateList: (List<Pair<String, Date>>) -> Unit
) {
    Button(
        onClick = {
            val chunkDir = File(context.filesDir, "encrypted_chunks")
            val chunkFiles = chunkDir.listFiles()?.map {
                it.name to Date(it.lastModified())
            } ?: emptyList()

            if (chunkFiles.isEmpty()) {
                Toast.makeText(context, "No chunks found", Toast.LENGTH_SHORT).show()
                onUpdateList(emptyList())
            } else {
                onUpdateList(chunkFiles)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("List All FileBlocks")
    }

    if (chunkList.isNotEmpty()) {
        Text(
            text = "FileBlocks found:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp) // limit height before scroll kicks in
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp)
        ) {
            chunkList.forEach { (chunk, date) ->
                Text(
                    text = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)} • $chunk",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}
