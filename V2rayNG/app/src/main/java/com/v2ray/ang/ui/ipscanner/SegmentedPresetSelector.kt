package com.v2ray.ang.ui.ipscanner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GridPresetSelector(
    label: String,
    presets: List<String>,
    selectedPresets: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    columns: Int = 2,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val rows = presets.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { preset ->
                        val isSelected = preset in selectedPresets

                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(150),
                            label = "chipBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            animationSpec = tween(150),
                            label = "chipText"
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            animationSpec = tween(150),
                            label = "chipBorder"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (multiSelect) {
                                        val newSet = selectedPresets.toMutableSet()
                                        if (isSelected) newSet.remove(preset) else newSet.add(preset)
                                        onSelectionChange(newSet)
                                    } else {
                                        onSelectionChange(setOf(preset))
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center,
                                color = textColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SingleGridPresetSelector(
    label: String,
    presets: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    columns: Int = 2,
    modifier: Modifier = Modifier
) {
    GridPresetSelector(
        label = label,
        presets = presets,
        selectedPresets = setOf(selected),
        onSelectionChange = { onSelect(it.firstOrNull() ?: selected) },
        columns = columns,
        modifier = modifier,
        multiSelect = false
    )
}
