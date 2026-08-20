package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    doubleColumnDisplay: Boolean,
    confirmRemove: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val serverFlow = remember(groupId) {
        mainViewModel.serversForGroup(groupId)
    }
    val servers by serverFlow.collectAsStateWithLifecycle()
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()

    ServerListPage(
        servers = servers,
        selectedGuid = selectedGuid,
        canReorder = canReorder,
        doubleColumnDisplay = doubleColumnDisplay,
        subscriptionId = groupId,
        groupId = groupId,
        lazyListStates = lazyListStates,
        lazyGridStates = lazyGridStates,
        onSelectServer = onSelectServer,
        onEditServer = onEditServer,
        onShareServer = onShareServer,
        onMoreServer = onMoreServer,
        onRemoveServer = onRemoveServer,
        onMoveServer = { fromIndex, toIndex -> mainViewModel.moveServer(groupId, fromIndex, toIndex) },
        contentPadding = contentPadding
    )
}

@Composable
private fun ServerListPage(
    servers: List<ServersCache>,
    selectedGuid: String?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    subscriptionId: String,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    onMoveServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    val spacing = 8.dp

    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + spacing,
                bottom = contentPadding.calculateBottomPadding() + spacing,
                start = 12.dp,
                end = 12.dp
            )
        ) {
            itemsIndexed(items = servers, key = { _, item -> item.guid }) { _, serverCache ->
                val content: @Composable () -> Unit = {
                    ServerCardItem(
                        serverCache = serverCache,
                        selectedGuid = selectedGuid,
                        subscriptionId = subscriptionId,
                        doubleColumnDisplay = true,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareServer = onShareServer,
                        onMoreServer = onMoreServer,
                        onRemoveServer = onRemoveServer
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = serverCache.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + spacing,
                bottom = contentPadding.calculateBottomPadding() + spacing,
                start = 12.dp,
                end = 12.dp
            )
        ) {
            itemsIndexed(items = servers, key = { _, item -> item.guid }) { _, serverCache ->
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = serverCache.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerCardItem(
                                serverCache = serverCache,
                                selectedGuid = selectedGuid,
                                subscriptionId = subscriptionId,
                                doubleColumnDisplay = false,
                                onSelectServer = onSelectServer,
                                onEditServer = onEditServer,
                                onShareServer = onShareServer,
                                onMoreServer = onMoreServer,
                                onRemoveServer = onRemoveServer
                            )
                        }
                    }
                } else {
                    ServerCardItem(
                        serverCache = serverCache,
                        selectedGuid = selectedGuid,
                        subscriptionId = subscriptionId,
                        doubleColumnDisplay = false,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareServer = onShareServer,
                        onMoreServer = onMoreServer,
                        onRemoveServer = onRemoveServer
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCardItem(
    serverCache: ServersCache,
    selectedGuid: String?,
    subscriptionId: String,
    doubleColumnDisplay: Boolean,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val isSelected = serverCache.guid == selectedGuid

    val subRemarks = remember(profile.subscriptionId, subscriptionId) {
        if (subscriptionId.isEmpty()) {
            MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()?.toString().orEmpty()
        } else ""
    }

    val statistics = remember(profile) {
        profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
    }

    val typeDescription = remember(profile) {
        getProtocolDescription(profile)
    }

    // هماهنگ‌سازی با رنگ‌بندی کانتینری متریال ۳ صفحه تنظیمات
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(300),
        label = "borderColor"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(300),
        label = "containerColor"
    )

    OutlinedCard(
        onClick = { onSelectServer(serverCache.guid) },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = serverCache.profile.remarks,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        lineBreak = LineBreak.Paragraph
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (doubleColumnDisplay) {
                    IconButton(onClick = { onMoreServer(serverCache.guid, serverCache.profile) }, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_more_vert_24dp), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = { onShareServer(serverCache.guid, serverCache.profile) }, modifier = Modifier.size(32.dp)) {
                            Icon(painterResource(R.drawable.ic_share_24dp), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEditServer(serverCache.guid, serverCache.profile) }, modifier = Modifier.size(32.dp)) {
                            Icon(painterResource(R.drawable.ic_edit_24dp), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemoveServer(serverCache.guid) }, modifier = Modifier.size(32.dp)) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subRemarks.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = subRemarks.take(1).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = statistics,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeDescription,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorConfigType,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (serverCache.testDelayMillis > 0L) stringResource(R.string.server_test_delay_value, serverCache.testDelayMillis) else "",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (serverCache.testDelayMillis < 0L) colorPingRed else colorPing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ServerItemColumn(
    serverCache: ServersCache,
    selectedGuid: String?,
    subscriptionId: String,
    doubleColumnDisplay: Boolean,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val subRemarks = if (subscriptionId.isEmpty()) {
        MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()?.toString() ?: ""
    } else ""
    Column {
        ServerListItem(
            remarks = profile.remarks,
            statistics = profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile),
            typeDescription = getProtocolDescription(profile),
            testDelayMillis = serverCache.testDelayMillis,
            isSelected = serverCache.guid == selectedGuid,
            subscriptionRemarks = subRemarks,
            doubleColumnDisplay = doubleColumnDisplay,
            onClick = { onSelectServer(serverCache.guid) },
            onEdit = { onEditServer(serverCache.guid, profile) },
            onShare = { onShareServer(serverCache.guid, profile) },
            onRemove = { onRemoveServer(serverCache.guid) },
            onMore = { onMoreServer(serverCache.guid, profile) }
        )
        ItemDivider()
    }
}

@Composable
fun ServerListItem(
    remarks: String,
    statistics: String,
    typeDescription: String,
    testDelayMillis: Long,
    isSelected: Boolean,
    subscriptionRemarks: String,
    doubleColumnDisplay: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier
) {
    val testResult = if (testDelayMillis == 0L) {
        ""
    } else {
        stringResource(R.string.server_test_delay_value, testDelayMillis)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .then(dragModifier)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = remarks,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        lineBreak = LineBreak.Paragraph
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (doubleColumnDisplay) {
                    IconButton(onClick = onMore, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert_24dp),
                            stringResource(R.string.acc_more),
                            Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onShare, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            stringResource(R.string.title_configuration_share),
                            Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onEdit, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_edit_24dp),
                            stringResource(R.string.acc_edit),
                            Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onRemove, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            stringResource(R.string.acc_delete),
                            Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (subscriptionRemarks.isNotBlank()) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), Alignment.Center
                    ) {
                        Text(subscriptionRemarks.take(1).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subscriptionRemarks.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = subscriptionRemarks.take(1).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = statistics,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeDescription,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorConfigType,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (testDelayMillis > 0L) stringResource(R.string.server_test_delay_value, testDelayMillis) else "",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (testDelayMillis < 0L) colorPingRed else colorPing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun getProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { net ->
        if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) parts.add(net)
    }
    profile.security?.let { sec ->
        if (sec.isNotBlank()) {
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                parts.add("$sec insecure")
            } else {
                parts.add(sec)
            }
        }
    }
    return parts.joinToString(" / ")
}

internal suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return

    if (abs(target - current) == 1 && animateAdjacentPage) {
        animateScrollToPage(target)
    } else {
        scrollToPage(target)
    }
}