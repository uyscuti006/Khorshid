package com.v2ray.ang.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R

@Composable
fun LionSunLogo(isRunning: Boolean) {
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
        modifier = Modifier.size(260.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onOpenAdvanced: () -> Unit
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val servers by mainViewModel.serversForGroup(uiState.selectedGroupId).collectAsState()
    val connectedServer = if (uiState.isRunning) {
        servers.find { it.guid == uiState.selectedGuid }
    } else null
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            LionSunLogo(isRunning = uiState.isRunning)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = uiState.statusText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .then(
                        if (uiState.isRunning) Modifier.clickable { onAction(MainAction.TestCurrentServer) }
                        else Modifier
                    )
            )

            if (uiState.isRunning && connectedServer != null) {
                Spacer(modifier = Modifier.height(16.dp))
                val pingText = when {
                    uiState.currentPingDelay == -2L -> "Testing..."
                    uiState.currentPingDelay >= 0 -> "${uiState.currentPingDelay} ms"
                    connectedServer!!.testDelayString.isNotEmpty() -> connectedServer!!.testDelayString
                    else -> "---"
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .clickable { onAction(MainAction.TestCurrentServer) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = connectedServer!!.profile.remarks,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ping: $pingText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (uiState.isRunning) {
                        onAction(MainAction.ToggleService)
                    } else {
                        onAction(MainAction.ConnectFastest)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 48.dp)
                    .height(56.dp),
                colors = if (uiState.isRunning) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    text = if (uiState.isRunning) "Disconnect" else "Connect",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
