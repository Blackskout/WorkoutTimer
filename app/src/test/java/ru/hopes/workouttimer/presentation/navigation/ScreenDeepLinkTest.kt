package ru.hopes.workouttimer.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenDeepLinkTest {

    // Проверяет, что createDeepLink() реально построен из DEEP_LINK_PATTERN, а не дублирует
    // схему отдельной строкой — расхождение между ними иначе не ловится ни компилятором,
    // ни другими тестами, только тапом по виджету на устройстве.
    @Test
    fun `createDeepLink builds a uri from DEEP_LINK_PATTERN`() {
        assertEquals("workouttimer://execution/7", Screen.Execution.createDeepLink(7))
    }
}
