package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            content = content
        )
    }
}

data class ActionSheetItem(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val subtitle: String? = null,
    val destructive: Boolean = false
)

@Composable
fun ActionSheet(
    title: String,
    subtitle: String?,
    items: List<ActionSheetItem>,
    onDismiss: () -> Unit
) {
    AppBottomSheet(onDismiss = onDismiss) {
        ActionSheetContent(title = title, subtitle = subtitle, items = items)
    }
}

// Извлечено из ActionSheet, чтобы тело листа можно было превьюшить без
// ModalBottomSheet — он требует Window и не рендерится в @Preview.
@Composable
private fun ColumnScope.ActionSheetContent(
    title: String,
    subtitle: String?,
    items: List<ActionSheetItem>
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding)
    )
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 2.dp
            )
        )
    }
    Column(modifier = Modifier.padding(top = 10.dp)) {
        items.forEach { item ->
            val tint = if (item.destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { item.onClick() }
                    .padding(horizontal = ScreenPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ActionSheetPreview() {
    WorkoutTimerTheme {
        Column {
            ActionSheetContent(
                title = "Упражнение",
                subtitle = "Жим лёжа",
                items = listOf(
                    ActionSheetItem(text = "Изменить", icon = Icons.Default.Edit, onClick = {}),
                    ActionSheetItem(text = "Поделиться", icon = Icons.Default.Share, onClick = {}),
                    ActionSheetItem(text = "История", icon = Icons.Default.History, onClick = {}),
                    ActionSheetItem(text = "Напоминание", icon = Icons.Default.Notifications, onClick = {}),
                    ActionSheetItem(
                        text = "Удалить",
                        icon = Icons.Default.Delete,
                        onClick = {},
                        destructive = true
                    )
                )
            )
        }
    }
}
