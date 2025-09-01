package com.aegis.components.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun CopyHashTextBlock(combinedKeyOutput: String, clipboardManager: ClipboardManager, context: Context) {
    // ---------------- Copy hash + AES key ----------------
    if (combinedKeyOutput.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(combinedKeyOutput, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(combinedKeyOutput))
                Toast.makeText(context, "Hash + AES Key copied!", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy hash + key")
            }
        }
    }
}