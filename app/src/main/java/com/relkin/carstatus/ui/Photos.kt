package com.relkin.carstatus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.relkin.carstatus.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object PhotoStore {
    private const val MAX_SIDE = 2048
    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** Сохранить снимок в photos/ с уменьшением до 2048 px и поворотом по EXIF. Возвращает имя файла. */
    fun import(ctx: Context, src: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(src)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = ctx.contentResolver.openInputStream(src)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height))
        val m = Matrix().apply { postRotate(rotation); postScale(scale, scale) }
        val out = if (rotation != 0f || scale < 1f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) else bmp
        val name = "p_" + UUID.randomUUID().toString().take(12) + ".jpg"
        File(dir(ctx), name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    fun load(ctx: Context, file: String, maxSide: Int): ImageBitmap? {
        val key = "$file@$maxSide"
        cache.get(key)?.let { return it }
        val f = File(dir(ctx), file)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }

    fun uri(ctx: Context, file: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
}

@Composable
fun PhotoThumb(file: String, size: Dp, modifier: Modifier = Modifier, maxSide: Int = 320, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, file, maxSide) { value = withContext(Dispatchers.IO) { PhotoStore.load(ctx, file, maxSide) } }
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** Полноэкранный просмотр фото. */
@Composable
fun PhotoViewer(file: String, caption: String, onDismiss: () -> Unit, actions: @Composable () -> Unit = {}) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, file) { value = withContext(Dispatchers.IO) { PhotoStore.load(ctx, file, 2048) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable(onClick = onDismiss)) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Color(0x99000000)).padding(16.dp),
            ) {
                if (caption.isNotBlank()) Text(caption, color = androidx.compose.ui.graphics.Color.White)
                Row { actions() }
            }
        }
    }
}

/** Запуск камеры и галереи; [onAdded] получает имя сохранённого файла. */
class PhotoAdder(val camera: () -> Unit, val gallery: () -> Unit)

@Composable
fun rememberPhotoAdder(onAdded: (String) -> Unit): PhotoAdder {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (ok) scope.launch {
            withContext(Dispatchers.IO) { PhotoStore.import(ctx, PhotoStore.uri(ctx, f)) }?.let(onAdded)
            f.delete()
        } else f.delete()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        scope.launch { for (u in uris) withContext(Dispatchers.IO) { PhotoStore.import(ctx, u) }?.let(onAdded) }
    }
    return remember {
        PhotoAdder(
            camera = {
                val f = File(PhotoStore.dir(ctx), "tmp_" + System.currentTimeMillis() + ".jpg")
                pending = f
                runCatching { cam.launch(PhotoStore.uri(ctx, f)) }
            },
            gallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/**
 * Полоса фото в форме: уже прикреплённые [existing] и новые [added] (ещё не сохранённые),
 * кнопки камеры и галереи. Крестик на миниатюре убирает фото из формы.
 */
@Composable
fun PhotoStrip(
    existing: List<String>, added: List<String>, onAdd: (String) -> Unit,
    onRemoveAdded: (String) -> Unit, onRemoveExisting: (String) -> Unit,
) {
    val adder = rememberPhotoAdder(onAdd)
    var view by remember { mutableStateOf<String?>(null) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = adder.camera, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.PhotoCamera, tr("Снять фото", "Зробити фото")) }
        OutlinedIconButton(onClick = adder.gallery, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.AddPhotoAlternate, tr("Из галереи", "З галереї")) }
        existing.forEach { f -> RemovableThumb(f, { view = f }) { onRemoveExisting(f) } }
        added.forEach { f -> RemovableThumb(f, { view = f }) { onRemoveAdded(f) } }
    }
    view?.let { PhotoViewer(it, "", { view = null }) }
}

@Composable
private fun RemovableThumb(file: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Box {
        PhotoThumb(file, 56.dp, maxSide = 160, onClick = onClick)
        Icon(
            Icons.Filled.Close, tr("Убрать", "Прибрати"),
            Modifier.align(Alignment.TopEnd).size(18.dp).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.errorContainer).clickable(onClick = onRemove),
        )
    }
}

@Composable
fun SimpleInfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("ОК", "ОК")) } },
    )
}
