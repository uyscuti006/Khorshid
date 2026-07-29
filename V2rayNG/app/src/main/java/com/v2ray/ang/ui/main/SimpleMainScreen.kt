package com.v2ray.ang.ui.main

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R

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
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun LionSunLogo(
    isRunning: Boolean,
    modifier: Modifier = Modifier.size(260.dp)
) {
    val sunTranslationY by animateFloatAsState(
        targetValue = if (isRunning) 0f else 120f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "sunTranslationY"
    )

    val sunAlpha by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "sunAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "sunRotation")
    val sunRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunRotationValue"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_sun),
            contentDescription = "Sun",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = sunTranslationY * density
                    alpha = sunAlpha
                    rotationZ = if (isRunning) sunRotation else 0f
                }
        )

        Image(
            painter = painterResource(id = R.drawable.ic_lion),
            contentDescription = "Lion",
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun MinimalStatsRow(
    uiState: MainUiState,
    onTestPing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "↓ ${uiState.downloadSpeedText}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "↑ ${uiState.uploadSpeedText}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = uiState.connectionDurationText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when (uiState.currentPingDelay) {
                -2L -> "..."
                -1L -> "Ping"
                else -> "${uiState.currentPingDelay}ms"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onTestPing)
        )
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: ConfigCategory,
    onSelectCategory: (ConfigCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConfigCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = { Text(category.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun ConnectButton(
    isRunning: Boolean,
    isConnecting: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            when {
                isRunning -> onAction(MainAction.ToggleService)
                isConnecting -> onAction(MainAction.CancelConnect)
                else -> onAction(MainAction.ConnectFastest)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = when {
            isRunning -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            isConnecting -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            else -> ButtonDefaults.buttonColors()
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = when {
                    isRunning -> "Disconnect"
                    isConnecting -> "Connecting... (Tap to Cancel)"
                    else -> "Connect"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onOpenAdvanced: () -> Unit
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val allServers by mainViewModel.serversForGroup(uiState.selectedGroupId).collectAsState()
    val servers = mainViewModel.filterByCategory(allServers, uiState.selectedCategory)
    val context = LocalContext.current

    val systemDarkMode = isSystemInDarkTheme()
    val effectiveDarkMode = if (uiState.hasUserToggledTheme) uiState.isDarkMode else systemDarkMode
    val colorScheme = if (effectiveDarkMode) DarkColorScheme else LightColorScheme

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { onAction(MainAction.ToggleTheme) }) {
                            Icon(
                                imageVector = if (effectiveDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/VPN_Khorshid"))
                            context.startActivity(intent)
                        }) {
                            Icon(painter = painterResource(id = R.drawable.ic_telegram), contentDescription = "Telegram", modifier = Modifier.size(25.dp))
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/uyscuti006/Khorshid"))
                            context.startActivity(intent)
                        }) {
                            Icon(painter = painterResource(id = R.drawable.ic_github), contentDescription = "GitHub", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onOpenAdvanced) {
                            Icon(painter = painterResource(id = R.drawable.ic_settings), contentDescription = "Settings", modifier = Modifier.size(30.dp))
                        }
                    }
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val screenHeight = maxHeight

                if (isLandscape) {
                    // 🟢 چیدمان افقی (Landscape Layout)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // سمت چپ: لوگوی بزرگ شیر و خورشید + آمار مصرف
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // اندازه لوگو در حالت افقی به شدت افزایش یافت
                            val landscapeLogoSize = when {
                                screenHeight < 400.dp -> 210.dp
                                screenHeight < 600.dp -> 260.dp
                                else -> 300.dp
                            }

                            LionSunLogo(
                                isRunning = uiState.isRunning,
                                modifier = Modifier.size(landscapeLogoSize)
                            )

                            if (uiState.isRunning) {
                                MinimalStatsRow(
                                    uiState = uiState,
                                    onTestPing = { onAction(MainAction.TestCurrentServer) }
                                )
                            }
                        }

                        // سمت راست: فیلتر دسته‌بندی‌ها + دکمه اتصال
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CategoryFilterRow(
                                selectedCategory = uiState.selectedCategory,
                                onSelectCategory = { category -> onAction(MainAction.SelectCategory(category)) }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            ConnectButton(
                                isRunning = uiState.isRunning,
                                isConnecting = uiState.isConnecting,
                                onAction = onAction,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    // 🔵 چیدمان عمودی (Portrait Layout)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // بخش بالایی: فیلتر دسته‌بندی‌ها
                        CategoryFilterRow(
                            selectedCategory = uiState.selectedCategory,
                            onSelectCategory = { category -> onAction(MainAction.SelectCategory(category)) }
                        )

                        // بخش میانی: لوگو و آمار مصرف
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            val logoSize = when {
                                screenHeight < 600.dp -> 180.dp
                                screenHeight < 700.dp -> 220.dp
                                else -> 260.dp
                            }

                            LionSunLogo(
                                isRunning = uiState.isRunning,
                                modifier = Modifier.size(logoSize)
                            )

                            if (uiState.isRunning) {
                                MinimalStatsRow(
                                    uiState = uiState,
                                    onTestPing = { onAction(MainAction.TestCurrentServer) }
                                )
                            }
                        }

                        // بخش پایینی: دکمه اتصال
                        ConnectButton(
                            isRunning = uiState.isRunning,
                            isConnecting = uiState.isConnecting,
                            onAction = onAction,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}