package com.aegis.components.buttons

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearAllFileBlocksBtn(
    onConfirmClear: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showClearDialog = true },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) {
        Text("Clear All Chunks")
    }

    if (showClearDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = { showClearDialog = false },
            title = { Text("Confirm Clear") },
            text = { Text("Are you sure you want to delete all chunks from internal storage? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary ),
                    onClick = {
                        onConfirmClear()
                        showClearDialog = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary ),
                    onClick = { showClearDialog = false }
                ) {
                    Text("No")
                }
            }
        )
    }
}

