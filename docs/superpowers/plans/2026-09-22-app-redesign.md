# Редизайн приложения — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Перевести приложение на визуальный язык «Dark Athletic» и перестроить экраны вокруг очереди ротации: главный экран показывает, какую тренировку делать следующей.

**Architecture:** Сначала строится дизайн-система (`presentation/ui/`) — токены и библиотека компонентов с превью. Затем меняется data-слой: сортировка по `lastUseAt ASC`, «Пропустить тренировку», единый тип состояния списка. Затем каждый экран переписывается на готовых компонентах и готовых данных. ViewModel экрана выполнения не трогается вообще.

**Tech Stack:** Kotlin 2.2.21, Jetpack Compose (BOM 2026.03.00 → material3 1.4.0, foundation 1.10.5), Glance 1.2.0, Room 2.8.4, Hilt 2.57.2, Navigation Compose 2.9.7, `sh.calvin.reorderable` 3.1.0, JUnit4 + mockk + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-22-app-redesign-design.md`

## Global Constraints

- `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36`, JVM target 17.
- Зависимости объявляются только через version catalog `gradle/libs.versions.toml`, никаких строковых координат в `app/build.gradle.kts`.
- Тесты — JVM unit-тесты в `app/src/test`. Robolectric и инструментальные тесты не добавляем.
- `unitTests.isReturnDefaultValues = true` — методы `android.jar` в тестах возвращают дефолты, поэтому тестируемый код не должен требовать `Context`/`Resources`.
- Комментарии и пользовательские строки — на русском, как в остальном проекте. Новый текст экранов — литералами в коде; в `strings.xml` добавляется только то, что нужно виджету.
- Собирать: `./gradlew :app:assembleDebug`. Тесты: `./gradlew :app:testDebugUnitTest`.
- Приложение всегда тёмное. `dynamicColor` и светлая схема не возвращаются ни на одном экране.
- Ни один экран не задаёт цвет литералом — только через `MaterialTheme.colorScheme` или токены из `presentation/ui/theme/Color.kt`.
- Горизонтальный отступ экрана — 18dp. Шаг между карточками — 8dp, между секциями — 18dp.
- Акцент `#C6FF3D`, текст на акценте `#0B0B0F`, фон `#0B0B0F`, поверхность `#16161C`, обводка `#26262E`.

---

## Структура файлов

| Файл | Ответственность |
|---|---|
| `presentation/ui/theme/Color.kt` | (переписывается) токены Dark Athletic |
| `presentation/ui/theme/Theme.kt` | (переписывается) одна `darkColorScheme`, без dynamic color |
| `presentation/ui/theme/Type.kt` | (переписывается) типографическая шкала с табличными цифрами |
| `presentation/ui/theme/Shape.kt` | (создаётся) `Shapes` и константы отступов |
| `presentation/ui/components/Buttons.kt` | `PrimaryButton` — главная кнопка действия |
| `presentation/ui/components/Labels.kt` | `EyebrowLabel`, `SectionHeader` |
| `presentation/ui/components/StatTile.kt` | Плитка «число + единица» |
| `presentation/ui/components/EmptyState.kt` | Пустые состояния и ошибки |
| `presentation/ui/components/WheelPicker.kt` | `WheelPicker`, `WheelRow` — барабаны со снапом |
| `presentation/ui/components/Sheets.kt` | `AppBottomSheet`, `ActionSheet`, `ActionSheetItem` |
| `presentation/ui/components/RestRing.kt` | Кольцо таймера отдыха |
| `presentation/ui/components/ProgressSegments.kt` | Сегменты прогресса по упражнениям |
| `presentation/utils/DateFormatter.kt` | (изменяется) `formatDurationCompact`, минус `isStaleWorkout` |
| `data/dao/WorkoutDao.kt` | (изменяется) сортировка очереди, тип поиска |
| `domain/repository/WorkoutRepository.kt` | (изменяется) `setLastUseAt`, `getLastUseAt`, тип поиска |
| `domain/usecase/SkipWorkoutUseCase.kt` | (создаётся) пропуск тренировки |
| `domain/usecase/UndoSkipWorkoutUseCase.kt` | (создаётся) отмена пропуска |
| `presentation/screen/workouts/ListWorkoutScreen.kt` | (переписывается) очередь, герой-карта, `ActionSheet` |
| `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt` | (переписывается) чип, плитки, кольцо, экран «Готово» |
| `presentation/screen/creation/CreateWorkoutScreen.kt` | (переписывается) строки-сводки, перетаскивание, лист-редактор |
| `presentation/components/SystemMediaController.kt` | (сокращается) только кнопка Яндекс Музыки |
| `presentation/widget/WidgetColors.kt` | (создаётся) `ColorProviders` для Glance |

Порядок задач: сначала риск-гейт на библиотеку перетаскивания, затем дизайн-система, затем data-слой с тестами, затем экраны, в конце — виджет и чистка.

---

## Task 1: Риск-гейт, `.gitignore`, снятие `androidx.media`

Библиотека перетаскивания — единственная непроверяемая на бумаге зависимость: она тянет KMP-артефакты `org.jetbrains.compose.*` 1.7.0, которые на Android переадресуются на `androidx.compose.*` из BOM проекта. Проверяем это первым действием, чтобы не узнать на шестой задаче. Заодно убираем `androidx.media`, которая не импортируется ни в одном файле.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:97`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: ничего.
- Produces: координата `libs.reorderable` доступна остальным задачам.

- [ ] **Step 1: Добавить `.superpowers/` и `.claude/` в `.gitignore`**

Дописать в конец `/Users/rikarti/AndroidStudioProjects/WorkoutTimer/.gitignore`:

```gitignore
.superpowers/
.claude/
```

- [ ] **Step 2: Добавить `reorderable` и удалить `media` в version catalog**

В `gradle/libs.versions.toml`, секция `[versions]`: удалить строку `media = "1.7.1"`, добавить:

```toml
reorderable = "3.1.0"
```

В секции `[libraries]`: удалить строку `androidx-media = { group = "androidx.media", name = "media", version.ref = "media" }`, добавить:

```toml
reorderable = { module = "sh.calvin.reorderable:reorderable", version.ref = "reorderable" }
```

- [ ] **Step 3: Заменить зависимость в модуле**

В `app/build.gradle.kts` строку `implementation(libs.androidx.media)` заменить на:

```kotlin
implementation(libs.reorderable)
```

- [ ] **Step 4: Проверить, что проект собирается**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

Если сборка падает на конфликте версий Compose — библиотека несовместима с BOM 2026.03.00. Тогда: откатить шаги 2–3 в части `reorderable` (оставив удаление `media`), записать это в Task 18 и реализовать перетаскивание там вручную на `detectDragGesturesAfterLongPress`. Остальной план не меняется.

- [ ] **Step 5: Проверить, что удаление `androidx.media` ничего не сломало**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, все существующие тесты проходят.

- [ ] **Step 6: Commit**

```bash
git add .gitignore gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: подключает reorderable, снимает неиспользуемую androidx.media"
```

---

## Task 2: Токены темы, типографика, формы

Палитра шаблона Android Studio заменяется на Dark Athletic. Dynamic color и светлая схема удаляются — приложение всегда тёмное. Роли Material 3 заполняются целиком, чтобы `AlertDialog`, `Snackbar`, `ModalBottomSheet` и текстовые поля красились сами.

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/theme/Color.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/theme/Theme.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/theme/Type.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/theme/Shape.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/colors.xml`

**Interfaces:**
- Consumes: ничего.
- Produces: `Background`, `SurfaceDark`, `SurfaceElevated`, `OutlineDark`, `TextPrimary`, `TextSecondary`, `TextMuted`, `Accent`, `OnAccent`, `Destructive` — `Color`-константы. `WorkoutTimerTheme(content: @Composable () -> Unit)`. `AppShapes: Shapes`. `ScreenPadding = 18.dp`, `CardSpacing = 8.dp`, `SectionSpacing = 18.dp`, `PrimaryButtonHeight = 58.dp`.

- [ ] **Step 1: Переписать `Color.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.theme

import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B0B0F)
val SurfaceDark = Color(0xFF16161C)
val SurfaceElevated = Color(0xFF1E1E26)
val OutlineDark = Color(0xFF26262E)

val TextPrimary = Color(0xFFF2F2F5)
val TextSecondary = Color(0xFF8A8A96)
val TextMuted = Color(0xFF55555F)

val Accent = Color(0xFFC6FF3D)
val OnAccent = Color(0xFF0B0B0F)
val Destructive = Color(0xFFFF5C5C)
```

- [ ] **Step 2: Создать `Shape.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/** Горизонтальный отступ содержимого экрана. */
val ScreenPadding = 18.dp

/** Вертикальный шаг между карточками списка. */
val CardSpacing = 8.dp

/** Вертикальный шаг между секциями экрана. */
val SectionSpacing = 18.dp

/** Высота главной кнопки действия. */
val PrimaryButtonHeight = 58.dp
```

- [ ] **Step 3: Переписать `Type.kt`**

`fontFeatureSettings = "tnum"` включает табличные цифры: без них таймер дёргается при каждой смене разряда.

```kotlin
package ru.hopes.workouttimer.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Numeric = TextStyle(fontFeatureSettings = "tnum")

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = Numeric.fontFeatureSettings
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = Numeric.fontFeatureSettings
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp
    )
)
```

- [ ] **Step 4: Переписать `Theme.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = Accent,
    onSecondary = OnAccent,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceElevated,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = Destructive,
    onError = TextPrimary
)

@Composable
fun WorkoutTimerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
```

- [ ] **Step 5: Перевести XML-тему на тёмную**

В `app/src/main/res/values/themes.xml` заменить строку стиля `Theme.WorkoutTimer` на:

```xml
    <style name="Theme.WorkoutTimer" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/app_background</item>
    </style>
```

В `app/src/main/res/values/colors.xml` удалить `purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700` (шаблонные, нигде не используются), заменить значение `splash_screen` и добавить `app_background`:

```xml
    <color name="splash_screen">#0B0B0F</color>
    <color name="app_background">#0B0B0F</color>
```

Без `windowBackground` между сплешем и первым кадром Compose мелькает белый фон.

- [ ] **Step 6: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Если падает на `WorkoutTimerTheme(darkTheme = ...)` — у вызывающих остались старые параметры; в `MainActivity.kt:38` вызов уже без параметров, а `@Preview` в экранах правятся в своих задачах.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/ui/theme app/src/main/res/values/themes.xml app/src/main/res/values/colors.xml
git commit -m "feat: переводит тему на палитру Dark Athletic"
```

---

## Task 3: Базовые компоненты — кнопка, подписи, плитка, пустое состояние

Компоненты Compose не покрываются unit-тестами (в проекте их нет и мы их не заводим). Гейт задачи — сборка и `@Preview`, который рендерится в Android Studio.

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/Buttons.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/Labels.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/StatTile.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/EmptyState.kt`

**Interfaces:**
- Consumes: токены и `WorkoutTimerTheme` из Task 2.
- Produces:
  - `PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`
  - `EyebrowLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary)`
  - `SectionHeader(text: String, modifier: Modifier = Modifier)`
  - `StatTile(value: String, unit: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null)`
  - `EmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier, subtitle: String? = null, actionText: String? = null, onAction: (() -> Unit)? = null)`

- [ ] **Step 1: Написать `Buttons.kt`**

Второй кнопки (обводкой) в плане нет: ни один экран редизайна её не использует. Появится, когда появится экран, которому она нужна.

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hopes.workouttimer.presentation.ui.theme.PrimaryButtonHeight
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(PrimaryButtonHeight),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = text.uppercase(),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            letterSpacing = 0.5.sp
        )
    }
}


