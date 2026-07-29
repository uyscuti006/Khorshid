package com.v2ray.ang.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.compose.ConfirmDialog

@Composable
fun MainDialogs(
    showDelAllConfirm: Boolean,
    onDismissDelAll: () -> Unit,
    onConfirmDelAll: () -> Unit,
    showDelDuplicateConfirm: Boolean,
    onDismissDelDuplicate: () -> Unit,
    onConfirmDelDuplicate: () -> Unit,
    showDelInvalidConfirm: Boolean,
    onDismissDelInvalid: () -> Unit,
    onConfirmDelInvalid: () -> Unit,
    showRemoveConfirm: String?,
    onDismissRemove: () -> Unit,
    onConfirmRemove: (String) -> Unit,
) {
    // 📌 مقداردهی متون مشترک دکمه‌ها جهت بهینه‌سازی عملکرد
    val confirmOk = stringResource(android.R.string.ok)
    val cancelText = stringResource(android.R.string.cancel)

    // 🗑️ دایالوگ تایید حذف کلیه کانفیگ‌ها
    if (showDelAllConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = confirmOk,
            dismissText = cancelText,
            onConfirm = onConfirmDelAll,
            onDismiss = onDismissDelAll
        )
    }

    // 👯 دایالوگ تایید حذف کانفیگ‌های تکراری
    if (showDelDuplicateConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = confirmOk,
            dismissText = cancelText,
            onConfirm = onConfirmDelDuplicate,
            onDismiss = onDismissDelDuplicate
        )
    }

    // ⚠️ دایالوگ تایید حذف کانفیگ‌های غیرمجاز / نامعتبر
    if (showDelInvalidConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_invalid_config_comfirm),
            confirmText = confirmOk,
            dismissText = cancelText,
            onConfirm = onConfirmDelInvalid,
            onDismiss = onDismissDelInvalid
        )
    }

    // ❌ دایالوگ تایید حذف یک کانفیگ خاص
    showRemoveConfirm?.let { guid ->
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = confirmOk,
            dismissText = cancelText,
            onConfirm = { onConfirmRemove(guid) },
            onDismiss = onDismissRemove
        )
    }
}