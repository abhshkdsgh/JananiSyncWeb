package `in`.drarunbabu.drsharmila.jananisync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class AppWindowSize { Compact, Medium, Expanded }
val LocalAppWindowSize = staticCompositionLocalOf { AppWindowSize.Expanded }

@Composable
fun AdaptiveLayout(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = when {
            maxWidth < 600.dp -> AppWindowSize.Compact
            maxWidth < 840.dp -> AppWindowSize.Medium
            else -> AppWindowSize.Expanded
        }
        CompositionLocalProvider(LocalAppWindowSize provides size, content = content)
    }
}

/** Fields retain their weights on wide windows and occupy separate lines on phones. */
@Composable
fun AdaptiveFields(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (LocalAppWindowSize.current == AppWindowSize.Compact) 1 else 2,
        content = content)
}

@Composable
fun WrappingActions(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
