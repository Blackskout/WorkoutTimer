package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class ActionSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var edits = 0
    private var history = 0

    private fun show() = composeRule.setContent {
        WorkoutTimerTheme {
            Column {
                ActionSheetContent(
                    title = "Ноги",
                    subtitle = null,
                    items = listOf(
                        ActionSheetItem(
                            text = "Редактировать",
                            icon = Icons.Default.Edit,
                            onClick = { edits++ },
                            subtitle = "Недоступно во время тренировки",
                            enabled = false
                        ),
                        ActionSheetItem(text = "История", icon = Icons.Default.History, onClick = { history++ })
                    )
                )
            }
        }
    }

    @Test
    fun неактивный_пункт_подписан_и_не_нажимается() {
        show()

        composeRule.onNodeWithText("Редактировать").assertIsNotEnabled()
        composeRule.onNodeWithText("Недоступно во время тренировки", substring = false, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Редактировать").performClick()

        composeRule.runOnIdle { assertEquals(0, edits) }
    }

    @Test
    fun активный_пункт_нажимается() {
        show()

        composeRule.onNodeWithText("История").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(1, history) }
    }
}
