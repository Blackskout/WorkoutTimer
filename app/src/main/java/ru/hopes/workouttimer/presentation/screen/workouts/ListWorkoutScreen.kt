package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: ListWorkoutViewModel = hiltViewModel(),
    onAddWorkoutClick: () -> Unit,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onLongClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onExportImportClick: () -> Unit = {}
) {

    val state by viewModel.state.collectAsState()
    var workoutToDelete by rememberSaveable { mutableStateOf<WorkoutEntity?>(null) }
    var showContextMenu by rememberSaveable { mutableStateOf<WorkoutEntity?>(null) }

    if (workoutToDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text("Удалить тренировку?") },
            text = { Text("Вы уверены, что хотите удалить \"${workoutToDelete?.name}\"? Это действие нельзя отменить.") },
            confirmButton = {
                Button(
                    onClick = {
                        workoutToDelete?.let { viewModel.deleteWorkout(it) }
                        workoutToDelete = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                Button(
                    onClick = { workoutToDelete = null }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.all_workouts),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onExportImportClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Экспорт/Импорт"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddWorkoutClick,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(
                    modifier = Modifier.size(50.dp),
                    painter = painterResource(R.drawable.outline_add_circle_24),
                    contentDescription = null
                )
            }
        }
    ) { innerPadding ->

        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                SearchBar(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    query = state.query,
                    onQueryChange = {
                        viewModel.updateSearchQuery(it)
                    })
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }


            itemsIndexed(
                items = state.workouts, key = { _, workout -> workout.id })
            { index, workout ->
                WorkoutCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    workout = workout,
                    lastSessionDurationMillis = state.lastSessionDurations[workout.id],
                    onWorkoutClick = onWorkoutClick,
                    onLongClick = onLongClick,
                    onEditClick = onEditClick,
                    onDeleteClick = {
                        workoutToDelete = it
                    },
                    backgroundColor = MaterialTheme.colorScheme.surface
                )

            }
        }
    }
}


@Composable
private fun Title(
    modifier: Modifier = Modifier,
    text: String,
) {
    Text(
        modifier = modifier,
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun SearchBar(
    modifier: Modifier = Modifier, query: String, onQueryChange: (String) -> Unit
) {
    TextField(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(10)
            ),
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Search...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search workouts",
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
fun WorkoutCard(
    modifier: Modifier = Modifier,
    workout: WorkoutEntity,
    backgroundColor: Color,
    lastSessionDurationMillis: Long? = null,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onLongClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onDeleteClick: (WorkoutEntity) -> Unit = {}
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .combinedClickable(
                    onClick = {
                        onWorkoutClick(workout)
                    },
                    onLongClick = {
                        showMenu = true
                    }
                )
                .padding(16.dp)
        ) {
            Text(
                modifier = Modifier.weight(3.5f),
                text = workout.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = DateFormatter.formatDateToString(workout.lastUseAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastSessionDurationMillis != null) {
                    Text(
                        text = DateFormatter.formatDurationToString(lastSessionDurationMillis),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Редактировать") },
                onClick = {
                    showMenu = false
                    onEditClick(workout)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = {
                    showMenu = false
                    onDeleteClick(workout)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Preview
@Composable
fun WorkoutScreenPreview() {
    WorkoutTimerTheme {
        ListWorkoutScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            onAddWorkoutClick = {},
            onLongClick = { },
            viewModel = hiltViewModel(),
            onWorkoutClick = {}
        )
    }
}

