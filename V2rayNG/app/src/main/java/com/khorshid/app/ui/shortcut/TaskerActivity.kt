package com.v2ray.ang.ui.shortcut

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.SettingsSwitchItem
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBF3FE),
    onPrimaryContainer = Color(0xFF1E293B),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF183A5D),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF8E94A0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF23252E),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFE67E22),
    onTertiary = Color.White,
    background = Color(0xFF0F1115),
    onBackground = Color.White,
    surface = Color(0xFF1E2026),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF23252E),
    onSurfaceVariant = Color(0xFF8E94A0),
    error = Color(0xFFEF4444),
    onError = Color.White
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)

class TaskerActivity : BaseComponentActivity() {

    private var lstData: ArrayList<String> = ArrayList()
    private var lstGuid: ArrayList<String> = ArrayList()

    private val switchState = mutableStateOf(false)
    private val selectedPosition = mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lstData.add("Default")
        lstGuid.add(AppConfig.TASKER_DEFAULT_GUID)

        MmkvManager.decodeAllServerList().forEach { key ->
            MmkvManager.decodeServerConfig(key)?.let { config ->
                lstData.add(config.remarks)
                lstGuid.add(key)
            }
        }

        init()
    }

    @Composable
    override fun ScreenContent() {
        TaskerScreen(
            items = lstData,
            switchState = switchState,
            selectedPosition = selectedPosition,
            onBackClick = { finish() },
            onSave = { confirmFinish() }
        )
    }

    private fun init() {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, "")

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else {
                switchState.value = switch
                val pos = lstGuid.indexOf(guid.toString())
                if (pos >= 0) {
                    selectedPosition.value = pos
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to initialize Tasker settings", e)
        }
    }

    private fun confirmFinish() {
        val position = selectedPosition.value
        if (position < 0) {
            return
        }

        val extraBundle = Bundle()
        extraBundle.putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, switchState.value)
        extraBundle.putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, lstGuid[position])
        val intent = Intent()

        val remarks = lstData[position]
        val blurb = if (switchState.value) {
            "Start $remarks"
        } else {
            "Stop $remarks"
        }

        intent.putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
        intent.putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        setResult(RESULT_OK, intent)
        finish()
    }
}

@Composable
fun TaskerScreen(
    items: List<String>,
    switchState: MutableState<Boolean>,
    selectedPosition: MutableState<Int>,
    onBackClick: () -> Unit,
    onSave: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val listState = rememberLazyListState()

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AppTopBar(
                    title = "",
                    onBackClick = onBackClick,
                    actions = {
                        IconButton(onClick = onSave) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fab_check),
                                contentDescription = stringResource(R.string.menu_item_save_config),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.tasker_start_service),
                        checked = switchState.value,
                        onCheckedChange = { switchState.value = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollbar(listState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items) { index, remarks ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPosition.value = index },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPosition.value == index,
                                    onClick = { selectedPosition.value = index }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = remarks,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}