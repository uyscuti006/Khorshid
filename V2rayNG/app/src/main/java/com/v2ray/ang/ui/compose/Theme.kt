package com.v2ray.ang.ui.compose

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val AppDarkColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF252525),
    onSecondaryContainer = Color(0xFFBCC7DB),
    tertiary = Color(0xFF2ECC71),
    onTertiary = Color.Black,
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111111),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF252525),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF252525),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF252525),
    surfaceContainerHighest = Color(0xFF303030)
)

val AppLightColorScheme = lightColorScheme(
    primary = Color(0xFF1D70F5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E5DE),
    onSecondaryContainer = Color(0xFF1C1B1F),
    tertiary = Color(0xFF2ECC71),
    onTertiary = Color.White,
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFAF8F5),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8E5DE),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFE8E5DE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF8F5),
    surfaceContainer = Color(0xFFF5F3EE),
    surfaceContainerHigh = Color(0xFFE8E5DE),
    surfaceContainerHighest = Color(0xFFDDDAD2)
)

val colorPing = Color(0xFF2ECC71)
val colorPingRed = Color(0xFFE5484D)

val colorConfigType: Color
    @Composable
    get() = if (LocalDarkTheme.current) Color(0xFFADC6FF) else Color(0xFF1D70F5)

val colorFabActive = Color(0xFF1D70F5)
val colorFabInactiveLight = Color(0xFF9C9C9C)
val colorFabInactiveDark = Color(0xFF646464)
val dividerColorLight = Color(0xFFE8E5DE)
val dividerColorDark = Color(0xFF252525)

val AlertRed = Color(0xFFE53935)
val AlertRedDeep = Color(0xFFC62828)

val toastNormalBgLight = Color(0xB3353A3E)
val toastNormalBgDark = Color(0xB31F222E)
val toastSuccessBg = Color(0xB32ECC71)
val toastErrorBg = Color(0xB3E5484D)
val toastInfoBg = Color(0xB31D70F5)
val toastIconCircleBg = Color(0x33FFFFFF)
val toastTextColor = Color.White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    @Suppress("UNUSED_PARAMETER")
    fun setDynamicColorEnabled(enabled: Boolean) {
        // Dynamic color disabled - Khorshid uses its own color scheme
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppDarkColorScheme else AppLightColorScheme
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
