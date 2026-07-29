package com.v2ray.ang.ui.server

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.compose.FormDropdownField
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast

// 🎨 پلت رنگی یکدست با بقیه بخش‌های برنامه
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF545F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    background = Color(0xFFFDFCFE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFE),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E3EB),
    onSurfaceVariant = Color(0xFF43474E),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF232832),
    onSurfaceVariant = Color(0xFFC3C6CF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 📐 اشکال و انحناهای استاندارد
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)

class ServerVlessActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.VLESS

    @Composable
    override fun ScreenContent() {
        val options = rememberFieldOptions()
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig,
                browserDialerDefault = options.browserDialerOptions.firstOrNull() ?: "Disable"
            )
        }.apply {
            configType = EConfigType.VLESS
        }
        val flowOptions = stringArrayResource(R.array.flows).toList()

        val isDark = isSystemInDarkTheme()
        val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes
        ) {
            ServerEditorScaffold(
                title = serverConfigType.toString(),
                onSaveClick = { saveServer(uiState) }
            ) {
                item { CommonBasicFields(uiState) }
                item { VlessProtocolFields(uiState, flowOptions) }
                item { CommonNetworkFields(uiState, options) }
                item {
                    CommonStreamSecurityFields(
                        state = uiState,
                        options = options,
                        scope = scope,
                        buildProfileItem = { uiState.toProfileItem(initialConfig) }
                    )
                }
            }
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.password.isNullOrBlank()) {
            toast(R.string.server_lab_id)
            return false
        }
        return true
    }

    @Composable
    private fun VlessProtocolFields(
        state: ServerUiState,
        flowOptions: List<String>
    ) {
        FormTextField(
            label = stringResource(R.string.server_lab_id),
            value = state.password,
            onValueChange = { state.password = it }
        )
        FormTextField(
            label = stringResource(R.string.server_lab_encryption),
            value = state.encryption,
            onValueChange = { state.encryption = it }
        )
        FormDropdownField(
            label = stringResource(R.string.server_lab_flow),
            value = state.flow,
            options = flowOptions,
            onValueChange = { state.flow = it }
        )
    }
}