@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ButtonsPreview() {
    WorkoutTimerTheme {
        Box(modifier = Modifier.padding(18.dp)) {
            PrimaryButton(text = "Закончить подход", onClick = {})
        }
    }
}
```

- [ ] **Step 2: Написать `Labels.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun EyebrowLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
```

- [ ] **Step 3: Написать `StatTile.kt`**

`onClick` в этой спеке всегда `null` — правка веса на ходу идёт отдельной спекой. Параметр есть, чтобы её реализация не переписывала компонент.

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@Composable
fun StatTile(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = unit.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun StatTilePreview() {
    WorkoutTimerTheme {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(value = "80", unit = "кг", modifier = Modifier.weight(1f))
            StatTile(value = "8", unit = "повт", modifier = Modifier.weight(1f))
        }
    }
}
```

- [ ] **Step 4: Написать `EmptyState.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionText != null && onAction != null) {
                PrimaryButton(
                    text = actionText,
                    onClick = onAction,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 5: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/ui/components
git commit -m "feat: добавляет базовые компоненты дизайн-системы"
```

---

## Task 4: `WheelPicker` и `WheelRow`

Ключевой компонент плана: барабан со снапом. Используется в редакторе сейчас и в правке веса на ходу — следующей спекой. Снап делается `rememberSnapFlingBehavior`, выбранное значение вычисляется из `firstVisibleItemIndex` с поправкой на пустые элементы сверху и снизу, которые центрируют первый и последний вариант.

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/WheelPicker.kt`

**Interfaces:**
- Consumes: токены из Task 2.
- Produces:
  - `WheelPicker(items: List<T>, selectedIndex: Int, onSelected: (Int) -> Unit, label: String, modifier: Modifier = Modifier, format: (T) -> String)`
  - `WheelRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit)`
  - `wheelIndexOfNearest(values: List<Double>, target: Double): Int` — индекс ближайшего значения; нужен, чтобы вес вне сетки не сдвигал барабан в 0.

- [ ] **Step 1: Написать падающий тест на выбор ближайшего значения**

Это единственная тестируемая часть компонента — чистая функция. Создать `app/src/test/java/ru/hopes/workouttimer/presentation/ui/components/WheelPickerTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelPickerTest {

    private val weights = generateSequence(0.0) { it + 0.25 }
        .takeWhile { it <= 300.0 }
        .toList()

    @Test
    fun `точное значение находит само себя`() {
        assertEquals(80.0, weights[wheelIndexOfNearest(weights, 80.0)], 0.0001)
    }

    @Test
    fun `значение вне сетки округляется к ближайшему`() {
        assertEquals(47.25, weights[wheelIndexOfNearest(weights, 47.3)], 0.0001)
    }

    @Test
    fun `значение ниже диапазона даёт первый элемент`() {
        assertEquals(0, wheelIndexOfNearest(weights, -10.0))
    }

    @Test
    fun `значение выше диапазона даёт последний элемент`() {
        assertEquals(weights.lastIndex, wheelIndexOfNearest(weights, 999.0))
    }

    @Test
    fun `пустой список даёт нулевой индекс`() {
        assertEquals(0, wheelIndexOfNearest(emptyList(), 5.0))
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "*WheelPickerTest*"`
Expected: FAIL — `Unresolved reference: wheelIndexOfNearest`.

- [ ] **Step 3: Написать `WheelPicker.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val ItemHeight = 42.dp
private const val VISIBLE_ITEMS = 5
private const val EDGE_ITEMS = VISIBLE_ITEMS / 2

/**
 * Индекс значения, ближайшего к [target]. Нужен, чтобы вес, введённый когда-то
 * с клавиатуры и не попадающий в шаг барабана, не сбрасывал барабан в начало.
 */
fun wheelIndexOfNearest(values: List<Double>, target: Double): Int {
    if (values.isEmpty()) return 0
    var best = 0
    var bestDelta = abs(values[0] - target)
    for (i in values.indices) {
        val delta = abs(values[i] - target)
        if (delta < bestDelta) {
            best = i
            bestDelta = delta
        }
    }
    return best
}

@Composable
fun <T> WheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    format: (T) -> String
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    val centeredIndex by remember {
        derivedStateOf {
            val offset = state.firstVisibleItemScrollOffset
            val index = state.firstVisibleItemIndex
            if (offset > 0) index + 1 else index
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val index = centeredIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    if (index != selectedIndex) onSelected(index)
                }
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.height(ItemHeight * VISIBLE_ITEMS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // count = ... обязателен: внутри LazyColumn имя items занято параметром-списком.
            items(count = EDGE_ITEMS) { Box(modifier = Modifier.height(ItemHeight)) }
            items(count = items.size) { index ->
                val isSelected = index == centeredIndex
                Box(
                    modifier = Modifier
                        .height(ItemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = format(items[index]),
                        fontSize = if (isSelected) 26.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(count = EDGE_ITEMS) { Box(modifier = Modifier.height(ItemHeight)) }
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Несколько барабанов в ряд с общей полосой выделения по центру.
 */
@Composable
fun WheelRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ItemHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content
        )
    }
}
```

Полоса выделения лежит под барабанами в `Box`, поэтому её высота обязана совпадать с `ItemHeight` — при расхождении выделение «съедет» с центрального элемента.

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "*WheelPickerTest*"`
Expected: PASS, 5 тестов.

- [ ] **Step 5: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/WheelPicker.kt app/src/test/java/ru/hopes/workouttimer/presentation/ui/components/WheelPickerTest.kt
git commit -m "feat: добавляет барабан выбора значений со снапом"
```

---

## Task 5: Нижние листы — `AppBottomSheet` и `ActionSheet`

`ActionSheet` заменяет `DropdownMenu` на карточках списка. `AppBottomSheet` — общая обёртка, её же использует лист-редактор упражнения (Task 18) и лист выбора упражнения (Task 15).

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/Sheets.kt`

**Interfaces:**
- Consumes: токены из Task 2.
- Produces:
  - `AppBottomSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`
  - `data class ActionSheetItem(val text: String, val icon: ImageVector, val onClick: () -> Unit, val subtitle: String? = null, val destructive: Boolean = false)`
  - `ActionSheet(title: String, subtitle: String?, items: List<ActionSheetItem>, onDismiss: () -> Unit)`

- [ ] **Step 1: Написать `Sheets.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding

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
}
```

- [ ] **Step 2: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/Sheets.kt
git commit -m "feat: добавляет нижние листы и меню действий"
```

---

## Task 6: `RestRing` и `ProgressSegments`

Кольцо таймера отдыха и полоски прогресса по упражнениям. Анимация кольца — та же, что в существующем `CircularTimer` (`WorkoutExecutionScreen.kt:483–522`): `tween(200, LinearEasing)`, совпадающий с частотой обновления во ViewModel, иначе прогресс дёргается.

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/RestRing.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/ProgressSegments.kt`

**Interfaces:**
- Consumes: токены из Task 2.
- Produces:
  - `RestRing(timeLeftMillis: Long, totalTimeMillis: Long, modifier: Modifier = Modifier)`
  - `ProgressSegments(total: Int, currentIndex: Int, modifier: Modifier = Modifier)` — `currentIndex` нумеруется с нуля.

- [ ] **Step 1: Написать `RestRing.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun RestRing(
    timeLeftMillis: Long,
    totalTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    val target = if (totalTimeMillis > 0L) {
        (timeLeftMillis.toFloat() / totalTimeMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 200 мс совпадает с частотой тика во ViewModel: при большем значении
    // кольцо отстаёт от цифр, при меньшем — дёргается.
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "RestRingProgress"
    )

    val track = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val stroke = 10.dp

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatClock(timeLeftMillis),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 52.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "из ${formatClock(totalTimeMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}
```

- [ ] **Step 2: Написать `ProgressSegments.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun ProgressSegments(
    total: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    if (total <= 0) return
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(total) { index ->
            val color = when {
                index < currentIndex -> accent.copy(alpha = 0.5f)
                index == currentIndex -> accent
                else -> track
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            ) {}
        }
    }
}
```

- [ ] **Step 3: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/RestRing.kt app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/ProgressSegments.kt
git commit -m "feat: добавляет кольцо таймера и сегменты прогресса"
```

---

## Task 7: `formatDurationCompact`

Карточки очереди показывают длительность как `MM:SS` («52:10»), а существующий `formatDurationToString` отдаёт «52 мин». Нужен второй форматтер; старый остаётся — им пользуется экран истории и виджет.

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/utils/DateFormatter.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/utils/DateFormatterTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: `DateFormatter.formatDurationCompact(millis: Long): String` — `MM:SS` до часа, `H:MM:SS` от часа.

- [ ] **Step 1: Написать падающие тесты**

Дописать в конец класса `DateFormatterTest`:

```kotlin
    @Test
    fun `formatDurationCompact печатает минуты и секунды`() {
        assertEquals("52:10", DateFormatter.formatDurationCompact(3_130_000L))
    }

    @Test
    fun `formatDurationCompact дополняет секунды нулём`() {
        assertEquals("05:07", DateFormatter.formatDurationCompact(307_000L))
    }

    @Test
    fun `formatDurationCompact добавляет часы после шестидесяти минут`() {
        assertEquals("1:05:03", DateFormatter.formatDurationCompact(3_903_000L))
    }

    @Test
    fun `formatDurationCompact печатает ноль`() {
        assertEquals("00:00", DateFormatter.formatDurationCompact(0L))
    }
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "*DateFormatterTest*"`
Expected: FAIL — `Unresolved reference: formatDurationCompact`.

- [ ] **Step 3: Добавить функцию**

Дописать в `object DateFormatter`, рядом с `formatDurationToString`:

```kotlin
    /**
     * Компактная длительность для карточек очереди: «52:10», «1:05:03».
     * В отличие от [formatDurationToString] не округляет до минут — в зале
     * секунды прошлой тренировки видно, и они складываются в ощущение прогресса.
     */
    fun formatDurationCompact(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
```

Добавить импорт `java.util.Locale`, если его ещё нет в файле.

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*DateFormatterTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/utils/DateFormatter.kt app/src/test/java/ru/hopes/workouttimer/presentation/utils/DateFormatterTest.kt
git commit -m "feat: добавляет компактный формат длительности"
```

---

## Task 8: Сортировка очереди в DAO и виджете

Очередь — это `lastUseAt ASC`: первой идёт тренировка, которую не делали дольше всех. Вторичный ключ `id ASC` обязателен: без него порядок при равных `lastUseAt` недетерминирован, а импорт ставит одинаковые значения из файла (`ExportImportRepositoryImpl.kt:118`).

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt:16-20,41-50`
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCase.kt:20`
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCaseTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: все списки тренировок приходят в порядке очереди.

- [ ] **Step 1: Переписать тест порядка в виджете**

В `GetWidgetWorkoutsUseCaseTest` заменить тест `sorts workouts by lastUseAt descending` целиком на:

```kotlin
    @Test
    fun `sorts workouts by lastUseAt ascending`() = runTest {
        val result = useCase(
            workouts = listOf(
                workoutWith(id = 1, name = "Старая", lastUseAt = 100L),
                workoutWith(id = 2, name = "Свежая", lastUseAt = 300L),
                workoutWith(id = 3, name = "Средняя", lastUseAt = 200L)
            )
        )().first()

        assertEquals(listOf("Старая", "Средняя", "Свежая"), result.map { it.name })
    }
```

- [ ] **Step 2: Починить второй тест, который упадёт от смены порядка**

Тест `puts the last session duration on the matching workout` берёт `result[0]` у набора `id=7 (lastUseAt=100)` / `id=9 (lastUseAt=50)`. После `sortedBy` первым станет `id=9`, у которого длительности нет. Заменить тело теста на:

```kotlin
    @Test
    fun `puts the last session duration on the matching workout`() = runTest {
        val result = useCase(
            workouts = listOf(
                workoutWith(id = 7, name = "Ноги", lastUseAt = 100L),
                workoutWith(id = 9, name = "Спина", lastUseAt = 50L)
            ),
            durations = mapOf(7 to 3_120_000L)
        )().first()

        // После сортировки по возрастанию первой идёт «Спина» (lastUseAt = 50).
        assertEquals("Спина", result[0].name)
        assertNull(result[0].lastDurationMillis)
        assertEquals(3_120_000L, result[1].lastDurationMillis)
    }
```

- [ ] **Step 3: Запустить тесты и убедиться, что оба падают**

Run: `./gradlew :app:testDebugUnitTest --tests "*GetWidgetWorkoutsUseCaseTest*"`
Expected: FAIL — оба теста, порядок пока по убыванию.

- [ ] **Step 4: Поменять сортировку в use case**

В `GetWidgetWorkoutsUseCase.kt:20` заменить:

```kotlin
                .sortedBy { it.workout.lastUseAt }
```

- [ ] **Step 5: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*GetWidgetWorkoutsUseCaseTest*"`
Expected: PASS, все тесты файла.

- [ ] **Step 6: Добавить сортировку в DAO**

В `WorkoutDao.kt` заменить три запроса. Первый (`:15-17`):

```kotlin
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY lastUseAt ASC, id ASC")
    fun getAllWorkoutsWithExercises(): Flow<List<WorkoutWithExercises>>
```

Второй (`:19-20`):

```kotlin
    @Query("SELECT * FROM workouts ORDER BY lastUseAt ASC, id ASC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>
```

Третий — поисковый (`:41-50`), меняется только `ORDER BY`:

```kotlin
    @Transaction
    @Query(
        """
        SELECT DISTINCT workouts.* FROM workouts JOIN exercises
        ON workouts.id == exercises.workoutId
        WHERE workouts.name LIKE '%' || :query || '%'
        OR exercises.name LIKE '%' || :query || '%'
        ORDER BY workouts.lastUseAt ASC, workouts.id ASC
        """
    )
    fun searchWorkouts(query: String): Flow<List<WorkoutEntity>>
```

- [ ] **Step 7: Проверить сборку и все тесты**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Схема БД не менялась — версия и миграции не трогаются.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCase.kt app/src/test/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCaseTest.kt
git commit -m "feat: сортирует тренировки по очереди ротации"
```

---

## Task 9: Поиск отдаёт `WorkoutWithExercises`

Экран списка переходит на `WorkoutWithExercises`, чтобы показывать число упражнений. Обе ветки `flatMapLatest` во ViewModel пишут в одно поле состояния, поэтому поиск обязан отдавать тот же тип. `searchWorkouts` уже помечен `@Transaction`, так что Room заполнит упражнения сам — нового запроса не нужно.

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt:50`
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt:16`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/SearchWorkoutsUseCase.kt`

**Interfaces:**
- Consumes: сортировку из Task 8.
- Produces: `SearchWorkoutsUseCase.invoke(query: String): Flow<List<WorkoutWithExercises>>`.

- [ ] **Step 1: Поменять тип в DAO**

В `WorkoutDao.kt` у запроса `searchWorkouts` заменить строку сигнатуры на:

```kotlin
    fun searchWorkouts(query: String): Flow<List<WorkoutWithExercises>>
```

- [ ] **Step 2: Поменять тип в интерфейсе репозитория**

В `WorkoutRepository.kt:16`:

```kotlin
        fun searchWorkoutUseCase(query: String): Flow<List<WorkoutWithExercises>>
```

- [ ] **Step 3: Поменять тип в реализации**

В `WorkoutRepositoryImpl.kt` найти `override fun searchWorkoutUseCase` и заменить сигнатуру на `Flow<List<WorkoutWithExercises>>`; тело (`dao.searchWorkouts(query)`) не меняется. Убрать импорт `WorkoutEntity`, если он больше не нужен.

- [ ] **Step 4: Поменять тип в use case**

Переписать `SearchWorkoutsUseCase.kt` целиком:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class SearchWorkoutsUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(query: String): Flow<List<WorkoutWithExercises>> {
        return repo.searchWorkoutUseCase(query)
    }
}
```

- [ ] **Step 5: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL в `ListWorkoutViewModel.kt` — `flatMapLatest` смешивает типы. Это ожидаемо: ViewModel чинится в Task 13. Чтобы не оставлять ветку несобираемой, временно привести ветку поиска к прежнему типу одной строкой в `ListWorkoutViewModel`:

```kotlin
                val workoutsFlow = if (input.isBlank()) {
                    getAllWorkoutsUseCase()
                } else {
                    searchWorkoutsUseCase(input).map { list -> list.map { it.workout } }
                }
```

Добавить импорт `kotlinx.coroutines.flow.map`. Эта строка уходит в Task 13.

- [ ] **Step 6: Проверить сборку и тесты**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/data app/src/main/java/ru/hopes/workouttimer/domain
git commit -m "refactor: поиск возвращает тренировки вместе с упражнениями"
```

---

## Task 10: `setLastUseAt` и `getLastUseAt` в репозитории

Для «Пропустить» нужно прочитать текущий `lastUseAt` и поставить свой; для «Отменить» — вернуть прежний. Существующий `getWorkoutById` в репозитории (`WorkoutRepositoryImpl.kt:34-39`) собирает весь список с упражнениями и ищет по id — ради одного `Long` это слишком дорого, поэтому добавляется тонкий метод поверх точечного DAO-запроса (`WorkoutDao.kt:38-39`).

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt:99-102`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces:
  - `WorkoutRepository.setLastUseAt(workoutId: Int, timestamp: Long)`
  - `WorkoutRepository.getLastUseAt(workoutId: Int): Long?`

- [ ] **Step 1: Написать падающие тесты**

Дописать в `WorkoutRepositoryImplTest`:

```kotlin
    @Test
    fun `setLastUseAt пишет переданное время и обновляет виджет`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val widgetUpdater = mockk<WidgetUpdater>(relaxed = true)
        val repo = WorkoutRepositoryImpl(dao, widgetUpdater)

        repo.setLastUseAt(workoutId = 5, timestamp = 1_700_000_000_000L)

        coVerify { dao.updateLastUseAt(5, 1_700_000_000_000L) }
        coVerify { widgetUpdater.requestUpdate() }
    }

    @Test
    fun `getLastUseAt возвращает null для несуществующей тренировки`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        coEvery { dao.getWorkoutById(42) } returns null
        val repo = WorkoutRepositoryImpl(dao, mockk(relaxed = true))

        assertNull(repo.getLastUseAt(42))
    }

    @Test
    fun `getLastUseAt возвращает сохранённое время`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        coEvery { dao.getWorkoutById(7) } returns WorkoutEntity(
            id = 7,
            name = "Ноги",
            lastUseAt = 555L
        )
        val repo = WorkoutRepositoryImpl(dao, mockk(relaxed = true))

        assertEquals(555L, repo.getLastUseAt(7))
    }
```

Импорты, которых может не быть в файле: `io.mockk.coEvery`, `io.mockk.coVerify`, `io.mockk.mockk`, `kotlinx.coroutines.test.runTest`, `org.junit.Assert.assertEquals`, `org.junit.Assert.assertNull`, `ru.hopes.workouttimer.data.entity.WorkoutEntity`, `ru.hopes.workouttimer.domain.repository.WidgetUpdater`, `ru.hopes.workouttimer.data.dao.WorkoutDao`.

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "*WorkoutRepositoryImplTest*"`
Expected: FAIL — `Unresolved reference: setLastUseAt`.

- [ ] **Step 3: Добавить методы в интерфейс**

В `WorkoutRepository.kt` после `suspend fun updateLastUseAt(workoutId: Int)`:

```kotlin
        suspend fun setLastUseAt(workoutId: Int, timestamp: Long)
        suspend fun getLastUseAt(workoutId: Int): Long?
```

- [ ] **Step 4: Реализовать методы**

В `WorkoutRepositoryImpl.kt` после `updateLastUseAt` (`:99-102`):

```kotlin
    // Виджет обязан обновиться и здесь: иначе «Отменить пропуск» починит очередь
    // в приложении и оставит на домашнем экране неправильную.
    override suspend fun setLastUseAt(workoutId: Int, timestamp: Long) {
        dao.updateLastUseAt(workoutId, timestamp)
        widgetUpdater.requestUpdate()
    }

    override suspend fun getLastUseAt(workoutId: Int): Long? {
        return dao.getWorkoutById(workoutId)?.lastUseAt
    }
```

- [ ] **Step 5: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*WorkoutRepositoryImplTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt
git commit -m "feat: добавляет точечное чтение и запись lastUseAt"
```

---

## Task 11: `SkipWorkoutUseCase` и `UndoSkipWorkoutUseCase`

«Пропустить» помечает тренировку как сделанную сегодня и отправляет в конец очереди, но **не пишет сессию в историю** — статистика не должна считать пропуск тренировкой. Возвращаемое значение нужно снекбару «Отменить».

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/SkipWorkoutUseCase.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/UndoSkipWorkoutUseCase.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/usecase/SkipWorkoutUseCaseTest.kt`

**Interfaces:**
- Consumes: `setLastUseAt`, `getLastUseAt` из Task 10.
- Produces:
  - `SkipWorkoutUseCase.invoke(workoutId: Int): Long?` — прежний `lastUseAt`, `null` если тренировки нет.
  - `UndoSkipWorkoutUseCase.invoke(workoutId: Int, previousLastUseAt: Long)`

- [ ] **Step 1: Написать падающие тесты**

Создать `app/src/test/java/ru/hopes/workouttimer/domain/usecase/SkipWorkoutUseCaseTest.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.domain.repository.WorkoutRepository

class SkipWorkoutUseCaseTest {

    @Test
    fun `возвращает прежний lastUseAt и ставит текущее время`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        coEvery { repo.getLastUseAt(3) } returns 1_000L
        val before = System.currentTimeMillis()

        val previous = SkipWorkoutUseCase(repo)(workoutId = 3)

        assertEquals(1_000L, previous)
        coVerify {
            repo.setLastUseAt(
                workoutId = 3,
                timestamp = match { it >= before }
            )
        }
    }

    @Test
    fun `не пишет сессию в историю`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        coEvery { repo.getLastUseAt(3) } returns 1_000L

        SkipWorkoutUseCase(repo)(workoutId = 3)

        coVerify(exactly = 0) {
            repo.addWorkoutSession(any(), any(), any(), any())
        }
    }

    @Test
    fun `для несуществующей тренировки возвращает null и ничего не пишет`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        coEvery { repo.getLastUseAt(99) } returns null

        assertNull(SkipWorkoutUseCase(repo)(workoutId = 99))

        coVerify(exactly = 0) { repo.setLastUseAt(any(), any()) }
    }

    @Test
    fun `отмена возвращает прежнее значение`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)

        UndoSkipWorkoutUseCase(repo)(workoutId = 3, previousLastUseAt = 1_000L)

        coVerify { repo.setLastUseAt(3, 1_000L) }
    }
}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "*SkipWorkoutUseCaseTest*"`
Expected: FAIL — `Unresolved reference: SkipWorkoutUseCase`.

- [ ] **Step 3: Написать `SkipWorkoutUseCase.kt`**

```kotlin
package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

/**
 * Помечает тренировку как сделанную сегодня, отправляя её в конец очереди,
 * но НЕ создаёт сессию: пропуск не должен попадать в историю и статистику.
 *
 * @return прежний `lastUseAt` для отмены, либо `null`, если тренировки нет.
 */
class SkipWorkoutUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workoutId: Int): Long? {
        val previous = repo.getLastUseAt(workoutId) ?: return null
        repo.setLastUseAt(workoutId, System.currentTimeMillis())
        return previous
    }
}
```

- [ ] **Step 4: Написать `UndoSkipWorkoutUseCase.kt`**

```kotlin
package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

/** Возвращает тренировке `lastUseAt`, который был до пропуска. */
class UndoSkipWorkoutUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workoutId: Int, previousLastUseAt: Long) {
        repo.setLastUseAt(workoutId, previousLastUseAt)
    }
}
```

- [ ] **Step 5: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*SkipWorkoutUseCaseTest*"`
Expected: PASS, 4 теста. Провайдеры в `AppModule` не нужны — оба класса создаются через `@Inject constructor`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/usecase/SkipWorkoutUseCase.kt app/src/main/java/ru/hopes/workouttimer/domain/usecase/UndoSkipWorkoutUseCase.kt app/src/test/java/ru/hopes/workouttimer/domain/usecase/SkipWorkoutUseCaseTest.kt
git commit -m "feat: добавляет пропуск тренировки и его отмену"
```

---

## Task 12: Новая тренировка встаёт первой в очереди

Сейчас `CreateWorkoutViewModel` ставит новой тренировке `lastUseAt = System.currentTimeMillis()`, из-за чего при сортировке по возрастанию она оказывается **последней**, хотя её ни разу не делали. Для создания значение становится `0L`, и карточка показывает «ещё не делали».

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt:114`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModelTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: контракт «`lastUseAt == 0L` означает „ещё не делали“», на который опирается Task 14.

- [ ] **Step 1: Написать падающий тест**

Создать `app/src/test/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModelTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.creation

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.UpdateWorkoutUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        add: AddWorkoutUseCase = mockk(relaxed = true),
        update: UpdateWorkoutUseCase = mockk(relaxed = true)
    ) = CreateWorkoutViewModel(add, mockk(relaxed = true), update)

    @Test
    fun `новая тренировка сохраняется с нулевым lastUseAt`() = runTest(dispatcher) {
        val add = mockk<AddWorkoutUseCase>(relaxed = true)
        val vm = viewModel(add = add)

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val exerciseId = vm.state.value.exercises.first().id
        vm.processCommand(
            CreateWorkoutCommand.UpdateExercise(
                exerciseId,
                vm.state.value.exercises.first().copy(name = "Жим ног")
            )
        )
        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        val saved = slot<Workout>()
        coVerify { add(capture(saved)) }
        assertEquals(0L, saved.captured.lastUseAt)
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "*CreateWorkoutViewModelTest*"`
Expected: FAIL — `expected:<0> but was:<1790...>`.

- [ ] **Step 3: Поменять значение для пути создания**

В `CreateWorkoutViewModel.kt` в блоке сохранения заменить строку

```kotlin
                            lastUseAt = editingLastUseAt ?: System.currentTimeMillis()
```

на

```kotlin
                            // 0 означает «ещё не делали»: при сортировке очереди по
                            // возрастанию новая тренировка встаёт первой, а не последней.
                            lastUseAt = editingLastUseAt ?: 0L
```

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "*CreateWorkoutViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt app/src/test/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModelTest.kt
git commit -m "feat: новая тренировка встаёт первой в очереди"
```

---

## Task 13: `ListWorkoutViewModel` — очередь, пропуск, чистка конструктора

Состояние переходит на `WorkoutWithExercises`, появляются «Пропустить» и «Отменить». Заодно уходит мусор: закомментированные блоки создания тестовых тренировок (`:43-121`) и три неиспользуемых параметра конструктора.

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModelTest.kt`

**Interfaces:**
- Consumes: `SearchWorkoutsUseCase` из Task 9, `SkipWorkoutUseCase`/`UndoSkipWorkoutUseCase` из Task 11.
- Produces:
  - `ListWorkoutState(query: String, workouts: List<WorkoutWithExercises>, lastSessionDurations: Map<Int, Long>, skippedWorkout: SkippedWorkout?)`
  - `data class SkippedWorkout(val id: Int, val name: String, val previousLastUseAt: Long)`
  - `ListWorkoutViewModel.skipWorkout(workout: WorkoutEntity)`, `.undoSkip()`, `.dismissSkipUndo()`, `.deleteWorkout(workout: WorkoutEntity)`, `.updateSearchQuery(newQuery: String)`

- [ ] **Step 1: Переписать тест состояния**

Заменить содержимое `ListWorkoutViewModelTest` на:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workouts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.usecase.DeleteWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsWithExerciseUseCase
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase
import ru.hopes.workouttimer.domain.usecase.SearchWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.SkipWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.UndoSkipWorkoutUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ListWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun workoutWith(id: Int, name: String, lastUseAt: Long) = WorkoutWithExercises(
        workout = WorkoutEntity(id = id, name = name, lastUseAt = lastUseAt),
        exercises = listOf(
            ExerciseEntity(
                id = id,
                workoutId = id.toLong(),
                name = "Упражнение",
                weight = 10.0,
                sets = 3,
                reps = 12,
                restTimeMillis = 120_000L,
                orderInWorkout = 1,
                note = ""
            )
        )
    )

    private fun viewModel(
        workouts: List<WorkoutWithExercises> = emptyList(),
        durations: Map<Int, Long> = emptyMap(),
        skip: SkipWorkoutUseCase = mockk(relaxed = true),
        undoSkip: UndoSkipWorkoutUseCase = mockk(relaxed = true)
    ): ListWorkoutViewModel {
        val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
        val search = mockk<SearchWorkoutsUseCase>()
        val getDurations = mockk<GetLastSessionDurationsUseCase>()
        every { getAll() } returns flowOf(workouts)
        every { search(any()) } returns flowOf(workouts)
        every { getDurations() } returns flowOf(durations)
        return ListWorkoutViewModel(
            getAll,
            search,
            mockk<DeleteWorkoutUseCase>(relaxed = true),
            getDurations,
            skip,
            undoSkip
        )
    }

    @Test
    fun `состояние соединяет тренировки с длительностями прошлых сессий`() = runTest(dispatcher) {
        val vm = viewModel(
            workouts = listOf(workoutWith(1, "Ноги", 0L)),
            durations = mapOf(1 to 3_120_000L)
        )
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.workouts.size)
        assertEquals("Ноги", state.workouts.first().workout.name)
        assertEquals(1, state.workouts.first().exercises.size)
        assertEquals(3_120_000L, state.lastSessionDurations[1])
    }

    @Test
    fun `пропуск запоминает прежнее время для отмены`() = runTest(dispatcher) {
        val skip = mockk<SkipWorkoutUseCase>()
        coEvery { skip(7) } returns 555L
        val vm = viewModel(skip = skip)
        testScheduler.advanceUntilIdle()

        vm.skipWorkout(WorkoutEntity(id = 7, name = "Спина", lastUseAt = 555L))
        testScheduler.advanceUntilIdle()

        val skipped = vm.state.value.skippedWorkout
        assertEquals(7, skipped?.id)
        assertEquals("Спина", skipped?.name)
        assertEquals(555L, skipped?.previousLastUseAt)
    }

    @Test
    fun `отмена возвращает прежнее время и гасит снекбар`() = runTest(dispatcher) {
        val skip = mockk<SkipWorkoutUseCase>()
        val undo = mockk<UndoSkipWorkoutUseCase>(relaxed = true)
        coEvery { skip(7) } returns 555L
        val vm = viewModel(skip = skip, undoSkip = undo)
        testScheduler.advanceUntilIdle()

        vm.skipWorkout(WorkoutEntity(id = 7, name = "Спина", lastUseAt = 555L))
        testScheduler.advanceUntilIdle()
        vm.undoSkip()
        testScheduler.advanceUntilIdle()

        coVerify { undo(7, 555L) }
        assertNull(vm.state.value.skippedWorkout)
    }

    @Test
    fun `пропуск несуществующей тренировки не показывает снекбар`() = runTest(dispatcher) {
        val skip = mockk<SkipWorkoutUseCase>()
        coEvery { skip(9) } returns null
        val vm = viewModel(skip = skip)
        testScheduler.advanceUntilIdle()

        vm.skipWorkout(WorkoutEntity(id = 9, name = "Нет", lastUseAt = 0L))
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.skippedWorkout)
    }
}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "*ListWorkoutViewModelTest*"`
Expected: FAIL — конструктор не совпадает, `skipWorkout` не найден.

- [ ] **Step 3: Переписать `ListWorkoutViewModel.kt` целиком**

```kotlin
package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.usecase.DeleteWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsWithExerciseUseCase
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase
import ru.hopes.workouttimer.domain.usecase.SearchWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.SkipWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.UndoSkipWorkoutUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListWorkoutViewModel @Inject constructor(
    private val getAllWorkoutsWithExerciseUseCase: GetAllWorkoutsWithExerciseUseCase,
    private val searchWorkoutsUseCase: SearchWorkoutsUseCase,
    private val deleteWorkoutUseCase: DeleteWorkoutUseCase,
    private val getLastSessionDurationsUseCase: GetLastSessionDurationsUseCase,
    private val skipWorkoutUseCase: SkipWorkoutUseCase,
    private val undoSkipWorkoutUseCase: UndoSkipWorkoutUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(ListWorkoutState())
    val state = _state.asStateFlow()

    init {
        query
            .onEach { input ->
                _state.update { it.copy(query = input) }
            }
            .flatMapLatest { input ->
                val workoutsFlow = if (input.isBlank()) {
                    getAllWorkoutsWithExerciseUseCase()
                } else {
                    searchWorkoutsUseCase(input)
                }
                workoutsFlow.combine(getLastSessionDurationsUseCase()) { workouts, durations ->
                    workouts to durations
                }
            }
            .onEach { (workouts, durations) ->
                _state.update { it.copy(workouts = workouts, lastSessionDurations = durations) }
            }
            .launchIn(viewModelScope)
    }

    fun updateSearchQuery(newQuery: String) {
        query.update { newQuery.trim() }
    }

    fun deleteWorkout(workout: WorkoutEntity) {
        viewModelScope.launch {
            deleteWorkoutUseCase(workout)
        }
    }

    /**
     * Отправляет тренировку в конец очереди без записи в историю.
     * Прежнее время кладётся в состояние, чтобы снекбар мог предложить отмену.
     */
    fun skipWorkout(workout: WorkoutEntity) {
        viewModelScope.launch {
            val previous = skipWorkoutUseCase(workout.id) ?: return@launch
            _state.update {
                it.copy(
                    skippedWorkout = SkippedWorkout(
                        id = workout.id,
                        name = workout.name,
                        previousLastUseAt = previous
                    )
                )
            }
        }
    }

    fun undoSkip() {
        val skipped = _state.value.skippedWorkout ?: return
        viewModelScope.launch {
            undoSkipWorkoutUseCase(skipped.id, skipped.previousLastUseAt)
            _state.update { it.copy(skippedWorkout = null) }
        }
    }

    fun dismissSkipUndo() {
        _state.update { it.copy(skippedWorkout = null) }
    }
}

