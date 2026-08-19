package com.v2ray.ang.ui.ipscanner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

class IpScannerActivity : BaseComponentActivity() {
    private val viewModel: IpScannerViewModel by viewModels {
        IpScannerViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadLastSession()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun ScreenContent() {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var selectedTab by remember { mutableIntStateOf(0) }
        var topN by remember { mutableStateOf("10") }
        var showHelp by remember { mutableStateOf(false) }

        // نمایش ایمن Toast
        LaunchedEffect(uiState.toastMessage) {
            uiState.toastMessage?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                viewModel.clearToastMessage()
            }
        }

        if (showHelp) {
            AlertDialog(
                onDismissRequest = { showHelp = false },
                title = { Text("How to use IP Scanner") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Scan Tab: Configure and run TCP scan to find reachable Cloudflare IPs.", fontWeight = FontWeight.Bold)
                        Text("   - Target Count: How many IPs to scan")
                        Text("   - Workers: Concurrent connections")
                        Text("   - Timeout: Connection timeout per IP")
                        Text("   - Ports: Which ports to test")
                        Spacer(Modifier.height(4.dp))
                        Text("2. Results Tab: See scan results sorted by latency.", fontWeight = FontWeight.Bold)
                        Text("   - Tap any IP to copy it")
                        Text("   - Use 'Copy Top 50' to copy best IPs")
                        Spacer(Modifier.height(4.dp))
                        Text("3. Generate Tab: Create Clean IP configs.", fontWeight = FontWeight.Bold)
                        Text("   - Select how many top IPs to use per config (1, 5, 10, or custom)")
                        Text("   - Tap Generate to create optimized configs")
                        Text("   - Generated configs appear in 'Clean IP' category")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelp = false }) { Text("OK") }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("IP Scanner") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back_24dp), "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showHelp = true }) {
                            Icon(Icons.Filled.HelpOutline, "Help")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Scan") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Results") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Generate") })
                }

                when (selectedTab) {
                    0 -> ScanTab(uiState, viewModel)
                    1 -> ResultsTab(uiState)
                    2 -> GenerateTab(uiState, viewModel, topN, { topN = it })
                }
            }
        }
    }
}

