package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ServersCache
import kotlinx.coroutines.flow.StateFlow

@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeSelectedTabIndex = remember(selectedTabIndex, groups.size) {
        selectedTabIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    }

    PrimaryScrollableTabRow(
        selectedTabIndex = safeSelectedTabIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 16.dp,
        minTabWidth = 56.dp,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier
                    .tabIndicatorOffset(
                        selectedTabIndex = safeSelectedTabIndex,
                        matchContentSize = true
                    )
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                width = Dp.Unspecified,
                color = MaterialTheme.colorScheme.primary
            )
        },
        divider = {}
    ) {
        groups.forEachIndexed { index, group ->
            GroupTabItem(
                group = group,
                selected = index == safeSelectedTabIndex,
                serverFlowProvider = { mainViewModel.serversForGroup(group.id) },
                onClick = { onTabClick(index) }
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlowProvider: () -> StateFlow<List<ServersCache>>,
    onClick: () -> Unit
) {
    val serverFlow = remember(group.id) { serverFlowProvider() }
    val servers by serverFlow.collectAsStateWithLifecycle()

    val count = if (group.id.isEmpty()) null else servers.size

    GroupTabContent(
        remarks = group.remarks,
        serverCount = count,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun GroupTabContent(
    remarks: String,
    serverCount: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // بهینه‌سازی: یادسپاری فرمت متن تب برای جلوگیری از String Concatenation در هر فریم Recomposition
    val tabText = remember(remarks, serverCount) {
        if (serverCount == null) remarks else "$remarks ($serverCount)"
    }

    // انیمیشن نرم متریال ۳ برای تغییر رنگ متن تب فعال/غیرفعال
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "tabTextColor"
    )

    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        text = {
            Text(
                text = tabText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textColor
            )
        }
    )
}