/** Пропущенная тренировка, пока на экране висит снекбар с отменой. */
data class SkippedWorkout(
    val id: Int,
    val name: String,
    val previousLastUseAt: Long
)

data class ListWorkoutState(
    val query: String = "",
    val workouts: List<WorkoutWithExercises> = listOf(),
    val lastSessionDurations: Map<Int, Long> = emptyMap(),
    val skippedWorkout: SkippedWorkout? = null
) {
    /** Первая в очереди — та, которую не делали дольше всех. */
    val nextWorkout: WorkoutWithExercises? get() = workouts.firstOrNull()

    /** Остальные, в порядке очереди. */
    val restOfQueue: List<WorkoutWithExercises> get() = workouts.drop(1)

    val isSearching: Boolean get() = query.isNotBlank()
}
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*ListWorkoutViewModelTest*"`
Expected: PASS, 4 теста.

- [ ] **Step 5: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL в `ListWorkoutScreen.kt` — экран ждёт `List<WorkoutEntity>`. Это ожидаемо, экран переписывается в Task 14. Не чинить здесь; перейти к Task 14 и закоммитить обе задачи, если ветка должна оставаться собираемой на каждом коммите. Если коммитим по задачам — коммитим сейчас, сборка чинится следующей задачей.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModel.kt app/src/test/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModelTest.kt
git commit -m "feat: очередь и пропуск тренировки в ListWorkoutViewModel"
```

---

## Task 14: Главный экран — очередь, герой-карта, меню действий

Экран переписывается целиком. Герой-карта «Следующая» + список очереди с номерами позиций, действия через `ActionSheet`, снекбар отмены пропуска. Поиск прячется за иконку; пока он активен, герой-карта не показывается, а «Пропустить» из меню убирается — пропуск переставил бы очередь, которой в этот момент не видно.

**Files:**
- Rewrite: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt:35-80`