@Composable
private fun ScanTab(uiState: IpScannerViewModel.UiState, viewModel: IpScannerViewModel) {
    val context = LocalContext.current
    var targetCount by remember { mutableStateOf("2,000") }
    var workers by remember { mutableStateOf("50") }
    var timeout by remember { mutableStateOf("3s") }
    var selectedPorts by remember { mutableStateOf(setOf("443", "8443", "2096")) }

    var showTargetCustom by remember { mutableStateOf(false) }
    var showWorkersCustom by remember { mutableStateOf(false) }
    var showTimeoutCustom by remember { mutableStateOf(false) }

    val isBusy = uiState.isScanning || uiState.isCooldown

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Scan Essentials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        SinglePresetSelector(
            label = "Target Count",
            presets = listOf("500", "2,000", "5,000", "Custom"),
            selected = if (showTargetCustom) "Custom" else targetCount,
            onSelect = {
                if (it == "Custom") {
                    showTargetCustom = true
                    targetCount = ""
                } else {
                    showTargetCustom = false
                    targetCount = it
                }
            }
        )
        if (showTargetCustom) {
            OutlinedTextField(
                value = targetCount,
                onValueChange = { targetCount = it },
                label = { Text("Custom Target Count") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        SinglePresetSelector(
            label = "Concurrent Workers",
            presets = listOf("20", "50", "100", "Custom"),
            selected = if (showWorkersCustom) "Custom" else workers,
            onSelect = {
                if (it == "Custom") {
                    showWorkersCustom = true
                    workers = ""
                } else {
                    showWorkersCustom = false
                    workers = it
                }
            }
        )
        if (showWorkersCustom) {
            OutlinedTextField(
                value = workers,
                onValueChange = { workers = it },
                label = { Text("Custom Workers") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        SinglePresetSelector(
            label = "Probe Timeout",
            presets = listOf("2s", "3s", "5s", "Custom"),
            selected = if (showTimeoutCustom) "Custom" else timeout,
            onSelect = {
                if (it == "Custom") {
                    showTimeoutCustom = true
                    timeout = ""
                } else {
                    showTimeoutCustom = false
                    timeout = it
                }
            }
        )
        if (showTimeoutCustom) {
            OutlinedTextField(
                value = timeout,
                onValueChange = { timeout = it },
                label = { Text("Custom Timeout (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        SegmentedPresetSelector(
            label = "Ports",
            presets = listOf("443", "8443", "2053", "2083", "2087", "2096"),
            selectedPresets = selectedPorts,
            onSelectionChange = { selectedPorts = it },
            multiSelect = true
        )

        if (uiState.isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "${uiState.testedCount} tested | ${uiState.greenCount} green | ${uiState.failedCount} failed",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(uiState.scanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = {
                if (uiState.isScanning) {
                    viewModel.stopScan()
                } else {
                    val parsedTarget = targetCount.replace(",", "").toIntOrNull()
                    if (parsedTarget == null || parsedTarget <= 0) {
                        Toast.makeText(context, "Please enter a valid target count", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.setTopN(parsedTarget)

                    val parsedWorkers = workers.toIntOrNull()
                    if (parsedWorkers == null || parsedWorkers <= 0) {
                        Toast.makeText(context, "Please enter valid worker count", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val timeoutMs = when (timeout) {
                        "2s" -> 2000
                        "3s" -> 3000
                        "5s" -> 5000
                        else -> timeout.removeSuffix("s").toIntOrNull()?.times(1000) ?: 3000
                    }

                    viewModel.startTcpScan(
                        ports = selectedPorts.mapNotNull { it.toIntOrNull() },
                        timeoutMs = timeoutMs,
                        concurrency = parsedWorkers
                    )
                }
            },
            enabled = !uiState.isCooldown,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when {
                    uiState.isCooldown -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Please wait (${uiState.cooldownSeconds}s)")
                    }
                    uiState.isScanning -> {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop")
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Scan")
                    }
                    else -> {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start")
                        Spacer(Modifier.width(8.dp))
                        Text("Start TCP Scan")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsTab(uiState: IpScannerViewModel.UiState) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (uiState.scanResults.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results yet. Run a scan first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${uiState.scanResults.size} IPs found", fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                val top50 = uiState.scanResults.take(50).joinToString("\n") { "${it.ip}:${it.port}" }
                clipboardManager.setText(AnnotatedString(top50))
                Toast.makeText(context, "Copied top 50 IPs (IP:Port)", Toast.LENGTH_SHORT).show()
            }) { Text("Copy Top 50") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(uiState.scanResults) { result ->
                val ipWithPort = "${result.ip}:${result.port}"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(12.dp)
                        .clickable {
                            clipboardManager.setText(AnnotatedString(ipWithPort))
                            Toast.makeText(context, "Copied $ipWithPort", Toast.LENGTH_SHORT).show()
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(result.ip, fontWeight = FontWeight.Medium)
                    Text("${result.port}")
                    Text("${result.latencyMs}ms", color = MaterialTheme.colorScheme.primary)
                    if (result.tlsOk) Text("TLS✓", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun GenerateTab(
    uiState: IpScannerViewModel.UiState,
    viewModel: IpScannerViewModel,
    topN: String,
    onTopNChange: (String) -> Unit
) {
    var showCustomTopN by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.scanResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scan results. Run a scan first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Text("Generate Clean IP Configs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Select how many top IPs to use per config", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SinglePresetSelector(
            label = "Top N IPs per config",
            presets = listOf("1", "5", "10", "Custom"),
            selected = if (showCustomTopN) "Custom" else topN,
            onSelect = {
                if (it == "Custom") {
                    showCustomTopN = true
                    onTopNChange("")
                } else {
                    showCustomTopN = false
                    onTopNChange(it)
                }
            }
        )

        if (showCustomTopN) {
            OutlinedTextField(
                value = topN,
                onValueChange = onTopNChange,
                label = { Text("Custom Top N") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isGenerating,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Text("${uiState.scanResults.size} IPs available from scan", style = MaterialTheme.typography.bodyMedium)

        if (uiState.isGenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Text(
            text = uiState.scanStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { viewModel.generateCleanIPs(topN.toIntOrNull() ?: 10) },
            enabled = !uiState.isGenerating && !uiState.isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onTertiary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generating Clean Configs...")
                } else {
                    Text("Generate Clean IPs (${if (topN.isEmpty()) "10" else topN} per config)")
                }
            }
        }
    }
}