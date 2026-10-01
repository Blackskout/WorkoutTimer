package ru.hopes.workouttimer.presentation.screen.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.ui.components.ActionSheet
import ru.hopes.workouttimer.presentation.ui.components.ActionSheetItem
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.SectionSpacing
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.SetFormat
import ru.hopes.workouttimer.presentation.utils.daysAgoText
import ru.hopes.workouttimer.presentation.utils.formatSet
import ru.hopes.workouttimer.presentation.utils.setFormat
import ru.hopes.workouttimer.presentation.utils.unitName

@Composable
fun ExerciseCatalogScreen(
    viewModel: ExerciseCatalogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenProgress: (CatalogExercise) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var menuFor by remember { mutableStateOf<CatalogExercise?>(null) }
    var unitFor by remember { mutableStateOf<CatalogExercise?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // Сбой записи или чтения — снекбар, как в списке тренировок.
    LaunchedEffect(state.errorMessage) {
        val messageRes = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(messageRes))
        viewModel.dismissError()
    }

    menuFor?.let { target ->
        ActionSheet(
            title = target.name,
            subtitle = null,
            items = listOf(
                ActionSheetItem(stringResource(R.string.catalog_action_rename), Icons.Default.Edit, {
                    menuFor = null
                    viewModel.startRename(target)
                }),
                ActionSheetItem(stringResource(R.string.catalog_action_unit), Icons.Default.Straighten, {
                    menuFor = null
                    unitFor = target
                }, subtitle = unitName(target.unit))
            ),
            onDismiss = { menuFor = null }
        )
    }

    unitFor?.let { target ->
        UnitDialog(
            current = target.unit,
            onSelect = { unit ->
                unitFor = null
                viewModel.changeUnit(target, unit)
            },
            onDismiss = { unitFor = null }
        )
    }

    state.renameTarget?.let { target ->
        RenameDialog(
            initialName = target.name,
            nameTaken = state.renameTaken,
            onNameEdited = viewModel::clearRenameTaken,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::cancelRename
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.catalog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                placeholder = { Text(stringResource(R.string.catalog_search_placeholder)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            CatalogContent(state = state, onOpen = onOpenProgress, onMenu = { menuFor = it })
        }
    }
}

/**
 * Список справочника под поиском. Вынесен из [ExerciseCatalogScreen], чтобы
 * превьюшить и тестировать без `hiltViewModel()`.
 */
@Composable
internal fun CatalogContent(
    state: ExerciseCatalogState,
    onOpen: (CatalogExercise) -> Unit,
    onMenu: (CatalogExercise) -> Unit
) {
    // До первого ответа базы пустое состояние не показываем — оно мигнуло бы.
    if (!state.isLoaded) return
    if (state.rows.isEmpty()) {
        EmptyState(
            icon = Icons.Default.FitnessCenter,
            title = stringResource(
                if (state.isCatalogEmpty) R.string.catalog_empty_title else R.string.catalog_search_empty_title
            ),
            subtitle = stringResource(
                if (state.isCatalogEmpty) R.string.catalog_empty_subtitle else R.string.catalog_search_empty_subtitle
            )
        )
        return
    }
    val format = setFormat()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenPadding,
            end = ScreenPadding,
            top = SectionSpacing,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(CardSpacing)
    ) {
        items(state.rows, key = { it.exercise.id }) { summary ->
            CatalogRow(
                summary = summary,
                format = format,
                onOpen = { onOpen(summary.exercise) },
                onMenu = { onMenu(summary.exercise) }
            )
        }
    }
}

/** Строка справочника: тап открывает прогресс упражнения, ⋮ — меню переименования и единицы. */
@Composable
private fun CatalogRow(
    summary: CatalogSummary,
    format: SetFormat,
    onOpen: () -> Unit,
    onMenu: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.catalog_open_progress),
                onClick = onOpen
            )
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = summary.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = lastSetLine(summary, format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMenu) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.catalog_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** «60 кг × 8 · 3 дн. назад» либо «ещё не делали». */
@Composable
private fun lastSetLine(summary: CatalogSummary, format: SetFormat): String {
    val best = summary.lastBest
    val doneAt = summary.lastDoneAt
    if (best == null || doneAt == null) return stringResource(R.string.common_never_done)
    // Без веса голое «8» неясно, что это: подписываем повторения (история держит формат спеки).
    val setText = if (best.unit == ExerciseUnit.BODYWEIGHT) {
        stringResource(R.string.catalog_last_reps, best.reps)
    } else {
        formatSet(best, format)
    }
    return stringResource(R.string.catalog_last_set, setText, daysAgoText(doneAt))
}

/**
 * Переименование. Ошибка «уже есть» живёт в поле, а не в снекбаре: диалог
 * остаётся открытым, чтобы название можно было сразу поправить.
 */
@Composable
internal fun RenameDialog(
    initialName: String,
    nameTaken: Boolean,
    onNameEdited: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // rememberSaveable: набранное название переживает поворот экрана.
    var text by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onNameEdited()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = nameTaken,
                supportingText = if (nameTaken) {
                    { Text(stringResource(R.string.catalog_rename_taken)) }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.small
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** Выбор единицы: применяется сразу по тапу, подсказка объясняет, что станет с весом. */
@Composable
internal fun UnitDialog(
    current: ExerciseUnit,
    onSelect: (ExerciseUnit) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_unit_title)) },
        text = {
            Column {
                ExerciseUnit.entries.forEach { unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(
                                selected = unit == current,
                                onClick = { onSelect(unit) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = unit == current, onClick = null)
                        Text(
                            text = unitName(unit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.catalog_unit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun CatalogContentPreview() {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    WorkoutTimerTheme {
        CatalogContent(
            state = ExerciseCatalogState(
                isLoaded = true,
                rows = listOf(
                    CatalogSummary(
                        CatalogExercise(1, "Подтягивания", ExerciseUnit.BODYWEIGHT),
                        SessionSet(1, 1, 1, 0.0, 0.0, 12, ExerciseUnit.BODYWEIGHT),
                        now - day
                    ),
                    CatalogSummary(
                        CatalogExercise(2, "Присед", ExerciseUnit.KG),
                        SessionSet(2, 1, 2, 60.0, 0.0, 8, ExerciseUnit.KG),
                        now - 3 * day
                    ),
                    CatalogSummary(
                        CatalogExercise(3, "Тяга блока", ExerciseUnit.PLATE),
                        SessionSet(3, 1, 3, 5.0, 2.0, 12, ExerciseUnit.PLATE),
                        now
                    ),
                    CatalogSummary(CatalogExercise(4, "Фронтальный присед", ExerciseUnit.KG), null, null)
                )
            ),
            onOpen = {},
            onMenu = {}
        )
    }
}
