package ru.hopes.workouttimer.presentation.screen.exportImport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.repository.ImportError
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@Composable
fun ExportImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // Лаунчер для выбора файла импорта
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importWorkouts(it)
        }
    }

    // Обработка состояний
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ExportImportUiState.ExportSuccess -> {
                snackbarHostState.showSnackbar(resources.getString(R.string.export_done))
                viewModel.resetState()
            }
            is ExportImportUiState.ImportSuccess -> {
                val result = state.result
                if (result.success) {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.export_imported_count, result.importedCount)
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        when (val error = result.error) {
                            ImportError.ReadFailed -> resources.getString(R.string.export_error_read)
                            ImportError.NoWorkouts -> resources.getString(R.string.export_error_empty)
                            is ImportError.Failed -> error.detail
                                ?.let { resources.getString(R.string.export_error_import_detail, it) }
                                ?: resources.getString(R.string.export_error_import)
                            null -> resources.getString(R.string.export_error_import)
                        }
                    )
                }
                viewModel.resetState()
            }
            is ExportImportUiState.Error -> {
                snackbarHostState.showSnackbar(state.detail ?: resources.getString(state.fallback))
                viewModel.resetState()
            }
            else -> {}
        }
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
                    text = stringResource(R.string.list_export_import),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            ExportImportContent(
                busy = uiState is ExportImportUiState.Loading,
                onExportClick = { viewModel.exportWorkouts() },
                onImportClick = { importLauncher.launch(arrayOf("application/json")) }
            )
        }
    }
}

/**
 * Тело экрана под шапкой: карточки действий и индикатор занятости.
 * Вынесено из [ExportImportScreen], чтобы превьюшить без `hiltViewModel()`.
 */
@Composable
private fun ExportImportContent(
    busy: Boolean,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(CardSpacing)
    ) {
        ActionCard(
            icon = Icons.Default.Upload,
            title = stringResource(R.string.export_action_title),
            description = stringResource(R.string.export_action_description),
            enabled = !busy,
            onClick = onExportClick
        )
        ActionCard(
            icon = Icons.Default.Download,
            title = stringResource(R.string.import_action_title),
            description = stringResource(R.string.import_action_description),
            enabled = !busy,
            onClick = onImportClick
        )
        if (busy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.export_busy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ExportImportContentIdlePreview() {
    WorkoutTimerTheme {
        ExportImportContent(busy = false, onExportClick = {}, onImportClick = {})
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ExportImportContentBusyPreview() {
    WorkoutTimerTheme {
        ExportImportContent(busy = true, onExportClick = {}, onImportClick = {})
    }
}
