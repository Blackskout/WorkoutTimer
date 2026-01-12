//package ru.hopes.workouttimer.presentation.screen.creation
//
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ArrowBack
//import androidx.compose.material.icons.filled.AddPhotoAlternate
//import androidx.compose.material3.Button
//import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.Icon
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Text
//import androidx.compose.material3.TextField
//import androidx.compose.material3.TextFieldDefaults
//import androidx.compose.material3.TopAppBar
//import androidx.compose.material3.TopAppBarDefaults
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.TextStyle
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
//import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
//import ru.hopes.workouttimer.presentation.utils.DateFormatter
//
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable // side effect// Composable functions can run frequently when recomposition happens — so they must be free of side effects unless you use the right APIs.
//fun CreateWorkoutScreen(
//    modifier: Modifier = Modifier,
//    viewModel: CreateWorkoutViewModel = hiltViewModel(),
//    onFinished: () -> Unit,
//) {
////TODO by or .value ...// upd: here we use ".value" because "by" is dynamic and constantly changing and we need current state
//    val state = viewModel.state.collectAsState()
//    val currentState = state.value
//
//    val imagePicker = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.GetContent(),
//        onResult = { uri ->
//            uri?.let {
//                viewModel.processCommand(CreateWorkoutCommand.AddImage(it))
//            }
//        }
//    )
//
//    when (currentState) {
//        is CreateWorkoutState.Creation -> {
//            Scaffold(
//                modifier = modifier,
//                topBar = {
//                    TopAppBar(
//                        title = {
//                            Text(
//                                text = "Create workout",
//                                fontSize = 20.sp,
//                                fontWeight = FontWeight.Bold,
//                                color = MaterialTheme.colorScheme.onBackground
//                            )
//                        },
//                        colors = TopAppBarDefaults.topAppBarColors(
//                            containerColor = Color.Transparent,
//                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
//                        ),
//                        navigationIcon = {
//                            Icon(
//                                modifier = Modifier
//                                    .padding(start = 16.dp, end = 8.dp)
//                                    .clickable {
//                                        viewModel.processCommand(CreateWorkoutCommand.Back)
//                                    },
//                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
//                                contentDescription = "Back"
//                            )
//                        },
//                        actions = {
//                            Icon(
//                                modifier = Modifier
//                                    .clickable {
//                                        imagePicker.launch("image/*")
//                                    }
//                                    .padding(end = 24.dp),
//                                imageVector = Icons.Default.AddPhotoAlternate,
//                                contentDescription = "add photo from gallery",
//                                tint = MaterialTheme.colorScheme.onSurface,
//                            )
//                        }
//                    )
//                }
//            ) { innerPadding ->
//                Column(
//                    modifier = Modifier.padding(innerPadding)
//                ) {
//
//                    TextField(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(horizontal = 8.dp),
//                        value = currentState.title,
//                        onValueChange = { changedTitle ->
//                            viewModel
//                                .processCommand(CreateWorkoutCommand.InputTitle(changedTitle))
//                        },
//                        colors = TextFieldDefaults.colors(
//                            focusedContainerColor = Color.Transparent,
//                            unfocusedContainerColor = Color.Transparent,
//                            unfocusedIndicatorColor = Color.Transparent,
//                            focusedIndicatorColor = Color.Transparent
//                        ),
//                        textStyle = TextStyle(
//                            fontSize = 24.sp,
//                            fontWeight = FontWeight.Bold,
//                            color = MaterialTheme.colorScheme.onBackground
//                        ),
//                        placeholder = {
//                            Text(
//                                text = "Title",
//                                fontSize = 24.sp,
//                                fontWeight = FontWeight.Bold,
//                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
//                            )
//                        }
//                    )
//                    Text(
//                        modifier = Modifier.padding(horizontal = 24.dp),
//                        text = DateFormatter.formatCurrentDate(),
//                        fontSize = 12.sp,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//
//                    Content(
//                        modifier = Modifier
//                            .weight(1f),
//                        content = currentState.content,
//                        onDeleteImageClick = {
//                            viewModel.processCommand(
//                                CreateWorkoutCommand.DeleteImage(it)
//                            )
//                        },
//                        onTextChanged = { index, text ->
//                            viewModel.processCommand(
//                                CreateWorkoutCommand.InputContent(
//                                    content = text,
//                                    index = index
//                                )
//                            )
//                        }
//                    )
//
//                    Button(
//                        modifier = Modifier
//                            .padding(horizontal = 8.dp)
//                            .fillMaxWidth(),
//                        onClick = {
//                            viewModel.processCommand(CreateWorkoutCommand.Save)
//                        },
//                        shape = RoundedCornerShape(10.dp),
//                        enabled = currentState.isSaveEnabled,
//                        colors = ButtonDefaults.buttonColors(
//                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(
//                                alpha = 0.1f
//                            ),
//                            containerColor = MaterialTheme.colorScheme.primary,
//                            contentColor = MaterialTheme.colorScheme.onPrimary,
//                            disabledContentColor = MaterialTheme.colorScheme.onPrimary
//                        )
//                    ) {
//                        Text(
//                            text = "Save workout",
//                        )
//                    }
//                }
//            }
//        }
//
//        CreateWorkoutState.Finished -> {
//            LaunchedEffect(key1 = Unit) {
//                onFinished()
//            }
//        }
//    }
//
//}
//
//
//@Preview
//@Composable
//fun CreateWorkoutScreenPreview() {
//    WorkoutTimerTheme {
//        CreateWorkoutScreen(onFinished = {})
//    }
//}
//
