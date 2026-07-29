package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.verticalScrollbar

@Composable
fun MainDrawerContent(
    currentRoute: String? = null,
    onNavigate: (String) -> Unit
) {
    val drawerScrollState = rememberScrollState()

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .navigationBarsPadding(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerTonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
        ) {
            // 🎨 هدر زیبای کشوی جانبی
            DrawerHeader()

            Spacer(modifier = Modifier.height(12.dp))

            // 📌 گروه اصلی تنظیمات
            DrawerMenuGroup(
                items = listOf(
                    DrawerMenuItemData(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting, "sub_setting"),
                    DrawerMenuItemData(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings, "per_app_proxy"),
                    DrawerMenuItemData(R.drawable.ic_routing_24dp, R.string.routing_settings_title, "routing_setting"),
                    DrawerMenuItemData(R.drawable.ic_file_24dp, R.string.title_user_asset_setting, "user_asset"),
                    DrawerMenuItemData(R.drawable.ic_settings_24dp, R.string.title_settings, "settings")
                ),
                currentRoute = currentRoute,
                onNavigate = onNavigate
            )

            AppDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

            // 🛠️ گروه ابزارها و لاگ
            DrawerMenuGroup(
                items = listOf(
                    DrawerMenuItemData(R.drawable.ic_logcat_24dp, R.string.title_logcat, "logcat")
                ),
                currentRoute = currentRoute,
                onNavigate = onNavigate
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily(Font(R.font.montserrat_thin)),
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class DrawerMenuItemData(
    val iconRes: Int,
    val labelRes: Int,
    val route: String
)

@Composable
private fun DrawerMenuGroup(
    items: List<DrawerMenuItemData>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    items.forEach { item ->
        DrawerMenuItem(
            icon = painterResource(item.iconRes),
            label = stringResource(item.labelRes),
            selected = currentRoute == item.route,
            onClick = { onNavigate(item.route) }
        )
    }
}

@Composable
fun DrawerMenuItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        },
        modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}