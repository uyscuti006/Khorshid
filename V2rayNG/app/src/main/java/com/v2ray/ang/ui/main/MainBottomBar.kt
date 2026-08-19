package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // انیمیشن تغییر رنگ پس‌زمینه دکمه شناور (FAB)
    val fabContainerColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(300),
        label = "fabContainerColor"
    )

    // انیمیشن تغییر رنگ آیکون داخل دکمه شناور
    val fabIconTint by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "fabIconTint"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        // کانتینر اصلی نوار پایین به سبک متریال ۳ مدرن و گرد
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable { onAction(MainAction.TestCurrentServer) },
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 88.dp), // فاصله از راست جهت عدم تداخل متن با دکمه FAB
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // دکمه اتصال/قطع سرویس (Floating Action Button)
        FloatingActionButton(
            onClick = { onAction(MainAction.ToggleService) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp)
                .offset(y = (-24).dp)
                .navigationBarsPadding(),
            containerColor = fabContainerColor,
            contentColor = fabIconTint,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
                ),
                contentDescription = if (isRunning) "Stop" else "Start",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}