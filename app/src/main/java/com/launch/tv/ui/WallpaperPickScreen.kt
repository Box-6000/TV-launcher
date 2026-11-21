package com.launch.tv.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.launch.tv.LaunchViewModel
import com.launch.tv.data.WallpaperImage
import com.launch.tv.data.WallpaperLoader
import com.launch.tv.data.WallpaperStudio
import com.launch.tv.data.WallpaperSources
import com.launch.tv.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WallpaperPickScreen(viewModel: LaunchViewModel, onGallery: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var category by remember { mutableStateOf(WallpaperStudio.categories.first().id) }
    var images by remember { mutableStateOf<List<WallpaperImage>>(emptyList()) }
    var flash by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) images = WallpaperSources.list(context)
    }
    LaunchedEffect(Unit) {
        flash = withContext(Dispatchers.IO) { WallpaperSources.removableMounted(context) }
        if (!flash) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            images = withContext(Dispatchers.IO) { WallpaperSources.list(context) }
        } else {
            ask.launch(permission)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Живые обои, фото, GIF или видео. OK ставит выбранное.",
            color = palette.muted,
            fontSize = 14.sp,
            maxLines = 1,
        )
        Text("Живые", color = palette.text, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            WallpaperStudio.live.forEach { item ->
                LiveCard(item) { viewModel.useLive(item.id) }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(WallpaperStudio.categories, key = { it.id }) { item ->
                Surface(
                    onClick = { category = item.id },
                    modifier = Modifier.height(40.dp).pageLead(item.id).okPress({ category = item.id }) {},
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (category == item.id) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White.copy(alpha = 0.3f),
                    ),
                ) {
                    Text(
                        item.title,
                        color = palette.text,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items((0 until WallpaperStudio.COUNT).toList(), key = { it }) { index ->
                StudioCard(category, index) { viewModel.useStudio(category, index) }
            }
        }
        if (flash) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (images.isEmpty()) "Флешка: файлов пока нет" else "Флешка",
                color = palette.muted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onGallery,
                modifier = Modifier.height(40.dp).okPress(onGallery) {},
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            ) {
                Text(
                    "Файл с накопителя",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        if (images.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                items(images, key = { it.uri.toString() }) { image ->
                    val thumb by produceState<ImageBitmap?>(null, image.uri) {
                        value = withContext(Dispatchers.IO) { WallpaperLoader.preview(context, image.uri) }
                    }
                    Surface(
                        onClick = { viewModel.importWallpaper(image.uri) },
                        modifier = Modifier.width(112.dp).height(64.dp).okPress({ viewModel.importWallpaper(image.uri) }) {},
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    ) {
                        if (thumb != null) {
                            Image(thumb!!, image.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(image.label, color = palette.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCard(item: WallpaperStudio.LiveWallpaper, onPick: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) { WallpaperStudio.thumb(context, item.thumb, 0) }
    }
    Surface(
        onClick = onPick,
        modifier = Modifier.width(168.dp).height(96.dp).okPress(onPick) {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                Image(image!!, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Text(
                item.title,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

@Composable
private fun StudioCard(category: String, index: Int, onPick: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, category, index) {
        value = withContext(Dispatchers.IO) { WallpaperStudio.thumb(context, category, index) }
    }
    Surface(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth().height(92.dp).okPress(onPick) {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        if (image != null) {
            Image(image!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}
