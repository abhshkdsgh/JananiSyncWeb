package `in`.drarunbabu.drsharmila.jananisync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.drarunbabu.drsharmila.jananisync.data.privacy.PrivacyPolicyDocument

/** Shows the complete versioned notice; only explicit buttons submit decisions. */
@Composable
fun PrivacyPolicyDialog(
    requireDecision: Boolean,
    isSaving: Boolean,
    error: String?,
    onAccept: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit,
    onExit: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onCancelReview: (() -> Unit)? = null
) {
    val blocks = remember { policyBlocks(PrivacyPolicyDocument.CONTENT) }
    val scrollState = rememberScrollState()
    Dialog(
        onDismissRequest = { if (!requireDecision && !isSaving) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !requireDecision && !isSaving,
            dismissOnClickOutside = !requireDecision && !isSaving,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.padding(12.dp).widthIn(max = 880.dp)
                .fillMaxWidth().fillMaxHeight(0.94f).testTag("privacy-policy-dialog"),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Privacy policy", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Version ${PrivacyPolicyDocument.VERSION} · Effective ${PrivacyPolicyDocument.EFFECTIVE_DATE}",
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                SelectionContainer(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(scrollState).testTag("privacy-policy-content"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (requireDecision) {
                            Text(
                                "Your decision is recorded with your account and this policy version. Denial keeps access blocked.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            HorizontalDivider()
                        }
                        blocks.forEach { block ->
                            when (block) {
                                is PolicyBlock.Heading -> Text(
                                    policyAnnotatedText(block.text),
                                    style = if (block.level == 1) MaterialTheme.typography.titleLarge
                                        else MaterialTheme.typography.titleMedium
                                )
                                is PolicyBlock.Paragraph -> Text(
                                    policyAnnotatedText(block.text),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                is PolicyBlock.Table -> block.rows.forEach { cells ->
                                    OutlinedCard(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(policyAnnotatedText(cells.firstOrNull().orEmpty()),
                                                style = MaterialTheme.typography.titleSmall)
                                            cells.drop(1).forEachIndexed { index, cell ->
                                                Text(policyAnnotatedText("**${block.headers.getOrNull(index + 1).orEmpty()}:** $cell"),
                                                    style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("privacy-policy-error"))
                }
                if (isSaving) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Saving your decision…", style = MaterialTheme.typography.bodySmall)
                }
                WrappingActions {
                    if (requireDecision) {
                        OutlinedButton(onClick = onAccept, enabled = !isSaving,
                            modifier = Modifier.testTag("privacy-policy-accept")) {
                            Text(PrivacyPolicyDocument.ACCEPT_BUTTON)
                        }
                        OutlinedButton(onClick = onDeny, enabled = !isSaving,
                            modifier = Modifier.testTag("privacy-policy-deny")) {
                            Text(PrivacyPolicyDocument.DENY_BUTTON)
                        }
                    } else {
                        TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Close") }
                    }
                    onCancelReview?.let { cancel ->
                        TextButton(onClick = cancel, enabled = !isSaving) { Text("Cancel") }
                    }
                    onHistory?.let { history ->
                        TextButton(onClick = history, enabled = !isSaving) { Text("Decision history") }
                    }
                    onExit?.let { exit ->
                        TextButton(onClick = exit, enabled = !isSaving) { Text("Sign out") }
                    }
                }
            }
        }
    }
}

private sealed interface PolicyBlock {
    data class Heading(val level: Int, val text: String) : PolicyBlock
    data class Paragraph(val text: String) : PolicyBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : PolicyBlock
}

/** Tables become stacked labelled cards rather than requiring horizontal phone scrolling. */
private fun policyBlocks(markdown: String): List<PolicyBlock> = buildList {
    val lines = markdown.lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        when {
            line.isEmpty() -> index++
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                add(PolicyBlock.Heading(level, line.drop(level).trim()))
                index++
            }
            line.startsWith("|") -> {
                val table = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].trim().startsWith("|")) {
                    val cells = lines[index].trim().trim('|').split('|').map { it.trim() }
                    if (!cells.all { it.matches(Regex(":?-+:?")) }) table += cells
                    index++
                }
                if (table.isNotEmpty()) add(PolicyBlock.Table(table.first(), table.drop(1)))
            }
            else -> {
                val paragraph = mutableListOf<String>()
                while (index < lines.size && lines[index].isNotBlank() &&
                    !lines[index].startsWith("#") && !lines[index].startsWith("|")) {
                    paragraph += lines[index].trimEnd()
                    index++
                }
                add(PolicyBlock.Paragraph(paragraph.joinToString("\n")))
            }
        }
    }
}

/** Keeps the document's links usable and its limited inline Markdown readable. */
private fun policyAnnotatedText(text: String): AnnotatedString = buildAnnotatedString {
    val markup = Regex("""\[([\s\S]+?)\x5D\(([^)]+)\)|\*\*([^*]+)\*\*|`([^`]+)`""")
    var start = 0
    markup.findAll(text).forEach { match ->
        append(text.substring(start, match.range.first))
        when {
            match.groups[1] != null -> withLink(LinkAnnotation.Url(match.groupValues[2])) {
                append(match.groupValues[1])
            }
            match.groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[3])
            }
            else -> append(match.groupValues[4])
        }
        start = match.range.last + 1
    }
    append(text.substring(start))
}
