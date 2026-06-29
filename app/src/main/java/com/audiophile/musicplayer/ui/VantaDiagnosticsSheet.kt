package com.audiophile.musicplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.debug.VantaDiagnosticLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VantaDiagnosticsSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val logText = remember { VantaDiagnosticLog.readText(maxLines = 400) }
    val lines = remember(logText) {
        if (logText.isBlank()) listOf("No diagnostic events yet. Use the app — crashes and errors will appear here.")
        else logText.lines()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Problem log", color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Recent crashes and errors on this device. Copy or share this when something breaks.",
                color = AppTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = {
                    copyLog(context, logText)
                }) {
                    Text("Copy", color = AppAccent)
                }
                TextButton(onClick = {
                    shareLog(context, logText)
                }) {
                    Text("Share", color = AppAccent)
                }
                TextButton(onClick = {
                    VantaDiagnosticLog.clear()
                    Toast.makeText(context, "Log cleared", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) {
                    Text("Clear", color = AppTextMuted)
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .background(AppBackground, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        color = when {
                            line.contains(" CRASH ") -> AppError
                            line.contains(" ERROR ") -> AppWarning
                            line.contains(" WARN ") -> AppAccentSecondary
                            else -> AppTextSecondary
                        },
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun copyLog(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("VANTA diagnostics", text))
    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
}

private fun shareLog(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "VANTA problem log")
        putExtra(Intent.EXTRA_TEXT, text.ifBlank { "No log entries." })
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share VANTA log").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
