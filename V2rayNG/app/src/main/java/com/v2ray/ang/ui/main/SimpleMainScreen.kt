package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.AlertRed
import kotlinx.coroutines.launch


private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1D70F5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFAF8F5),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8E5DE),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF253140),
    background = Color(0xFF111111),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF252525),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val ConnectingYellow = Color(0xFFFFC107)
private val SunGlowYellowLight = Color(0xFFFFCA28)
private val SunGlowYellowDark = Color(0xFFFFD54F)
private val StatDownloadColor = Color(0xFF2ECC71)

@Composable
fun LionSunLogo(
    isRunning: Boolean,
    isDarkMode: Boolean = true,
    modifier: Modifier = Modifier.size(280.dp)
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

    val glowAlpha by animateFloatAsState(
        targetValue = if (isRunning) 0.50f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(if (isDarkMode) SunGlowYellowDark else SunGlowYellowLight, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Image(
            painter = painterResource(id = R.drawable.ic_sun),
            contentDescription = "Sun",
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-8).dp)
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
private fun CategoryFilterRow(
    selectedCategory: ConfigCategory,
    isConnecting: Boolean,
    onSelectCategory: (ConfigCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDisabled = isConnecting
    val alpha = if (isDisabled) 0.5f else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .graphicsLayer { this.alpha = alpha }
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConfigCategory.entries.forEach { category ->
            val selected = selectedCategory == category

            val bgColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(300),
                label = "categoryBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(300),
                label = "categoryText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .then(
                        if (!isDisabled) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectCategory(category) } else Modifier
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun CipherSuitesToggle(
    isEnabled: Boolean,
    isConnecting: Boolean,
    effectiveDarkMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDisabled = isConnecting
    val alpha = if (isDisabled) 0.5f else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .then(
                if (!isDisabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Cipher Suites",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Bypass internet restrictions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            enabled = !isConnecting,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = if (effectiveDarkMode) Color(0xFFBDBDBD) else Color(0xFF757575),
                uncheckedTrackColor = if (effectiveDarkMode) Color(0xFF424242) else Color(0xFFBDBDBD)
            )
        )
    }
}

@Composable
private fun FixedStat(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(85.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
    )
}

@Composable
private fun LiveStatsRow(
    uiState: MainUiState,
    onTestPing: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier
) {
    val isTestingPing = uiState.currentPingDelay == -2L
    val pingText = when (val ping = uiState.currentPingDelay) {
        -2L -> "..."
        -1L -> "Test"
        0L -> "-"
        else -> "${ping}ms"
    }
    val pingColor = when {
        isTestingPing -> MaterialTheme.colorScheme.primary
        uiState.currentPingDelay == -1L -> MaterialTheme.colorScheme.onSurfaceVariant
        uiState.currentPingDelay > 0L -> colorPing
        else -> colorPingRed
    }

    Row(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FixedStat(
            icon = Icons.Filled.ArrowDownward,
            label = "Down",
            value = uiState.downloadSpeedText,
            iconTint = StatDownloadColor
        )
        StatDivider()
        FixedStat(
            icon = Icons.Filled.ArrowUpward,
            label = "Up",
            value = uiState.uploadSpeedText,
            iconTint = MaterialTheme.colorScheme.primary
        )
        StatDivider()
        FixedStat(
            icon = Icons.Filled.Bolt,
            label = "Ping",
            value = pingText,
            iconTint = pingColor,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTestPing
            )
        )
    }
}

@Composable
private fun ConnectButton(
    isRunning: Boolean,
    isConnecting: Boolean,
    isDarkMode: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val blueColor = Color(0xFF2979FF)
    val orangeColor = Color(0xFFFF9100)
    val redColor = if (isDarkMode) Color(0xFFEF5350) else Color(0xFFE53935)

    val disconnectOuterColor = if (isDarkMode) Color(0xFF2A1213) else Color(0xFFFCDAD7)

    val buttonShape = RoundedCornerShape(16.dp)
    val outerHaloShape = RoundedCornerShape(12.dp)

    val infiniteTransition = rememberInfiniteTransition(label = "connectingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rippleScale = remember { Animatable(1f) }
    val rippleAlpha = remember { Animatable(0f) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "pressScale"
    )

    val currentScale = if (isConnecting) pulseScale else pressScale

    Box(
        modifier = modifier.height(80.dp),
        contentAlignment = Alignment.Center
    ) {

        AnimatedVisibility(
            visible = isRunning,
            enter = fadeIn(tween(250)) + expandVertically(),
            exit = fadeOut(tween(250)) + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(outerHaloShape)
                    .background(disconnectOuterColor)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(buttonShape)
                        .background(redColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onAction(MainAction.ToggleService) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Disconnect",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isRunning,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(0.70f)
            ) {
                // پالس آبی موقع کلیک
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer {
                            scaleX = rippleScale.value
                            scaleY = rippleScale.value
                            alpha = rippleAlpha.value
                        }
                        .clip(buttonShape)
                        .background(blueColor)
                )

                val buttonColor by animateColorAsState(
                    targetValue = if (isConnecting) orangeColor else blueColor,
                    animationSpec = tween(350),
                    label = "buttonColor"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer {
                            scaleX = currentScale
                            scaleY = currentScale
                        }
                        .clip(buttonShape)
                        .background(buttonColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (!isConnecting) {
                                scope.launch {
                                    rippleScale.snapTo(1f)
                                    rippleAlpha.snapTo(0.4f)
                                    launch { rippleScale.animateTo(1.15f, tween(400)) }
                                    launch { rippleAlpha.animateTo(0f, tween(400)) }
                                }
                                onAction(MainAction.ConnectFastest)
                            } else {
                                onAction(MainAction.CancelConnect)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Connecting...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "Connect",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
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

    // Read theme directly from ThemeManager for instant reactivity
    val themeMode by com.v2ray.ang.ui.compose.ThemeManager.themeMode.collectAsState()
    val effectiveDarkMode = when (themeMode) {
        "1" -> false  // Light
        "2" -> true   // Dark
        else -> isSystemInDarkTheme()  // System
    }
    val colorScheme = if (effectiveDarkMode) DarkColorScheme else LightColorScheme

    // Kill Switch disconnect confirmation dialog
    if (uiState.showDisconnectDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onAction(MainAction.DismissDisconnectDialog) },
            title = { Text("Kill Switch Active") },
            text = { Text("Kill Switch is active. Disconnecting will restore internet access and remove protection. Continue?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { onAction(MainAction.ConfirmDisconnectWithKillSwitch) }
                ) {
                    Text("Disconnect", color = AlertRed)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { onAction(MainAction.DismissDisconnectDialog) }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = colorScheme.surface,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurfaceVariant
        )
    }

    // Empty category dialog
    if (uiState.showEmptyCategoryDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onAction(MainAction.DismissEmptyCategoryDialog) },
            title = { Text("No Configs Found") },
            text = { Text("There are no configurations in the \"${uiState.emptyCategoryName}\" category. Try a different category or update your subscriptions.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { onAction(MainAction.DismissEmptyCategoryDialog) }
                ) {
                    Text("OK")
                }
            },
            containerColor = colorScheme.surface,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurfaceVariant
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        val gradientTint by animateColorAsState(
            targetValue = when {
                uiState.isConnecting -> Color(0xFFFFCA28).copy(alpha = 0.22f)
                uiState.isRunning -> (if (effectiveDarkMode) Color(0xFFFFD54F).copy(alpha = 0.35f) else Color(0xFFFFE082).copy(alpha = 0.35f))
                else -> colorScheme.background
            },
            animationSpec = tween(durationMillis = 700),
            label = "gradientTint"
        )
        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(gradientTint, colorScheme.background)
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                val iconTint = if (effectiveDarkMode) Color(0xFFE0E0E0) else Color(0xFF424242)
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { onAction(MainAction.ToggleTheme) }) {
                            Icon(
                                imageVector = if (effectiveDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle Theme",
                                modifier = Modifier.size(24.dp),
                                tint = iconTint
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = Color.Unspecified,
                        actionIconContentColor = Color.Unspecified
                    ),
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/VPN_Khorshid"))
                            context.startActivity(intent)
                        }) {
                            Icon(painter = painterResource(id = R.drawable.ic_telegram), contentDescription = "Telegram", modifier = Modifier.size(25.dp), tint = iconTint)
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/uyscuti006/Khorshid"))
                            context.startActivity(intent)
                        }) {
                            Icon(painter = painterResource(id = R.drawable.ic_github), contentDescription = "GitHub", modifier = Modifier.size(24.dp), tint = iconTint)
                        }
                        IconButton(onClick = {
                            onAction(MainAction.EnterAdvancedMode)
                            onOpenAdvanced()
                        }) {
                            Icon(painter = painterResource(id = R.drawable.ic_settings_24dp), contentDescription = "Settings", modifier = Modifier.size(30.dp), tint = iconTint)
                        }
                    }
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
            ) {

                val screenWidth = maxWidth
                val screenHeight = maxHeight
                val isLandscape = screenWidth > screenHeight

                if (isLandscape) {

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding() + 8.dp,
                                start = 16.dp,
                                end = 16.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            LionSunLogo(
                                isRunning = uiState.isRunning,
                                isDarkMode = effectiveDarkMode,
                                modifier = Modifier.size((screenHeight * 0.65f).coerceAtMost(260.dp))
                            )
                        }


                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CategoryFilterRow(
                                selectedCategory = uiState.selectedCategory,
                                isConnecting = uiState.isConnecting,
                                onSelectCategory = { category -> onAction(MainAction.SelectCategory(category)) }
                            )

                            CipherSuitesToggle(
                                isEnabled = uiState.isCipherSuitesEnabled,
                                isConnecting = uiState.isConnecting,
                                effectiveDarkMode = effectiveDarkMode,
                                onToggle = { onAction(MainAction.ToggleCipherSuites) }
                            )

                            if (uiState.isRunning) {
                                LiveStatsRow(
                                    uiState = uiState,
                                    onTestPing = { onAction(MainAction.TestCurrentServer) },
                                    width = screenWidth * 0.45f
                                )
                            }

                            ConnectButton(
                                isRunning = uiState.isRunning,
                                isConnecting = uiState.isConnecting,
                                isDarkMode = effectiveDarkMode,
                                onAction = onAction,
                                modifier = Modifier.fillMaxWidth(0.9f)
                            )
                        }
                    }
                } else {

                    val logoSize = (screenWidth * 0.80f).coerceAtMost(screenHeight * 0.38f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LionSunLogo(
                            isRunning = uiState.isRunning,
                            isDarkMode = effectiveDarkMode,
                            modifier = Modifier.size(logoSize)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding() + 16.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {

                        CategoryFilterRow(
                            selectedCategory = uiState.selectedCategory,
                            isConnecting = uiState.isConnecting,
                            onSelectCategory = { category -> onAction(MainAction.SelectCategory(category)) },
                            modifier = Modifier.padding(top = 24.dp)
                        )

                        CipherSuitesToggle(
                            isEnabled = uiState.isCipherSuitesEnabled,
                            isConnecting = uiState.isConnecting,
                            effectiveDarkMode = effectiveDarkMode,
                            onToggle = { onAction(MainAction.ToggleCipherSuites) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // Clean IP: نمایش تعداد کانفیگ‌های تولید شده
                        if (uiState.selectedCategory == ConfigCategory.CLEAN_IP && servers.isNotEmpty()) {
                            Text(
                                text = "${servers.size} Clean IP configs available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        AnimatedVisibility(
                            visible = uiState.isRunning,
                            enter = fadeIn(tween(400)) + expandVertically(),
                            exit = fadeOut(tween(250)) + shrinkVertically(),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            LiveStatsRow(
                                uiState = uiState,
                                onTestPing = { onAction(MainAction.TestCurrentServer) },
                                width = screenWidth * 0.85f
                            )
                        }

                        ConnectButton(
                            isRunning = uiState.isRunning,
                            isConnecting = uiState.isConnecting,
                            isDarkMode = effectiveDarkMode,
                            onAction = onAction,
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}