**Interfaces:**
- Consumes: `ListWorkoutState` из Task 13, компоненты из Tasks 3/5, `formatDurationCompact` из Task 7.
- Produces: `ListWorkoutScreen(modifier, viewModel, onAddWorkoutClick, onWorkoutClick: (WorkoutEntity) -> Unit, onEditClick, onExportImportClick, onHistoryClick)` — параметр `onLongClick` удалён.

- [ ] **Step 1: Написать новый `ListWorkoutScreen.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.presentation.ui.components.ActionSheet
import ru.hopes.workouttimer.presentation.ui.components.ActionSheetItem
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.SectionHeader
import ru.hopes.workouttimer.presentation.ui.theme.Accent
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.OnAccent
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.SectionSpacing
import ru.hopes.workouttimer.presentation.utils.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: ListWorkoutViewModel = hiltViewModel(),
    onAddWorkoutClick: () -> Unit,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onExportImportClick: () -> Unit = {},
    onHistoryClick: (WorkoutEntity) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var workoutToDelete by rememberSaveable { mutableStateOf<WorkoutEntity?>(null) }
    var menuFor by remember { mutableStateOf<WorkoutEntity?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Снекбар отмены пропуска. dismissSkipUndo() гасит состояние и по таймауту,
    // иначе «Отменить» осталась бы доступной после того, как снекбар исчез.
    LaunchedEffect(state.skippedWorkout) {
        val skipped = state.skippedWorkout ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "${skipped.name} пропущена",
            actionLabel = "Отменить"
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoSkip()
        } else {
            viewModel.dismissSkipUndo()
        }
    }

    workoutToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text("Удалить тренировку?") },
            text = { Text("«${target.name}» и её история будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkout(target)
                    workoutToDelete = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) { Text("Отмена") }
            }
        )
    }

    menuFor?.let { target ->
        val items = buildList {
            add(
                ActionSheetItem("Начать", Icons.Default.PlayArrow, {
                    menuFor = null
                    onWorkoutClick(target)
                })
            )
            // В режиме поиска очередь не видна целиком, поэтому пропуск скрыт:
            // он переставил бы порядок, которого пользователь сейчас не наблюдает.
            if (!state.isSearching) {
                add(
                    ActionSheetItem("Пропустить", Icons.Default.SkipNext, {
                        menuFor = null
                        viewModel.skipWorkout(target)
                    }, subtitle = "в конец очереди")
                )
            }
            add(
                ActionSheetItem("Редактировать", Icons.Default.Edit, {
                    menuFor = null
                    onEditClick(target)
                })
            )
            add(
                ActionSheetItem("История", Icons.Default.History, {
                    menuFor = null
                    onHistoryClick(target)
                })
            )
            add(
                ActionSheetItem("Удалить", Icons.Default.Delete, {
                    menuFor = null
                    workoutToDelete = target
                }, destructive = true)
            )
        }
        ActionSheet(
            title = target.name,
            subtitle = subtitleFor(target.lastUseAt),
            items = items,
            onDismiss = { menuFor = null }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddWorkoutClick,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Новая", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Тренировки",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    searchVisible = !searchVisible
                    if (!searchVisible) viewModel.updateSearchQuery("")
                }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onExportImportClick) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Экспорт и импорт",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (searchVisible) {
                TextField(
                    value = state.query,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding),
                    placeholder = { Text("Название тренировки или упражнения") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            if (state.workouts.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FitnessCenter,
                    title = if (state.isSearching) "Ничего не найдено" else "Нет тренировок",
                    subtitle = if (state.isSearching) {
                        "Попробуйте другой запрос"
                    } else {
                        "Создайте первую — она сразу встанет первой в очереди"
                    },
                    actionText = if (state.isSearching) null else "Создать тренировку",
                    onAction = if (state.isSearching) null else onAddWorkoutClick
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = SectionSpacing,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(CardSpacing)
            ) {
                // В поиске очереди нет — только плоский список совпадений.
                if (!state.isSearching) {
                    state.nextWorkout?.let { next ->
                        item(key = "hero-${next.workout.id}") {
                            NextWorkoutCard(
                                item = next,
                                durationMillis = state.lastSessionDurations[next.workout.id],
                                onStart = { onWorkoutClick(next.workout) },
                                onMenu = { menuFor = next.workout }
                            )
                        }
                    }
                    if (state.restOfQueue.isNotEmpty()) {
                        item(key = "queue-header") {
                            SectionHeader(
                                text = "Дальше по очереди",
                                modifier = Modifier.padding(top = SectionSpacing, bottom = 2.dp)
                            )
                        }
                    }
                }

                val rows = if (state.isSearching) state.workouts else state.restOfQueue
                items(rows, key = { it.workout.id }) { item ->
                    val position = state.workouts.indexOf(item) + 1
                    WorkoutRow(
                        item = item,
                        position = if (state.isSearching) null else position,
                        durationMillis = state.lastSessionDurations[item.workout.id],
                        onClick = { onWorkoutClick(item.workout) },
                        onMenu = { menuFor = item.workout }
                    )
                }
            }
        }
    }
}

/** Подпись давности. `lastUseAt == 0` означает «ещё не делали» (см. Task 12). */
private fun subtitleFor(lastUseAt: Long): String {
    if (lastUseAt == 0L) return "ещё не делали"
    val days = ((System.currentTimeMillis() - lastUseAt) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "сегодня"
        days == 1 -> "вчера"
        else -> "$days дн. назад"
    }
}

@Composable
private fun NextWorkoutCard(
    item: WorkoutWithExercises,
    durationMillis: Long?,
    onStart: () -> Unit,
    onMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Accent, Color(0xFF9BE01E))))
            .padding(16.dp)
    ) {
        IconButton(
            onClick = onMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Действия", tint = OnAccent)
        }
        Column {
            Text(
                text = "СЛЕДУЮЩАЯ",
                style = MaterialTheme.typography.labelSmall,
                color = OnAccent.copy(alpha = 0.7f)
            )
            Text(
                text = item.workout.name,
                style = MaterialTheme.typography.headlineMedium,
                color = OnAccent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = metaLine(item, durationMillis),
                style = MaterialTheme.typography.bodySmall,
                color = OnAccent.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp)
            )
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.background)
                    .combinedClickable(onClick = onStart),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "НАЧАТЬ",
                    color = Accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun WorkoutRow(
    item: WorkoutWithExercises,
    position: Int?,
    durationMillis: Long?,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (position != null) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.workout.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metaLine(item, durationMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = subtitleFor(item.workout.lastUseAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onMenu, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Действия",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun metaLine(item: WorkoutWithExercises, durationMillis: Long?): String {
    val exercises = "${item.exercises.size} упр"
    return if (durationMillis != null) {
        "$exercises · ${DateFormatter.formatDurationCompact(durationMillis)}"
    } else {
        exercises
    }
}
```

