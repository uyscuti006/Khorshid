package com.v2ray.ang.ui.ipscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.base.BaseComponentActivity

class IpScannerActivity : BaseComponentActivity() {

    private val viewModel: IpScannerViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun ScreenContent() {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var selectedTab by remember { mutableIntStateOf(0) }
        var showOperatorMenu by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            viewModel.toastEvent.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(Unit) {
            viewModel.navigationEvent.collect { targetTab ->
                selectedTab = targetTab
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Khorshid IP Scanner",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        Box {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable { showOperatorMenu = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = uiState.operator.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showOperatorMenu,
                                onDismissRequest = { showOperatorMenu = false }
                            ) {
                                com.v2ray.ang.reachability.KhorshidIspDetector.Operator.entries.forEach { op ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = op.displayName,
                                                fontWeight = if (op == uiState.operator) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            if (op == uiState.operator) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.setOperator(op)
                                            showOperatorMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Scanner", fontWeight = FontWeight.Bold) })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Results (${uiState.scanResults.size})", fontWeight = FontWeight.Bold) })
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Generate", fontWeight = FontWeight.Bold) })
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 -> MobileScanWorkspace(uiState, viewModel)
                            1 -> MobileResultsWorkspace(uiState, viewModel) { selectedTab = it }
                            2 -> MobileExportWorkspace(uiState, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileScanWorkspace(
    uiState: IpScannerViewModel.UiState,
    viewModel: IpScannerViewModel
) {
    var showCustomTarget by remember { mutableStateOf(false) }
    var showCustomWorkers by remember { mutableStateOf(false) }
    var showCustomTimeout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val settingsDisabled = uiState.isScanning || uiState.isCooldown || uiState.scanningNeighborIp != null

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (settingsDisabled) 0.6f else 1f },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Scan Essentials",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                SingleGridPresetSelector(
                    label = "IP Source",
                    presets = listOf("Random Pool", "Custom IPs"),
                    selected = if (uiState.ipSource == IpScannerViewModel.IpSourceType.RANDOM_POOL) "Random Pool" else "Custom IPs",
                    onSelect = {
                        viewModel.setIpSource(if (it == "Random Pool") IpScannerViewModel.IpSourceType.RANDOM_POOL else IpScannerViewModel.IpSourceType.CUSTOM_IPS)
                    },
                    columns = 2
                )

                if (uiState.ipSource == IpScannerViewModel.IpSourceType.CUSTOM_IPS) {
                    OutlinedTextField(
                        value = uiState.customIpText,
                        onValueChange = { viewModel.setCustomIpText(it) },
                        label = { Text("IPs / CIDRs (one per line)") },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        singleLine = false
                    )
                }

                SingleGridPresetSelector(
                    label = "Target Count",
                    presets = listOf("500", "1,000", "5,000", "Custom"),
                    selected = if (showCustomTarget) "Custom" else uiState.targetCount,
                    onSelect = {
                        if (it == "Custom") {
                            showCustomTarget = true
                            viewModel.setTargetCount("Custom")
                        } else {
                            showCustomTarget = false
                            viewModel.setTargetCount(it)
                        }
                    },
                    columns = 4
                )
                if (showCustomTarget) {
                    OutlinedTextField(
                        value = uiState.customTargetCount,
                        onValueChange = { viewModel.setCustomTargetCount(it) },
                        label = { Text("Custom count (min 10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                SingleGridPresetSelector(
                    label = "Concurrent Workers",
                    presets = listOf("50 (Restricted)", "100 (Balanced)", "200 (Fast)", "Custom"),
                    selected = if (showCustomWorkers) "Custom" else uiState.workers,
                    onSelect = {
                        if (it == "Custom") {
                            showCustomWorkers = true
                            viewModel.setWorkers("Custom")
                        } else {
                            showCustomWorkers = false
                            viewModel.setWorkers(it)
                        }
                    },
                    columns = 2
                )
                if (showCustomWorkers) {
                    OutlinedTextField(
                        value = uiState.customWorkers,
                        onValueChange = { viewModel.setCustomWorkers(it) },
                        label = { Text("Custom workers (5 - 300)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                SingleGridPresetSelector(
                    label = "Probe Timeout",
                    presets = listOf("2s (Fast)", "3s (Balanced)", "5s (Default)", "Custom"),
                    selected = if (showCustomTimeout) "Custom" else uiState.timeout,
                    onSelect = {
                        if (it == "Custom") {
                            showCustomTimeout = true
                            viewModel.setTimeout("Custom")
                        } else {
                            showCustomTimeout = false
                            viewModel.setTimeout(it)
                        }
                    },
                    columns = 2
                )
                if (showCustomTimeout) {
                    OutlinedTextField(
                        value = uiState.customTimeout,
                        onValueChange = { viewModel.setCustomTimeout(it) },
                        label = { Text("Timeout in seconds (1 - 30)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                GridPresetSelector(
                    label = "Target Ports",
                    presets = listOf("443", "8443", "2053", "2083", "2087", "2096"),
                    selectedPresets = uiState.selectedPorts,
                    onSelectionChange = { viewModel.setSelectedPorts(it) },
                    columns = 3,
                    multiSelect = true,
                    modifier = Modifier
                        .fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Generate Configs", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text("Auto-clone all compatible configs with best IP after scan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.autoGenerateConfigs, onCheckedChange = { viewModel.setAutoGenerateConfigs(it) }, enabled = !settingsDisabled)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WebSocket Check", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                        Text("Stricter verification for WS configs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.requireWebSocket, onCheckedChange = { viewModel.setRequireWebSocket(it) }, enabled = !settingsDisabled)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-scan neighbors (/24)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                        Text("Expand subnet on white hit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.autoScanNeighbors, onCheckedChange = { viewModel.setAutoScanNeighbors(it) }, enabled = !settingsDisabled)
                }
            }
        }

        if (uiState.isScanning) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { uiState.progressPercent },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${uiState.testedCount} tested", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${uiState.whiteCount} white", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        Text("${uiState.failedCount} failed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Button(
            onClick = {
                if (uiState.isScanning) viewModel.stopScan() else viewModel.startDiscovery()
            },
            enabled = !uiState.isCooldown && uiState.scanningNeighborIp == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (uiState.isCooldown) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Please wait (${uiState.cooldownSeconds}s)")
                } else {
                    Icon(imageVector = if (uiState.isScanning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = if (uiState.isScanning) "Stop Scan" else "Start Scan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun MobileResultsWorkspace(
    uiState: IpScannerViewModel.UiState,
    viewModel: IpScannerViewModel,
    onNavigateToTab: (Int) -> Unit = {}
) {
    val context = LocalContext.current

    if (uiState.scanResults.isEmpty() && uiState.scanningNeighborIp == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results yet. Run a scan from the Scanner tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.showScanCompleteBanner && uiState.scanResults.isNotEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onNavigateToTab(2)
                            viewModel.dismissScanCompleteBanner()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scan complete! ${uiState.scanResults.size} white IPs found",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap to go to Generate tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (uiState.scanningNeighborIp != null) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Scanning /24 for ${uiState.scanningNeighborIp}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${(uiState.subnetProgressPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { uiState.subnetProgressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${uiState.subnetTestedCount} / ${uiState.subnetTotalCount} tested",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${uiState.subnetWhiteCount} white found",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${uiState.scanResults.size} White IPs", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!uiState.isScanning && uiState.scanningNeighborIp == null) {
                        FilledTonalButton(
                            onClick = { viewModel.runSpeedTestOnTopResults() },
                            enabled = !uiState.isTestingSpeed,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (uiState.isTestingSpeed) "Testing..." else "Speed Test", fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val list = uiState.scanResults.take(20).joinToString("\n") { "${it.ip}:${it.port}" }
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("scan results", list))
                            Toast.makeText(context, "Top 20 endpoints copied", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Top 20", fontSize = 11.sp)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.scanResults) { item ->
                val isThisItemScanning = uiState.scanningNeighborIp == item.ip

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.ip, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                item.colo?.let { colo ->
                                    Spacer(Modifier.width(6.dp))
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                                        Text(" $colo ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                            Text("Port: ${item.port} | Latency: ${item.latencyMs}ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (item.speedMbps > 0) {
                                Text("${item.speedMbps} MB/s", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }

                            TextButton(
                                onClick = { viewModel.scanNeighbors(item.ip) },
                                enabled = uiState.scanningNeighborIp == null && !uiState.isScanning,
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                if (isThisItemScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Scanning...", fontSize = 11.sp)
                                } else {
                                    Text("Scan /24", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileExportWorkspace(
    uiState: IpScannerViewModel.UiState,
    viewModel: IpScannerViewModel
) {
    val compatibleConfigs = uiState.userConfigs.filter { it.isCompatible }
    val allSelected = compatibleConfigs.isNotEmpty() && uiState.selectedGuids.size == compatibleConfigs.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleGridPresetSelector(
                    label = "Clean IP Source",
                    presets = listOf("From Scanner (${uiState.scanResults.size})", "Manual Custom IPs"),
                    selected = if (uiState.useManualCleanIps) "Manual Custom IPs" else "From Scanner (${uiState.scanResults.size})",
                    onSelect = {
                        viewModel.setUseManualCleanIps(it == "Manual Custom IPs")
                    },
                    columns = 2
                )

                if (uiState.useManualCleanIps) {
                    OutlinedTextField(
                        value = uiState.manualCleanIpText,
                        onValueChange = { viewModel.setManualCleanIpText(it) },
                        label = { Text("Clean IPs / Endpoints (e.g. 104.21.5.8:443)") },
                        placeholder = { Text("104.21.5.8:443\n104.16.2.1:2053") },
                        modifier = Modifier.fillMaxWidth().height(85.dp),
                        singleLine = false
                    )
                }
            }
        }

        if (compatibleConfigs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No compatible CDN configs found to clone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Text("Select source configs to clone:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${uiState.selectedGuids.size} of ${compatibleConfigs.size} compatible selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { viewModel.selectAllConfigs(!allSelected) }) {
                Text(if (allSelected) "Deselect All" else "Select Compatible")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(uiState.userConfigs) { cfg ->
                val isSelected = uiState.selectedGuids.contains(cfg.guid)

                val cardBg = when {
                    !cfg.isCompatible -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surface
                }
                val borderStroke = when {
                    !cfg.isCompatible -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    isSelected -> androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    else -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = cfg.isCompatible) { viewModel.toggleConfigSelection(cfg.guid) },
                    shape = RoundedCornerShape(12.dp),
                    color = cardBg,
                    border = borderStroke,
                    tonalElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { viewModel.toggleConfigSelection(cfg.guid) },
                            enabled = cfg.isCompatible
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = cfg.remarks,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (cfg.isCompatible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                if (!cfg.isCompatible) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                                        Text(" Direct ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text("${cfg.server}:${cfg.port} — ${cfg.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        var showCustomIps by remember { mutableStateOf(false) }

        SingleGridPresetSelector(
            label = "Top IPs per config",
            presets = listOf("1", "3", "5", "Custom"),
            selected = if (showCustomIps) "Custom" else uiState.ipsPerConfig.toString(),
            onSelect = {
                if (it == "Custom") {
                    showCustomIps = true
                } else {
                    showCustomIps = false
                    viewModel.setIpsPerConfig(it.toIntOrNull() ?: 1)
                }
            },
            columns = 4
        )
        if (showCustomIps) {
            OutlinedTextField(
                value = uiState.ipsPerConfigCustom,
                onValueChange = { viewModel.setIpsPerConfigCustom(it) },
                label = { Text("Custom (1 - 100)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        val hasValidIpSource = if (uiState.useManualCleanIps) uiState.manualCleanIpText.isNotBlank() else uiState.scanResults.isNotEmpty()

        Button(
            onClick = {
                viewModel.generateCleanConfigs()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = uiState.selectedGuids.isNotEmpty() && !uiState.isGenerating && hasValidIpSource
        ) {
            Text(if (uiState.isGenerating) "Generating..." else "Generate Khorshid Clean Configs", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}