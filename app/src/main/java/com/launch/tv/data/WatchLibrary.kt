package com.launch.tv.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import com.launch.tv.model.WatchItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object WatchLibrary {
    private val cinemaPackages = setOf(
        "ru.kinopoisk.tv",
        "ru.ivi.client",
        "gpm.tnt_premier",
        "ru.start.androidmobile",
        "com.vk.tv",
        "ru.rt.video.app.tv",
        "mobi.zona",
        "com.google.android.videos",
        "com.amazon.amazonvideo.livingroom",
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
    )
    private val patchwall = listOf(
        "content://com.mitv.patchwall.media/history_video",
        "content://com.mitv.patchwall.media/local_history_video",
    ).map(Uri::parse)

    fun load(context: Context): List<WatchItem> {
        val merged = LinkedHashMap<String, WatchItem>()
        fun put(item: WatchItem) {
            val key = item.packageName + "|" + item.title
            if (item.title.isBlank() || key in merged) return
            merged[key] = item
        }
        if (Build.VERSION.SDK_INT >= 26) {
            readPrograms(context, TvContract.WatchNextPrograms.CONTENT_URI, watch = true).forEach(::put)
            readPrograms(context, TvContract.PreviewPrograms.CONTENT_URI, watch = false).forEach(::put)
            readChannelPrograms(context).forEach(::put)
        }
        patchwall.forEach { uri -> readPrograms(context, uri, watch = false).forEach(::put) }
        readCatalogs(context).forEach(::put)
        RecommendationHub.read(context).forEach(::put)
        return stacks(context, merged.values.toList())
    }

    private fun stacks(context: Context, items: List<WatchItem>): List<WatchItem> {
        val grouped = items.groupBy { it.packageName }
        val ordered = ArrayList<WatchItem>()
        val seen = HashSet<String>()
        fun take(pkg: String) {
            if (!seen.add(pkg)) return
            val rows = grouped[pkg].orEmpty().filter { !it.poster.isNullOrBlank() }.take(6)
            if (rows.isNotEmpty()) {
                ordered += rows
                return
            }
            val label = launchLabel(context, pkg)
            if (label.isNotBlank()) {
                ordered += WatchItem(
                    title = label,
                    poster = bannerPath(context, pkg),
                    intentUri = null,
                    packageName = pkg,
                )
            }
        }
        cinemaPackages.forEach(::take)
        grouped.keys.forEach(::take)
        return ordered
    }

    private fun readPrograms(context: Context, uri: Uri, watch: Boolean): List<WatchItem> {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return emptyList()
            cursor.use { rows(context, it, watch) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun readChannelPrograms(context: Context): List<WatchItem> {
        return try {
            val channels = context.contentResolver.query(
                TvContract.Channels.CONTENT_URI,
                arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME),
                "${TvContract.Channels.COLUMN_BROWSABLE}=1",
                null,
                null,
            ) ?: return emptyList()
                val items = ArrayList<WatchItem>()
            channels.use { cursor ->
                val idIndex = cursor.getColumnIndex(TvContract.Channels._ID)
                while (cursor.moveToNext() && items.size < 24) {
                    if (idIndex < 0) continue
                    val channelId = cursor.getLong(idIndex)
                    val programs = TvContract.buildPreviewProgramsUriForChannel(channelId)
                    items += readPrograms(context, programs, watch = false)
                }
            }
            items
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun readCatalogs(context: Context): List<WatchItem> {
        val items = ArrayList<WatchItem>()
        for ((packageName, authority) in catalogAuthorities(context)) {
            val query = Uri.parse("content://$authority/search_suggest_query")
            val cursor = runCatching {
                context.contentResolver.query(query, null, null, arrayOf(""), null)
            }.getOrNull() ?: continue
            cursor.use { items += suggestRows(it, packageName) }
        }
        return items
    }

    private fun catalogAuthorities(context: Context): List<Pair<String, String>> {
        val fixed = listOf(
            "ru.kinopoisk.tv" to "ru.kinopoisk.tv.search.kinopoisk.tv",
            "ru.ivi.client" to "ru.ivi.client.tv.data.search",
            "ru.rt.video.app.tv" to "wink-search-provider-authority",
            "mobi.zona" to "mobi.zona.TvSearchProvider",
        )
        val seen = fixed.map { it.second }.toMutableSet()
        val found = ArrayList<Pair<String, String>>()
        found += fixed
        val providers = runCatching { context.packageManager.queryContentProviders(null, 0, 0) }.getOrNull().orEmpty()
        for (info in providers) {
            val pkg = info.packageName ?: continue
            if (pkg !in cinemaPackages) continue
            val authority = info.authority?.substringBefore(';').orEmpty()
            val name = authority.lowercase()
            if (authority.isBlank() || authority in seen) continue
            if (!name.contains("search") && !name.contains("suggest")) continue
            if (name.contains("file") || name.contains("firebase") || name.contains("ads")) continue
            seen += authority
            found += pkg to authority
        }
        return found
    }

    private fun suggestRows(cursor: Cursor, packageName: String): List<WatchItem> {
        val titleIndex = findColumn(cursor, listOf("suggest_text_1", "title", "name"))
        val posterIndex = findColumn(
            cursor,
            listOf("suggest_result_card_image", "suggest_icon_1", "poster_art_uri", "suggest_icon_2"),
        )
        val dataIndex = findColumn(cursor, listOf("suggest_intent_data", "intent_uri"))
        val actionIndex = findColumn(cursor, listOf("suggest_intent_action"))
        val items = ArrayList<WatchItem>()
        while (cursor.moveToNext() && items.size < 6) {
            val title = text(cursor, titleIndex)
            if (title.length < 2) continue
            val poster = text(cursor, posterIndex).takeIf { it.startsWith("http") || it.startsWith("content:") || it.startsWith("/") }
            if (poster == null) continue
            val data = text(cursor, dataIndex)
            val action = text(cursor, actionIndex).ifBlank { "android.intent.action.VIEW" }
            val intent = when {
                data.startsWith("intent:") -> data
                data.startsWith("http://") || data.startsWith("https://") || data.startsWith("content:") -> {
                    "intent:#Intent;action=$action;data=${Uri.encode(data)};package=$packageName;end"
                }
                else -> null
            }
            items += WatchItem(title = title, poster = poster, intentUri = intent, packageName = packageName)
        }
        return items
    }

    private fun rows(context: Context, cursor: Cursor, watch: Boolean): List<WatchItem> {
        val titleIndex = column(
            cursor,
            if (watch) listOf(TvContract.WatchNextPrograms.COLUMN_TITLE) else listOf(TvContract.PreviewPrograms.COLUMN_TITLE),
            "title",
            "name",
            "program_title",
            "display_name",
            "short_title",
        )
        val posterIndex = column(
            cursor,
            "thumbnail_uri",
            "poster_art_uri",
            "poster_uri",
            "image_uri",
            "art_uri",
            "horizontal_poster_art",
            "poster",
            "card_image",
            "background_image_uri",
            "logo_uri",
        )
        val intentIndex = column(cursor, "intent_uri", "intent", "data_uri", "deeplink", "action_uri", "content_uri")
        val pkgIndex = column(cursor, "package_name", "pkg", "package", "app_package")
        val typeIndex = column(
            cursor,
            if (watch) TvContract.WatchNextPrograms.COLUMN_TYPE else TvContract.PreviewPrograms.COLUMN_TYPE,
        )
        val items = ArrayList<WatchItem>()
        while (cursor.moveToNext() && items.size < 16) {
            val title = text(cursor, titleIndex)
            if (title.isBlank()) continue
            val type = if (typeIndex >= 0 && cursor.getType(typeIndex) == Cursor.FIELD_TYPE_INTEGER) cursor.getInt(typeIndex) else -1
            if (!isFilm(type, typeIndex >= 0)) continue
            val poster = image(context, cursor, posterIndex, title)
            items += WatchItem(
                title = title,
                poster = poster,
                intentUri = text(cursor, intentIndex).ifBlank { null },
                packageName = text(cursor, pkgIndex),
            )
        }
        return items
    }

    private fun image(context: Context, cursor: Cursor, index: Int, title: String): String? {
        if (index < 0 || cursor.isNull(index)) return null
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_BLOB -> blobFile(context, cursor.getBlob(index), title)
            else -> cursor.getString(index)?.takeIf { it.isNotBlank() }
        }
    }

    private fun blobFile(context: Context, bytes: ByteArray?, title: String): String? {
        if (bytes == null || bytes.size < 32) return null
        return try {
            val dir = File(context.cacheDir, "posters").apply { mkdirs() }
            val file = File(dir, "blob-${title.hashCode().toUInt().toString(16)}.img")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun text(cursor: Cursor, index: Int): String {
        if (index < 0 || cursor.isNull(index)) return ""
        return if (cursor.getType(index) == Cursor.FIELD_TYPE_STRING) cursor.getString(index).orEmpty() else ""
    }

    private fun column(cursor: Cursor, preferred: String, vararg names: String): Int {
        return findColumn(cursor, listOf(preferred) + names)
    }

    private fun column(cursor: Cursor, preferred: List<String>, vararg names: String): Int {
        return findColumn(cursor, preferred + names)
    }

    private fun findColumn(cursor: Cursor, names: List<String>): Int {
        val have = cursor.columnNames ?: return -1
        for (name in names) {
            val index = have.indexOfFirst { it.equals(name, ignoreCase = true) }
            if (index >= 0) return index
        }
        return -1
    }

    private fun isFilm(type: Int, known: Boolean): Boolean {
        if (!known || type < 0) return true
        return type != TvContract.PreviewPrograms.TYPE_TRACK &&
            type != TvContract.PreviewPrograms.TYPE_ALBUM &&
            type != TvContract.PreviewPrograms.TYPE_ARTIST &&
            type != TvContract.PreviewPrograms.TYPE_PLAYLIST &&
            type != TvContract.PreviewPrograms.TYPE_STATION
    }

    private fun bannerPath(context: Context, packageName: String): String? {
        val manager = context.packageManager
        val info = runCatching { manager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        val drawable = runCatching { manager.getApplicationBanner(info) }.getOrNull()
            ?: runCatching { manager.getApplicationLogo(info) }.getOrNull()
            ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 8 } ?: 320
        val height = drawable.intrinsicHeight.takeIf { it > 8 } ?: 180
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            val dir = File(context.cacheDir, "banners").apply { mkdirs() }
            val file = File(dir, packageName.hashCode().toUInt().toString(16) + ".jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    fun launchLabel(context: Context, packageName: String): String {
        if (packageName.isBlank()) return ""
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault("")
    }
}

object RecommendationHub {
    private val pending = java.util.concurrent.ConcurrentHashMap<String, android.app.PendingIntent>()
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile var onChanged: (() -> Unit)? = null

    fun allowed(context: Context): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return flat.contains(context.packageName)
    }

    fun send(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val intent = pending[key] ?: return false
        return runCatching { intent.send() }.isSuccess
    }

    fun merge(context: Context, rows: List<RecommendationRow>) {
        if (rows.isEmpty()) return
        val kept = read(context).associateBy { it.launchKey ?: (it.packageName + "|" + it.title) }
        val next = LinkedHashMap<String, RecommendationRow>()
        rows.forEach { row -> next[row.key] = row }
        kept.values.forEach { item ->
            val key = item.launchKey ?: return@forEach
            if (key !in next) {
                next[key] = RecommendationRow(key, item.title, item.packageName, item.poster, pending[key])
            }
        }
        replace(context, next.values.take(18).toList())
    }

    fun replace(context: Context, rows: List<RecommendationRow>) {
        val app = context.applicationContext
        io.execute {
            val kept = rows.map { it.key }.toSet()
            pending.keys.filter { it !in kept }.forEach { pending.remove(it) }
            rows.forEach { row ->
                if (row.pending != null) pending[row.key] = row.pending
            }
            val array = JSONArray()
            rows.forEach { row ->
                array.put(
                    JSONObject()
                        .put("key", row.key)
                        .put("title", row.title)
                        .put("package", row.packageName)
                        .put("poster", row.poster.orEmpty()),
                )
            }
            file(app).writeText(array.toString())
            android.os.Handler(android.os.Looper.getMainLooper()).post { onChanged?.invoke() }
        }
    }

    fun read(context: Context): List<WatchItem> {
        val source = file(context)
        if (!source.isFile) return emptyList()
        return try {
            val array = JSONArray(source.readText())
            List(array.length()) { index ->
                val obj = array.optJSONObject(index) ?: return@List null
                val title = obj.optString("title")
                if (title.isBlank()) return@List null
                WatchItem(
                    title = title,
                    poster = obj.optString("poster").ifBlank { null },
                    intentUri = null,
                    packageName = obj.optString("package"),
                    launchKey = obj.optString("key").ifBlank { null },
                )
            }.filterNotNull()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, "recommendations.json")
}

data class RecommendationRow(
    val key: String,
    val title: String,
    val packageName: String,
    val poster: String?,
    val pending: android.app.PendingIntent?,
)
