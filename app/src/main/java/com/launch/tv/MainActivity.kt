package com.launch.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.launch.tv.model.Section

class MainActivity : ComponentActivity() {
    private val viewModel: LaunchViewModel by viewModels()
    private var packageReceiver: BroadcastReceiver? = null

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importWallpaper(uri)
    }

    private val wallpaperFallback = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importWallpaper(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.refreshApps()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        packageReceiver = receiver
        if (PatchWall.isButtonIntent(intent)) openFromRemote()
        setContent {
            com.launch.tv.ui.LaunchRoot(
                viewModel = viewModel,
                onPickImage = { openWallpaperPicker() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (PatchWall.isButtonIntent(intent)) openFromRemote()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0 && PatchWall.isRemoteKey(event.keyCode)) {
            openFromRemote()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
        viewModel.refreshApps()
        viewModel.refreshSources()
    }

    private fun openWallpaperPicker() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)) {
            runCatching { wallpaperPicker.launch(request) }
                .onFailure { openWallpaperFallback() }
        } else {
            openWallpaperFallback()
        }
    }

    private fun openWallpaperFallback() {
        runCatching { wallpaperFallback.launch("image/*") }
            .onFailure { viewModel.toast("Нет приложения для выбора изображения") }
    }

    private fun openFromRemote() {
        viewModel.closeMenu()
        viewModel.section(Section.Home)
    }

    override fun onDestroy() {
        packageReceiver?.let { runCatching { unregisterReceiver(it) } }
        packageReceiver = null
        super.onDestroy()
    }
}