- [ ] **Step 2: Убрать `onLongClick` из `NavGraph`**

В `NavGraph.kt` в блоке `composable(Screen.Workouts.route)` удалить целиком параметр с заглушкой:

```kotlin
                onLongClick = {
                    // TODO
                },
```

Остальные колбэки не меняются: экран по-прежнему отдаёт наружу `WorkoutEntity`.

- [ ] **Step 3: Проверить сборку и тесты**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Если падает на `Icons.Default.FitnessCenter` или `SkipNext` — они из `material-icons-extended`, зависимость уже подключена (`app/build.gradle.kts:95`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt
git commit -m "feat: главный экран показывает очередь ротации"
```

---

## Task 15: Медиа-кнопки — только запуск Яндекс Музыки

Кнопки prev/play-pause/next шлют `KeyEvent` через `AudioManager` без обратной связи: отсюда задержка и статичная иконка воспроизведения. Они удаляются. Настоящая замена — `MediaController` поверх `MediaSessionManager` — требует разрешения на доступ к уведомлениям и идёт отдельной спекой.

**Files:**
- Rewrite: `app/src/main/java/ru/hopes/workouttimer/presentation/components/SystemMediaController.kt`
- Delete: `app/src/main/java/ru/hopes/workouttimer/presentation/components/MediaButtonManager.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: `YandexMusicButton(modifier: Modifier = Modifier)` — иконка 48dp, запускает приложение или его страницу в Google Play.

- [ ] **Step 1: Проверить, что `MediaButtonManager` действительно никем не используется**

Run: `grep -rn "MediaButtonManager" app/src | grep -v "components/MediaButtonManager.kt"`
Expected: пустой вывод. Если что-то нашлось — не удалять файл, разобраться с вызывающей стороной.

- [ ] **Step 2: Переписать `SystemMediaController.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.components

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ru.hopes.workouttimer.R

/**
 * Кнопка запуска плеера. Управление воспроизведением отсюда убрано:
 * AudioManager.dispatchMediaKeyEvent() — односторонняя отправка, поэтому
 * кнопки срабатывали с задержкой, а иконка play/pause никогда не меняла вид.
 */
@Composable
fun YandexMusicButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            val packageName = "ru.yandex.music"
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                    )
                } catch (e: Exception) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=$packageName".toUri()
                        )
                    )
                }
            }
        },
        modifier = modifier.size(58.dp)
    ) {
        Icon(
            modifier = Modifier.size(44.dp),
            painter = painterResource(R.drawable.yandex_icon_pain),
            contentDescription = "Открыть плеер",
            tint = Color.Unspecified
        )
    }
}
```

- [ ] **Step 3: Удалить `MediaButtonManager.kt`**

```bash
rm app/src/main/java/ru/hopes/workouttimer/presentation/components/MediaButtonManager.kt
```

- [ ] **Step 4: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL в `WorkoutExecutionScreen.kt:195` — `SystemMediaControllerCompat` больше нет. Ожидаемо: экран переписывается в Task 16. Чтобы шаг закрывался собираемым, временно заменить вызов на `YandexMusicButton()` и поправить импорт.

- [ ] **Step 5: Проверить сборку повторно**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/ru/hopes/workouttimer/presentation/components app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt
git commit -m "refactor: убирает нерабочие медиа-кнопки, оставляет запуск плеера"
```

---

## Task 16: Экран выполнения

Экран переписывается целиком. `WorkoutExecutionViewModel` **не трогается**: все состояния, таймеры и сохранение сессии остаются как есть. Меняется только отрисовка: чип выбора упражнения вместо выпадающего списка, плитки вместо строк, кольцо вместо `CircularProgressIndicator`, полноэкранное «Готово» вместо диалога.

**Files:**
- Rewrite: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt`

**Interfaces:**
- Consumes: `StatTile`, `EmptyState`, `PrimaryButton`, `EyebrowLabel` (Task 3), `AppBottomSheet` (Task 5), `RestRing`, `ProgressSegments` (Task 6), `YandexMusicButton` (Task 15), `formatDurationCompact` (Task 7).
- Produces: `WorkoutExecutionScreen(viewModel, onExerciseCompleted: () -> Unit, workoutId: Int)` — сигнатура не меняется, `NavGraph` править не нужно.

- [ ] **Step 1: Написать новый `WorkoutExecutionScreen.kt`**

Публичные члены ViewModel, на которые опирается экран: `uiState`, `exercises`, `workoutName`, `totalExercises`, `currentExerciseNumber`, `isLastSetOfWorkout`, `loadWorkout(id)`, `onExerciseFinished()`, `skipRest()`, `moveToSelectedExercise(exercise)`, `updateExerciseNote(id, note)`.

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.components.YandexMusicButton
import ru.hopes.workouttimer.presentation.ui.components.AppBottomSheet
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.EyebrowLabel
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.components.ProgressSegments
import ru.hopes.workouttimer.presentation.ui.components.RestRing
import ru.hopes.workouttimer.presentation.ui.components.StatTile
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.toCorrectNum

@Composable
fun WorkoutExecutionScreen(
    viewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onExerciseCompleted: () -> Unit,
    workoutId: Int
) {
    val uiState by viewModel.uiState.collectAsState()

    var showNoteDialog by remember { mutableStateOf(false) }
    var currentEditingExercise by remember { mutableStateOf<Exercise?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }

    // Уходить без подтверждения нечего терять только в Loading/Error/Finished:
    // в Finished сессия уже сохранена, в остальных двух её ещё нет.
    val hasUnsavedProgress =
        uiState is WorkoutExecutionState.Active || uiState is WorkoutExecutionState.Rest

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    BackHandler(enabled = hasUnsavedProgress) { showExitDialog = true }

    val finishedState = uiState as? WorkoutExecutionState.Finished
    if (finishedState != null) {
        FinishedContent(
            workoutName = viewModel.workoutName,
            durationMillis = finishedState.durationMillis,
            onDone = onExerciseCompleted
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val currentState = uiState
            val currentExercise = when (currentState) {
                is WorkoutExecutionState.Active -> currentState.exercise
                is WorkoutExecutionState.Rest -> currentState.exercise
                else -> null
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (hasUnsavedProgress) showExitDialog = true else onExerciseCompleted()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (currentExercise != null) {
                        ExerciseChip(
                            name = currentExercise.name,
                            position = viewModel.currentExerciseNumber,
                            total = viewModel.totalExercises,
                            onClick = { showExercisePicker = true }
                        )
                    } else {
                        Text(
                            text = viewModel.workoutName.ifEmpty { "Тренировка" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Box(modifier = Modifier.size(48.dp))
            }

            if (currentExercise != null && viewModel.totalExercises > 0) {
                ProgressSegments(
                    total = viewModel.totalExercises,
                    currentIndex = viewModel.currentExerciseNumber - 1,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentState) {
                    is WorkoutExecutionState.Loading -> LoadingContent()

                    is WorkoutExecutionState.Error -> EmptyState(
                        icon = Icons.Default.ErrorOutline,
                        title = "Не удалось загрузить",
                        subtitle = currentState.message,
                        actionText = "Повторить",
                        onAction = { viewModel.loadWorkout(workoutId) }
                    )

                    is WorkoutExecutionState.Active -> ActiveContent(
                        state = currentState,
                        onEditNote = {
                            currentEditingExercise = it
                            showNoteDialog = true
                        }
                    )

                    is WorkoutExecutionState.Rest -> RestContent(
                        state = currentState,
                        onEditNote = {
                            currentEditingExercise = it
                            showNoteDialog = true
                        }
                    )

                    is WorkoutExecutionState.Finished -> Unit
                }
            }

            if (currentState is WorkoutExecutionState.Active ||
                currentState is WorkoutExecutionState.Rest
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ScreenPadding, end = ScreenPadding, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    YandexMusicButton()
                    if (currentState is WorkoutExecutionState.Active) {
                        PrimaryButton(
                            text = "Закончить подход",
                            onClick = {
                                if (viewModel.isLastSetOfWorkout) {
                                    showFinishDialog = true
                                } else {
                                    viewModel.onExerciseFinished()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        PrimaryButton(
                            text = "Пропустить отдых",
                            onClick = { viewModel.skipRest() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showExercisePicker) {
        AppBottomSheet(onDismiss = { showExercisePicker = false }) {
            Text(
                text = "Упражнения",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
            )
            viewModel.exercises.forEachIndexed { index, exercise ->
                val isCurrent = index + 1 == viewModel.currentExerciseNumber
                val isDone = index + 1 < viewModel.currentExerciseNumber
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.moveToSelectedExercise(exercise)
                            showExercisePicker = false
                        }
                        .padding(horizontal = ScreenPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    currentEditingExercise?.let { exercise ->
        if (showNoteDialog) {
            NoteEditDialog(
                exercise = exercise,
                onDismiss = { showNoteDialog = false },
                onSave = { note ->
                    viewModel.updateExerciseNote(exercise.id, note)
                    showNoteDialog = false
                }
            )
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Выйти из тренировки?") },
            text = { Text("Прогресс не будет сохранён.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExerciseCompleted()
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Завершить тренировку?") },
            text = { Text("Это последний подход. Тренировка будет сохранена в историю.") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    viewModel.onExerciseFinished()
                }) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ExerciseChip(
    name: String,
    position: Int,
    total: Int,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = " · $position/$total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ActiveContent(
    state: WorkoutExecutionState.Active,
    onEditNote: (Exercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EyebrowLabel(
            text = "Подход ${state.currentSet} из ${state.totalSets}",
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = state.exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                value = state.weight.toCorrectNum(),
                unit = "кг",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = state.reps.toString(),
                unit = "повт",
                modifier = Modifier.weight(1f)
            )
        }
        NoteBlock(
            note = state.exercise.note,
            onEdit = { onEditNote(state.exercise) },
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun RestContent(
    state: WorkoutExecutionState.Rest,
    onEditNote: (Exercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EyebrowLabel(
            text = "Отдых · далее подход ${state.currentSet}",
            modifier = Modifier.padding(top = 18.dp)
        )
        RestRing(
            timeLeftMillis = state.restTimeMillis,
            totalTimeMillis = state.totalRestTimeMillis,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = state.exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = "${state.exercise.weight.toCorrectNum()} кг · ${state.exercise.reps} повторений",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        NoteBlock(
            note = state.exercise.note,
            onEdit = { onEditNote(state.exercise) },
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun NoteBlock(
    note: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ЗАМЕТКА",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Редактировать заметку",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = note.ifBlank { "Нет заметок" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (note.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun FinishedContent(
    workoutName: String,
    durationMillis: Long,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EyebrowLabel(text = "Готово")
        Text(
            text = DateFormatter.formatDurationCompact(durationMillis),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = workoutName.ifEmpty { "Тренировка" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Box(modifier = Modifier.height(36.dp))
        PrimaryButton(text = "На главную", onClick = onDone)
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NoteEditDialog(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(exercise.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заметка к упражнению") },
        text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите заметку...") },
                minLines = 3,
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(noteText) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
```

- [ ] **Step 2: Сверить имена полей состояний с ViewModel**

Run: `grep -n "data class Active\|data class Rest\|data class Finished\|data class Error" -A 8 app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
Expected: поля `Active.exercise/currentSet/totalSets/weight/reps`, `Rest.exercise/currentSet/restTimeMillis/totalRestTimeMillis`, `Finished.durationMillis`, `Error.message`. Если имя отличается — поправить в экране, ViewModel не менять.

- [ ] **Step 3: Проверить сборку и тесты**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. `WorkoutExecutionViewModelTest` должен пройти без единой правки — если он упал, значит ViewModel случайно задет, откатить.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt
git commit -m "feat: переписывает экран выполнения на новую дизайн-систему"
```

---

## Task 17: `CreateWorkoutViewModel` — перестановка и несохранённые изменения

Добавляется `MoveExercise`, удаляется `UpdateExerciseWeight` (барабан отдаёт `Double`, строковый ввод больше не нужен), появляется флаг несохранённых изменений для подтверждения выхода.

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModelTest.kt`

**Interfaces:**
- Consumes: `lastUseAt = 0L` из Task 12.
- Produces:
  - `CreateWorkoutCommand.MoveExercise(from: Int, to: Int)`
  - `ExerciseItem(id, name, weight: Double, sets, reps, restTimeSeconds, note)` — поле `weightStr` удалено
  - `CreateWorkoutState.hasUnsavedChanges: Boolean`

- [ ] **Step 1: Написать падающие тесты**

Дописать в `CreateWorkoutViewModelTest`:

```kotlin
    @Test
    fun `перестановка меняет порядок упражнений в состоянии`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val ids = vm.state.value.exercises.map { it.id }

        vm.processCommand(CreateWorkoutCommand.MoveExercise(from = 0, to = 2))

        assertEquals(
            listOf(ids[1], ids[2], ids[0]),
            vm.state.value.exercises.map { it.id }
        )
    }

    @Test
    fun `перестановка вверх работает симметрично`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val ids = vm.state.value.exercises.map { it.id }

        vm.processCommand(CreateWorkoutCommand.MoveExercise(from = 2, to = 0))

        assertEquals(
            listOf(ids[2], ids[0], ids[1]),
            vm.state.value.exercises.map { it.id }
        )
    }

    @Test
    fun `перестановка вне диапазона не ломает список`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val ids = vm.state.value.exercises.map { it.id }

        vm.processCommand(CreateWorkoutCommand.MoveExercise(from = 0, to = 5))

        assertEquals(ids, vm.state.value.exercises.map { it.id })
    }

    @Test
    fun `после перестановки order сохраняется непрерывным от единицы`() = runTest(dispatcher) {
        val add = mockk<AddWorkoutUseCase>(relaxed = true)
        val vm = viewModel(add = add)
        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))
        repeat(3) { vm.processCommand(CreateWorkoutCommand.AddExercise()) }
        vm.state.value.exercises.forEachIndexed { index, item ->
            vm.processCommand(
                CreateWorkoutCommand.UpdateExercise(item.id, item.copy(name = "Упр $index"))
            )
        }
        vm.processCommand(CreateWorkoutCommand.MoveExercise(from = 0, to = 2))
        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        val saved = slot<Workout>()
        coVerify { add(capture(saved)) }
        assertEquals(listOf(1, 2, 3), saved.captured.exercises.map { it.order })
        assertEquals("Упр 1", saved.captured.exercises.first().name)
    }

    @Test
    fun `свежезагруженное состояние не считается изменённым`() = runTest(dispatcher) {
        val vm = viewModel()

        assertEquals(false, vm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `изменение имени помечает состояние как несохранённое`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))

        assertEquals(true, vm.state.value.hasUnsavedChanges)
    }
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "*CreateWorkoutViewModelTest*"`
Expected: FAIL — `MoveExercise` и `hasUnsavedChanges` не существуют.

- [ ] **Step 3: Добавить команду и убрать строковый вес**

В `CreateWorkoutViewModel.kt`:

а) В `sealed interface CreateWorkoutCommand` удалить строку `data class UpdateExerciseWeight(...)` и добавить:

```kotlin
    data class MoveExercise(val from: Int, val to: Int) : CreateWorkoutCommand
```

б) В `data class ExerciseItem` удалить поле `val weightStr: String = weight.toString(),`.

в) Удалить ветку `is CreateWorkoutCommand.UpdateExerciseWeight -> { ... }` из `processCommand` целиком (`:69-80`).

г) В ветке `AddExercise` удалить аргумент `weightStr = "",`.

д) Добавить в `processCommand` новую ветку:

```kotlin
            is CreateWorkoutCommand.MoveExercise -> {
                _state.update { state ->
                    val items = state.exercises
                    if (command.from !in items.indices || command.to !in items.indices) {
                        return@update state
                    }
                    val reordered = items.toMutableList().apply {
                        add(command.to, removeAt(command.from))
                    }
                    // order здесь не трогаем: он присваивается при сохранении
                    // через mapIndexed, поэтому порядок списка и есть порядок упражнений.
                    state.copy(exercises = reordered)
                }
            }
```

- [ ] **Step 4: Добавить отслеживание несохранённых изменений**

В теле класса, рядом с `editingLastUseAt`:

```kotlin
    /** Слепок состояния после загрузки — с ним сравнивается текущее при выходе. */
    private var savedSnapshot: CreateWorkoutState = CreateWorkoutState()
```

В `data class CreateWorkoutState` добавить поле и производный флаг:

```kotlin
data class CreateWorkoutState(
    val workoutName: String = "",
    val exercises: List<ExerciseItem> = emptyList(),
    val isFinished: Boolean = false,
    val hasUnsavedChanges: Boolean = false
) {
    val isSaveEnabled: Boolean
        get() = workoutName.isNotBlank() && exercises.any { it.name.isNotBlank() }
}
```

В конце `processCommand`, после `when`, пересчитать флаг — кроме команд `Save` и `Back`:

```kotlin
        if (command !is CreateWorkoutCommand.Save && command !is CreateWorkoutCommand.Back) {
            _state.update { current ->
                val changed = current.workoutName != savedSnapshot.workoutName ||
                        current.exercises != savedSnapshot.exercises
                if (current.hasUnsavedChanges == changed) current else current.copy(hasUnsavedChanges = changed)
            }
        }
```

В `loadWorkout`, сразу после того как состояние заполнено загруженной тренировкой, зафиксировать слепок:

```kotlin
        savedSnapshot = _state.value
```

- [ ] **Step 5: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*CreateWorkoutViewModelTest*"`
Expected: PASS, 7 тестов.

- [ ] **Step 6: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL в `CreateWorkoutScreen.kt` — экран ссылается на `weightStr` и `UpdateExerciseWeight`. Ожидаемо, экран переписывается в Task 18.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt app/src/test/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModelTest.kt
git commit -m "feat: перестановка упражнений и флаг несохранённых изменений"
```

---

## Task 18: Редактор тренировки — строки, перетаскивание, лист с барабанами

Пять тесных текстовых полей заменяются строкой со сводкой и нижним листом с барабанами. Клавиатура остаётся только у названия тренировки и названия упражнения.

**Files:**
- Rewrite: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutScreen.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `MoveExercise`, `hasUnsavedChanges` (Task 17), `WheelPicker`/`WheelRow`/`wheelIndexOfNearest` (Task 4), `AppBottomSheet` (Task 5), `PrimaryButton` (Task 3).
- Produces: `CreateWorkoutScreen(modifier, viewModel, onFinished, workoutId)` — сигнатура не меняется.

Сетки барабанов (константы файла):

```kotlin
private val WeightValues = generateSequence(0.0) { it + 0.25 }.takeWhile { it <= 300.0 }.toList()
private val SetsValues = (1..10).toList()
private val RepsValues = (1..50).toList()
private val RestValues = (15..1800 step 15).toList() // секунды: 0:15 … 30:00
```

Шаг веса 0.25 выбран по реальным данным: встречаются 7, 8, 13, 16, 48, 61, 88 (блочные тренажёры) и 1.25-блины. Шаг 2.5 молча переписал бы такие значения при первом сохранении.

- [ ] **Step 1: Написать новый `CreateWorkoutScreen.kt`**

```kotlin
package ru.hopes.workouttimer.presentation.screen.creation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.presentation.ui.components.AppBottomSheet
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.components.SectionHeader
import ru.hopes.workouttimer.presentation.ui.components.WheelPicker
import ru.hopes.workouttimer.presentation.ui.components.WheelRow
import ru.hopes.workouttimer.presentation.ui.components.wheelIndexOfNearest
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.utils.toCorrectNum
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

private val WeightValues = generateSequence(0.0) { it + 0.25 }.takeWhile { it <= 300.0 }.toList()
private val SetsValues = (1..10).toList()
private val RepsValues = (1..50).toList()
private val RestValues = (15..1800 step 15).toList()

private fun formatRest(seconds: Int): String =
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

@Composable
fun CreateWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: CreateWorkoutViewModel = hiltViewModel(),
    onFinished: () -> Unit,
    workoutId: Int? = null
) {
    val state by viewModel.state.collectAsState()
    var editingId by remember { mutableStateOf<Int?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        workoutId?.let { viewModel.loadWorkout(it) }
    }

    if (state.isFinished) {
        onFinished()
        return
    }

    // Перетаскивание можно потерять одним тапом «назад» — подтверждаем выход.
    BackHandler(enabled = state.hasUnsavedChanges) { showExitDialog = true }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.processCommand(
            CreateWorkoutCommand.MoveExercise(
                from = from.index - HEADER_ITEMS,
                to = to.index - HEADER_ITEMS
            )
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.processCommand(CreateWorkoutCommand.AddExercise())
                    editingId = null // лист откроется на последнем добавленном ниже
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    "Упражнение",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (state.hasUnsavedChanges) {
                        showExitDialog = true
                    } else {
                        viewModel.processCommand(CreateWorkoutCommand.Back)
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (workoutId == null) "Новая тренировка" else "Редактирование",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.processCommand(CreateWorkoutCommand.Save) },
                    enabled = state.isSaveEnabled
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Сохранить",
                        tint = if (state.isSaveEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(CardSpacing)
            ) {
                item(key = "name") {
                    OutlinedTextField(
                        value = state.workoutName,
                        onValueChange = {
                            viewModel.processCommand(CreateWorkoutCommand.ChangeWorkoutName(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название тренировки") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                }
                item(key = "section") {
                    SectionHeader(
                        text = "Упражнения · ${state.exercises.size}",
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                itemsIndexed(state.exercises, key = { _, item -> item.id }) { _, item ->
                    ReorderableItem(reorderState, key = item.id) { _ ->
                        ExerciseRow(
                            item = item,
                            onClick = { editingId = item.id },
                            dragHandle = {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Переставить",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .draggableHandle()
                                )
                            }
                        )
                    }
                }
                if (state.exercises.isEmpty()) {
                    item(key = "empty") {
                        Box(modifier = Modifier.height(220.dp)) {
                            EmptyState(
                                icon = Icons.Default.FitnessCenter,
                                title = "Упражнений пока нет",
                                subtitle = "Добавьте первое кнопкой внизу"
                            )
                        }
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val item = state.exercises.firstOrNull { it.id == id }
        if (item == null) {
            editingId = null
        } else {
            ExerciseEditSheet(
                item = item,
                onDismiss = { editingId = null },
                onChange = { updated ->
                    viewModel.processCommand(CreateWorkoutCommand.UpdateExercise(id, updated))
                },
                onDelete = {
                    viewModel.processCommand(CreateWorkoutCommand.RemoveExercise(id))
                    editingId = null
                }
            )
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Выйти без сохранения?") },
            text = { Text("Изменения, включая порядок упражнений, будут потеряны.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.processCommand(CreateWorkoutCommand.Back)
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Отмена") }
            }
        )
    }
}

/** Поле имени + заголовок секции идут перед списком упражнений. */
private const val HEADER_ITEMS = 2

@Composable
private fun ExerciseRow(
    item: ExerciseItem,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        dragHandle()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.ifBlank { "Без названия" },
                style = MaterialTheme.typography.titleMedium,
                color = if (item.name.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.weight.toCorrectNum()} кг · ${item.sets}×${item.reps} · отдых ${formatRest(item.restTimeSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExerciseEditSheet(
    item: ExerciseItem,
    onDismiss: () -> Unit,
    onChange: (ExerciseItem) -> Unit,
    onDelete: () -> Unit
) {
    var weightIndex by remember(item.id) {
        mutableIntStateOf(wheelIndexOfNearest(WeightValues, item.weight))
    }
    var setsIndex by remember(item.id) {
        mutableIntStateOf(SetsValues.indexOf(item.sets).coerceAtLeast(0))
    }
    var repsIndex by remember(item.id) {
        mutableIntStateOf(RepsValues.indexOf(item.reps).coerceAtLeast(0))
    }
    var restIndex by remember(item.id) {
        mutableIntStateOf(
            wheelIndexOfNearest(RestValues.map { it.toDouble() }, item.restTimeSeconds.toDouble())
        )
    }

    AppBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
            OutlinedTextField(
                value = item.name,
                onValueChange = { onChange(item.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название упражнения") },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            WheelRow(modifier = Modifier.padding(vertical = 12.dp)) {
                WheelPicker(
                    items = WeightValues,
                    selectedIndex = weightIndex,
                    onSelected = {
                        weightIndex = it
                        onChange(item.copy(weight = WeightValues[it]))
                    },
                    label = "кг",
                    format = { it.toCorrectNum() }
                )
                WheelPicker(
                    items = SetsValues,
                    selectedIndex = setsIndex,
                    onSelected = {
                        setsIndex = it
                        onChange(item.copy(sets = SetsValues[it]))
                    },
                    label = "подх",
                    format = { it.toString() }
                )
                WheelPicker(
                    items = RepsValues,
                    selectedIndex = repsIndex,
                    onSelected = {
                        repsIndex = it
                        onChange(item.copy(reps = RepsValues[it]))
                    },
                    label = "повт",
                    format = { it.toString() }
                )
                WheelPicker(
                    items = RestValues,
                    selectedIndex = restIndex,
                    onSelected = {
                        restIndex = it
                        onChange(item.copy(restTimeSeconds = RestValues[it]))
                    },
                    label = "отдых",
                    format = { formatRest(it) }
                )
            }

            OutlinedTextField(
                value = item.note,
                onValueChange = { onChange(item.copy(note = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Заметка") },
                minLines = 2,
                shape = MaterialTheme.shapes.small
            )

            PrimaryButton(
                text = "Готово",
                onClick = onDismiss,
                modifier = Modifier.padding(top = 14.dp)
            )
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text("Удалить упражнение", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
```

- [ ] **Step 2: Открывать лист сразу после добавления упражнения**

В обработчике FAB заменить тело на:

```kotlin
                onClick = {
                    viewModel.processCommand(CreateWorkoutCommand.AddExercise())
                    // AddExercise кладёт новое упражнение в конец — открываем его лист.
                    editingId = viewModel.state.value.exercises.lastOrNull()?.id
                },
```

- [ ] **Step 3: Если `reorderable` не подключилась (см. Task 1, Step 4)**

Заменить `rememberReorderableLazyListState` / `ReorderableItem` / `draggableHandle()` на ручную реализацию: `Modifier.pointerInput` с `detectDragGesturesAfterLongPress` на иконке ручки, накопление смещения в `remember { mutableFloatStateOf(0f) }`, вычисление целевого индекса делением смещения на высоту строки и вызов той же `CreateWorkoutCommand.MoveExercise`. Команда и её тесты от этого не меняются.

- [ ] **Step 4: Проверить сборку и тесты**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutScreen.kt
git commit -m "feat: редактор тренировки на строках, барабанах и перетаскивании"
```

---

## Task 19: История и экспорт/импорт

Два оставшихся экрана приводятся к общему языку. Статистика и графики — отдельная спека, здесь только вёрстка.

**Files:**
- Rewrite: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryScreen.kt`
- Rewrite: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/exportImport/ExportImportScreen.kt`

**Interfaces:**
- Consumes: `EmptyState` (Task 3), `formatDurationCompact` (Task 7).
- Produces: сигнатуры экранов не меняются — `NavGraph` править не нужно.

- [ ] **Step 1: Переписать тело `WorkoutHistoryScreen`**

Заменить содержимое `Scaffold` (шапка остаётся, но `TopAppBar` меняется на обычный `Row`, как на других экранах) — список строк с разделителями становится карточками:

```kotlin
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
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
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = state.workoutName.ifEmpty { "История" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (state.sessions.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "История пуста",
                    subtitle = "Завершите тренировку — она появится здесь"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(CardSpacing)
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = DateFormatter.formatSessionDateTime(session.finishedAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = DateFormatter.formatDurationCompact(session.durationMillis),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
```

Импорты, которые понадобятся: `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.*`, `androidx.compose.foundation.lazy.items`, `androidx.compose.material.icons.automirrored.filled.ArrowBack`, `androidx.compose.material.icons.filled.History`, `androidx.compose.ui.draw.clip`, `ru.hopes.workouttimer.presentation.ui.components.EmptyState`, `ru.hopes.workouttimer.presentation.ui.theme.CardSpacing`, `ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding`. Удалить импорт `HorizontalDivider` и функцию `SessionRow`.

- [ ] **Step 2: Переписать тело `ExportImportScreen`**

Две кнопки и абзац текста заменяются карточками-действиями. Шапка получает стрелку «назад»: `onNavigateBack` в экран уже передаётся из `NavGraph.kt:130`, но не использовался.

Заменить содержимое `Scaffold` на:

```kotlin
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
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Экспорт и импорт",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val busy = uiState is ExportImportUiState.Loading

            Column(
                modifier = Modifier.padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(CardSpacing)
            ) {
                ActionCard(
                    icon = Icons.Default.Upload,
                    title = "Экспортировать тренировки",
                    description = "Сохраняет все тренировки и упражнения в JSON-файл.",
                    enabled = !busy,
                    onClick = { viewModel.exportWorkouts() }
                )
                ActionCard(
                    icon = Icons.Default.Download,
                    title = "Импортировать тренировки",
                    description = "Добавляет тренировки из файла. Дубликаты переименовываются автоматически.",
                    enabled = !busy,
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
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
                            text = "Обработка...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }
    }
```

И добавить в тот же файл:

```kotlin
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
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
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
```

Логика лаунчера импорта и `LaunchedEffect` со снекбарами (`ExportImportScreen.kt:53-87`) не меняется.

- [ ] **Step 3: Проверить сборку и тесты**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory app/src/main/java/ru/hopes/workouttimer/presentation/screen/exportImport
git commit -m "feat: переводит историю и экспорт на новую дизайн-систему"
```

---

## Task 20: Виджет на палитру Dark Athletic

`QuickStartWidget.kt:65` использует голый `GlanceTheme { }` — то есть динамические системные цвета. Разметка тянет `GlanceTheme.colors.widgetBackground` (`:78`), `.onSurface` (`:86`), `.onSurfaceVariant` (`:100`), поэтому нужен собственный `ColorProviders`, а не правка литералов на местах.

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/widget/WidgetColors.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/widget/QuickStartWidget.kt:65`

**Interfaces:**
- Consumes: токены из Task 2.
- Produces: `WidgetColorScheme: ColorProviders`.

- [ ] **Step 1: Создать `WidgetColors.kt`**

Glance требует пару «light/dark» для каждой роли. Приложение всегда тёмное, поэтому обе половины одинаковы.

```kotlin
package ru.hopes.workouttimer.presentation.widget

import androidx.glance.material3.ColorProviders
import androidx.compose.material3.darkColorScheme
import ru.hopes.workouttimer.presentation.ui.theme.Accent
import ru.hopes.workouttimer.presentation.ui.theme.Background
import ru.hopes.workouttimer.presentation.ui.theme.OnAccent
import ru.hopes.workouttimer.presentation.ui.theme.OutlineDark
import ru.hopes.workouttimer.presentation.ui.theme.SurfaceDark
import ru.hopes.workouttimer.presentation.ui.theme.TextPrimary
import ru.hopes.workouttimer.presentation.ui.theme.TextSecondary

private val WidgetScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = Background,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark
)

/** Виджет не следует системной теме: приложение всегда тёмное. */
val WidgetColorScheme = ColorProviders(light = WidgetScheme, dark = WidgetScheme)
```

- [ ] **Step 2: Подставить схему в виджет**

В `QuickStartWidget.kt` заменить `GlanceTheme {` на:

```kotlin
            GlanceTheme(colors = WidgetColorScheme) {
```

- [ ] **Step 3: Проверить, что `glance-material3` доступен**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Если `androidx.glance.material3.ColorProviders` не резолвится — артефакт `glance-material3` не подключён. Тогда добавить в `libs.versions.toml`:

```toml
androidx-glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
```

и в `app/build.gradle.kts`:

```kotlin
    implementation(libs.androidx.glance.material3)
```

после чего повторить сборку.

- [ ] **Step 4: Проверить тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/widget gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: переводит виджет на палитру Dark Athletic"
```

---

## Task 21: Удаление мёртвого кода

Остатки, которые нечем больше держать: use case без потребителей, функция подсветки «заброшенных» карточек, неиспользуемая `Title`, шаблонные цвета.

**Files:**
- Delete: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetAllWorkoutsUseCase.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/utils/DateFormatter.kt:30-36`
- Modify: `app/src/test/java/ru/hopes/workouttimer/presentation/utils/DateFormatterTest.kt:77-95`
- Modify: `app/src/main/res/values/colors.xml`

**Interfaces:**
- Consumes: всё предыдущее.
- Produces: ничего нового.

- [ ] **Step 1: Убедиться, что `GetAllWorkoutsUseCase` больше никем не используется**

Run: `grep -rn "GetAllWorkoutsUseCase" app/src | grep -v "GetAllWorkoutsUseCase.kt:"`
Expected: пустой вывод. Если что-то нашлось — разобраться с вызывающей стороной, файл не удалять.

- [ ] **Step 2: Удалить файл**

```bash
rm app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetAllWorkoutsUseCase.kt
```

`dao.getAllWorkouts()` и `repo.getAllWorkouts()` остаются: ими пользуется экспорт (`ExportImportRepositoryImpl.kt:90`, `:152`).

- [ ] **Step 3: Убедиться, что `isStaleWorkout` больше не вызывается**

Run: `grep -rn "isStaleWorkout" app/src/main`
Expected: только объявление в `DateFormatter.kt`. Если экран всё ещё его зовёт — значит Task 14 выполнен не полностью.

- [ ] **Step 4: Удалить `isStaleWorkout` и его тесты**

В `DateFormatter.kt` удалить константу `milesIn13Days` и функцию `isStaleWorkout` целиком (`:30-36`). В `DateFormatterTest.kt` удалить три теста `isStaleWorkout` (`:77-95`) и ставший ненужным импорт.

- [ ] **Step 5: Почистить `colors.xml`**

Удалить `purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`, если они ещё остались после Task 2. Оставить `black`, `white`, `splash_screen`, `app_background` — их могут тянуть `ic_launcher_background.xml` и манифест.

Run: `grep -rn "purple_200\|purple_500\|purple_700\|teal_200\|teal_700" app/src`
Expected: пустой вывод перед удалением.

- [ ] **Step 6: Прогнать всё**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные.

- [ ] **Step 7: Commit**

```bash
git add -A app/src
git commit -m "chore: удаляет мёртвый код после редизайна"
```

---

## Task 22: Проверка на устройстве

Последний гейт. Всё собирается и тесты зелёные — теперь убедиться, что приложением можно пользоваться в зале.

**Files:** нет.

**Interfaces:**
- Consumes: все предыдущие задачи.
- Produces: готовую к слиянию ветку.

- [ ] **Step 1: Собрать и поставить на устройство**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, приложение на телефоне.

- [ ] **Step 2: Пройти сценарий очереди**

1. Открыть приложение — сверху «СЛЕДУЮЩАЯ» с тренировкой, которую не делали дольше всех.
2. Проверить, что ниже тренировки идут от давних к недавним, с номерами позиций.
3. Открыть ⋮ у любой карточки → «Пропустить» → тренировка уехала в конец, сверху встала следующая, внизу снекбар.
4. Нажать «Отменить» → порядок вернулся.

- [ ] **Step 3: Пройти сценарий тренировки**

1. Нажать «НАЧАТЬ», проверить сегменты прогресса и чип с именем упражнения.
2. Тапнуть чип → лист со списком → перейти к другому упражнению → вернуться.
3. Закончить подход, посмотреть кольцо отдыха: время и кольцо идут синхронно, цифры не прыгают по ширине.
4. Дойти до последнего подхода → подтвердить завершение → увидеть полноэкранное «Готово» со временем.
5. Вернуться на главную: тренировка ушла в конец очереди, длительность видна на карточке.

- [ ] **Step 4: Пройти сценарий редактора**

1. Открыть «Редактировать», поменять вес барабаном — проверить, что значения вроде 61 и 88 доступны.
2. Перетащить упражнение за ручку ≡.
3. Нажать «назад» → появилось подтверждение выхода → «Отмена» → сохранить ✓.
4. Открыть заново и убедиться, что порядок и вес сохранились.

- [ ] **Step 5: Проверить виджет**

Добавить виджет на домашний экран: первой строкой должна стоять «следующая» тренировка, цвета — тёмные с лаймом. Тап открывает экран выполнения напрямую.

- [ ] **Step 6: Создать новую тренировку**

Создать тренировку с одним упражнением и убедиться, что после сохранения она встала **первой** в очереди с подписью «ещё не делали».

- [ ] **Step 7: Зафиксировать результат**

Если всё прошло — ветка готова к слиянию. Использовать skill `superpowers:finishing-a-development-branch`.

Если что-то не так — не чинить на месте: записать наблюдение, вернуться к соответствующей задаче и пройти её цикл заново.

---

## Заметки для исполнителя

**Превью.** Спека требует `@Preview` для каждого компонента и каждого состояния экрана — это основной способ проверять вёрстку, не собирая APK. В коде задач превью показаны только для `PrimaryButton` и `StatTile`, чтобы не раздувать план; остальные добавляются по тому же образцу в конце соответствующей задачи, до коммита:

- Task 3: `EmptyState` (с кнопкой и без).
- Task 4: `WheelRow` с четырьмя барабанами.
- Task 5: `ActionSheet` с пятью пунктами, включая деструктивный.
- Task 6: `RestRing` на 100%, 50% и 0%; `ProgressSegments` при `total = 6, currentIndex = 1`.
- Task 14: очередь из шести тренировок; пустое состояние; состояние поиска.
- Task 16: `ActiveContent`, `RestContent`, `FinishedContent`.
- Task 18: `ExerciseRow` и `ExerciseEditSheet`.

Превью экранов делаются на приватных composable-функциях содержимого (`ActiveContent`, `WorkoutRow` и т.п.), а не на экране целиком: экран тянет `hiltViewModel()`, который в превью не поднимается.

**Чего не делать:**
- Не трогать `WorkoutExecutionViewModel` — весь таймер, подсчёт сессии и звук живут там и работают. Если кажется, что экран требует правки ViewModel, сначала перечитать её публичные члены.
- Не возвращать `dynamicColor` и светлую схему. Светлая тема придёт вместе с экраном настроек — отдельной спекой.
- Не добавлять правку веса по тапу на плитке. `StatTile` принимает `onClick`, но в этой ветке он всегда `null`.
- Не переносить текст экранов в `strings.xml` целиком — проект уже смешивает подходы, и массовый перенос размоет диф редизайна.

**Если задача упёрлась:**
- Сборка падает на несовпадении типа `WorkoutWithExercises` / `WorkoutEntity` — проверить, выполнены ли Tasks 9 и 13 до экранов.
- Тест `WorkoutExecutionViewModelTest` покраснел — значит ViewModel задета случайно, откатить её изменения.
- `reorderable` не резолвится — см. Task 18, Step 3: ручная реализация на том же `MoveExercise`.
