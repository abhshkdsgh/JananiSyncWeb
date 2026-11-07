package `in`.drarunbabu.drsharmila.jananisync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.launch

private const val seniorityExplanation =
    "Access level represents seniority within your organisation. Higher numbers indicate greater seniority."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessLevelInfo() {
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(seniorityExplanation) } },
        state = state
    ) {
        IconButton(onClick = { scope.launch { state.show() } }) {
            Icon(Icons.Outlined.Info, contentDescription = "About access level: seniority")
        }
    }
}

@Composable
fun AccessLevelText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f, fill = false), style = style)
        AccessLevelInfo()
    }
}
