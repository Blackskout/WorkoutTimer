//package ru.hopes.workouttimer.presentation.screen.creation
//
//import android.net.Uri
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.flow.update
//import kotlinx.coroutines.launch
//import ru.hopes.workouttimer.domain.model.Workout
//import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
//import javax.inject.Inject
//
//@HiltViewModel()
//class CreateWorkoutViewModel @Inject constructor(
//    private val addWorkoutUseCase: AddWorkoutUseCase
//) : ViewModel() {
//
//
//    private val _state =
//        MutableStateFlow<CreateWorkoutState>(CreateWorkoutState.Creation())
//    val state = _state.asStateFlow()
//
//    fun processCommand(command: CreateWorkoutCommand) {
//        when (command) {
//            CreateWorkoutCommand.Back -> {
//                _state.update { CreateWorkoutState.Finished }
//            }
//
//            is CreateWorkoutCommand.InputContent -> {
//                _state.update { prevState ->
//                    if (prevState is CreateWorkoutState.Creation) {
//                        val newContent = prevState.content
//                            .mapIndexed { index, contentItem ->
//                                if (index == command.index && contentItem is ContentItem.Text) {
//                                    contentItem.copy(content = command.content)
//                                } else {
//                                    contentItem
//                                }
//                            }
//                        prevState.copy(
//                            content = newContent
//                        )
//                    } else {
//                        prevState
//                    }
//                }
//            }
//
//            is CreateWorkoutCommand.InputTitle -> {
//                _state.update { prevState ->
//                    if (prevState is CreateWorkoutState.Creation) {
//                        prevState.copy(
//                            title = command.title
//                        )
//                    } else {
//                        prevState
//                    }
//
//                }
//            }
//
//            CreateWorkoutCommand.Save -> {
//                viewModelScope.launch {
//                    _state.update { prevState ->
//                        if (prevState is CreateWorkoutState.Creation) {
//                            val title = prevState.title
//                            val content = prevState.content.filter {
//                                it !is ContentItem.Text || it.content.isNotBlank()
//                            }
//                            val workout = Workout()
//                            addWorkoutUseCase(title, content)
//                            CreateWorkoutState.Finished
//                        } else {
//                            prevState
//                        }
//                    }
//                }
//            }
//
//            is CreateWorkoutCommand.AddImage -> {
//
//                _state.update { prevState ->
//                    if (prevState is CreateWorkoutState.Creation) {
//                        val newItems = prevState.content.toMutableList()
//                        val lastItem = newItems.last()
//                        if (lastItem is ContentItem.Text && lastItem.content.isBlank()) {
//                            newItems.removeAt(newItems.lastIndex)
//                        }
//                        newItems.add(Image(command.uri.toString()))
//                        newItems.add(Text(""))
//                        prevState.copy(content = newItems)
//                    } else {
//                        prevState
//                    }
//                }
//            }
//
//            is CreateWorkoutCommand.DeleteImage -> {
//                _state.update { prevState ->
//                    if (prevState is CreateWorkoutState.Creation) {
//                        prevState.content.toMutableList().apply {
//                            removeAt(command.index)
//                        }.let {
//                            prevState.copy(content = it)
//                        }
//                    } else {
//                        prevState
//                    }
//                }
//            }
//        }
//    }
//}
//
//
//sealed interface CreateWorkoutCommand {
//
//    data class InputTitle(val title: String) : CreateWorkoutCommand
//
//    data class InputContent(val content: String, val index: Int) : CreateWorkoutCommand
//
//    data class AddImage(val uri: Uri) : CreateWorkoutCommand
//
//    data class DeleteImage(val index: Int) : CreateWorkoutCommand
//
//    data object Save : CreateWorkoutCommand
//
//    data object Back : CreateWorkoutCommand
//}
//
//sealed interface CreateWorkoutState {
//
//    data class Creation(
//        val title: String = "",
//        val content: List<ContentItem> = listOf(ContentItem.Text(""))
//    ) : CreateWorkoutState {
//        val isSaveEnabled: Boolean
//            get() {
//                return when {
//                    title.isBlank() -> false
//                    content.isEmpty() -> false
//                    else -> {
//                        content.any {
//                            it !is ContentItem.Text || it.content.isNotBlank()
//                        }
//                    }
//                }
//            }
//    }
//
//    data object Finished : CreateWorkoutState
//}