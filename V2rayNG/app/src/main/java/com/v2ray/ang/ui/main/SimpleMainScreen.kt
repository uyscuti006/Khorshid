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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Radar
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.v2ray.ang.ui.compose.AlertRed
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import kotlinx.coroutines.launch

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
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
        label = "sunTranslationY"
    )

    val sunAlpha by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "sunAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "sunRotation")
    val sunRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunRotationValue"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isRunning) 0.55f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 100f),
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
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
            .height(32.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
    )
}

@Composable
private fun LiveStatsRow(
    uiState: MainUiState,
    onTestPing: () -> Unit,
    width: Dp = Dp.Unspecified,
    isRunning: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isTestingPing = uiState.currentPingDelay == -2L
    val pingText = when (val ping = uiState.currentPingDelay) {
        -2L -> "..."
        -1L -> "Test"
        -3L -> "Timeout"
        0L -> "-"
        else -> "${ping}ms"
    }
    val pingColor = when {
        isTestingPing -> MaterialTheme.colorScheme.primary
        uiState.currentPingDelay == -3L -> colorPingRed
        uiState.currentPingDelay > 0L -> colorPing
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val contentAlpha = if (isRunning) 1f else 0.35f

    Row(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier)
            .graphicsLayer { alpha = contentAlpha }
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FixedStat(
            icon = Icons.Filled.ArrowDownward,
            label = "Down",
            value = uiState.downloadSpeedText,
            iconTint = StatDownloadColor,
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        FixedStat(
            icon = Icons.Filled.ArrowUpward,
            label = "Up",
            value = uiState.uploadSpeedText,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        FixedStat(
            icon = Icons.Filled.Bolt,
            label = "Ping",
            value = pingText,
            iconTint = pingColor,
            modifier = Modifier
                .weight(1f)
                .clickable(
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
    val disconnectOuterColor = if (isDarkMode) Color(0xFF2A1213) else Color(0xFFFFCDD2)

    val buttonShape = RoundedCornerShape(50)
    val outerHaloShape = RoundedCornerShape(50)

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
        modifier = modifier,
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

@Composable
private fun CleanIpEmptyBanner(
    configCount: Int,
    onGoToScanner: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blueColor = Color(0xFF2979FF)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Radar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )

        if (configCount > 0) {
            Text(
                text = "$configCount Clean IP configs available",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "No Clean IP scanned yet",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Scan Cloudflare IPs to find clean, fast endpoints for your configs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(blueColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onGoToScanner
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Radar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Go to IP Scanner",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onOpenAdvanced: () -> Unit,
    onNavigateToIpScanner: () -> Unit = {}
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val allServers by mainViewModel.serversForGroup(uiState.selectedGroupId).collectAsState()
    val servers = mainViewModel.filterByCategory(allServers, uiState.selectedCategory)
    val context = LocalContext.current

    LaunchedEffect(uiState.status) {
        val status = uiState.status
        if (status is com.v2ray.ang.ui.main.MainStatus.TestProgress) {
            val msg = status.progress
            if (msg.contains("failed") || msg.contains("Failed") || msg.contains("timeout") || msg.contains("Error") || msg.contains("ping -1")) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val isDarkTheme = LocalDarkTheme.current
    val colorScheme = MaterialTheme.colorScheme

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

    val gradientTint by animateColorAsState(
        targetValue = when {
            uiState.isConnecting -> if (isDarkTheme) Color(0xFFFFCA28).copy(alpha = 0.10f) else Color(0xFFFFE0B2).copy(alpha = 0.25f)
            uiState.isRunning -> if (isDarkTheme) Color(0xFFFFD54F).copy(alpha = 0.22f) else Color(0xFFFFE082).copy(alpha = 0.30f)
            else -> colorScheme.background
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 80f),
        label = "gradientTint"
    )
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(gradientTint, colorScheme.background)
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val iconTint = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF424242)
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { onAction(MainAction.ToggleTheme) }) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
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
                            start = (screenWidth * 0.02f).coerceAtLeast(8.dp),
                            end = (screenWidth * 0.02f).coerceAtLeast(8.dp)
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
                            isDarkMode = isDarkTheme,
                            modifier = Modifier.size((screenHeight * 0.65f).coerceIn(120.dp, screenHeight * 0.85f))
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CategoryFilterRow(
                            selectedCategory = uiState.selectedCategory,
                            isConnecting = uiState.isConnecting,
                            onSelectCategory = { category -> onAction(MainAction.SelectCategory(category)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        CipherSuitesToggle(
                            isEnabled = uiState.isCipherSuitesEnabled,
                            isConnecting = uiState.isConnecting,
                            effectiveDarkMode = isDarkTheme,
                            onToggle = { onAction(MainAction.ToggleCipherSuites) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.selectedCategory == ConfigCategory.CLEAN_IP && uiState.showCleanIpEmptyBanner) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CleanIpEmptyBanner(
                                configCount = servers.size,
                                onGoToScanner = onNavigateToIpScanner,
                                onDismiss = { onAction(MainAction.DismissCleanIpEmptyBanner) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LiveStatsRow(
                            uiState = uiState,
                            onTestPing = { onAction(MainAction.TestCurrentServer) },
                            width = screenWidth,
                            isRunning = uiState.isRunning,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        ConnectButton(
                            isRunning = uiState.isRunning,
                            isConnecting = uiState.isConnecting,
                            isDarkMode = isDarkTheme,
                            onAction = onAction,
                            modifier = Modifier.fillMaxWidth(0.70f)
                        )
                    }
                }
            } else {
                val logoSize = (screenWidth * 0.80f).coerceIn(100.dp, screenHeight * 0.38f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = (screenHeight * 0.05f).coerceAtLeast(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    LionSunLogo(
                        isRunning = uiState.isRunning,
                        isDarkMode = isDarkTheme,
                        modifier = Modifier.size(logoSize)
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
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
                            modifier = Modifier
                                .padding(top = 24.dp)
                        )

                        CipherSuitesToggle(
                            isEnabled = uiState.isCipherSuitesEnabled,
                            isConnecting = uiState.isConnecting,
                            effectiveDarkMode = isDarkTheme,
                            onToggle = { onAction(MainAction.ToggleCipherSuites) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (uiState.selectedCategory == ConfigCategory.CLEAN_IP && uiState.showCleanIpEmptyBanner) {
                            CleanIpEmptyBanner(
                                configCount = servers.size,
                                onGoToScanner = onNavigateToIpScanner,
                                onDismiss = { onAction(MainAction.DismissCleanIpEmptyBanner) }
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        LiveStatsRow(
                            uiState = uiState,
                            onTestPing = { onAction(MainAction.TestCurrentServer) },
                            isRunning = uiState.isRunning,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(bottom = (screenHeight * 0.015f).coerceAtLeast(8.dp))
                        )

                        ConnectButton(
                            isRunning = uiState.isRunning,
                            isConnecting = uiState.isConnecting,
                            isDarkMode = isDarkTheme,
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
