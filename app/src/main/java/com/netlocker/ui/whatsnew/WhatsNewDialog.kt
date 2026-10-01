package com.netlocker.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Renders whatever [viewModel]'s state currently is — nothing while [WhatsNewUiState.Hidden],
 *  so this can be composed unconditionally once per screen that offers a "What's new" entry
 *  point (the app shell for the automatic popup, Settings for the on-demand one). */
@Composable
fun WhatsNewDialog(viewModel: WhatsNewViewModel) {
    val state by viewModel.state.collectAsState()
    when (val s = state) {
        WhatsNewUiState.Hidden -> Unit

        WhatsNewUiState.Loading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
        )

        WhatsNewUiState.Unavailable -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Couldn't load") },
            text = { Text("Couldn't fetch the latest release notes right now. Please try again later.") },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("Close") } },
        )

        is WhatsNewUiState.Shown -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("What's new in v${s.versionName}") },
            text = {
                Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    FormattedReleaseNotes(s.notes)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("Got it") } },
        )
    }
}

/** Release notes are written as plain GitHub-flavoured markdown (## headings, - bullets,
 *  **bold** spans) — rather than pull in a markdown renderer for this one screen, a few
 *  lines of manual formatting cover the only constructs NetLocker's own release notes
 *  ever use. */
@Composable
private fun FormattedReleaseNotes(notes: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        notes.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("## ") -> Text(
                    parseInlineBold(line.removePrefix("## ")),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                line.startsWith("- ") -> {
                    val bullet = buildAnnotatedString {
                        append("•  ")
                        append(parseInlineBold(line.removePrefix("- ")))
                    }
                    Text(bullet, fontSize = 13.sp)
                }
                line.isEmpty() -> Box(modifier = Modifier.height(4.dp))
                else -> Text(parseInlineBold(line), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Splits on `**bold**` spans (GitHub markdown's only inline emphasis NetLocker's release
 *  notes use) and renders them with [FontWeight.Bold]; everything else passes through as
 *  plain text. */
private fun parseInlineBold(text: String): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val start = rest.indexOf("**")
        val end = if (start >= 0) rest.indexOf("**", start + 2) else -1
        if (start < 0 || end < 0) {
            append(rest)
            return@buildAnnotatedString
        }
        append(rest.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(rest.substring(start + 2, end))
        }
        rest = rest.substring(end + 2)
    }
}
