package com.familygamenight.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.familygamenight.core.game.GameJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.min

@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** Fallback avatar colour (ARGB) when no picture is set. */
    val color: Long,
    val hasAvatar: Boolean = false,
    /** Bumped whenever the picture changes so caches refresh. */
    val avatarVersion: Int = 0,
)

/** Users live on this device only (for now) in a small JSON file, with avatars as JPEGs. */
class ProfileStore(private val context: Context) {
    private val file = File(context.filesDir, "profiles.json")
    private val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }

    fun load(): List<Profile> = runCatching {
        GameJson.decodeFromString(ListSerializer(Profile.serializer()), file.readText())
    }.getOrDefault(emptyList())

    fun save(profiles: List<Profile>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(GameJson.encodeToString(ListSerializer(Profile.serializer()), profiles))
        tmp.renameTo(file)
    }

    fun newProfile(name: String, color: Long) = Profile(UUID.randomUUID().toString(), name.trim(), color)

    fun avatarFile(id: String) = File(avatarDir, "$id.jpg")

    /** Copies a picked photo in, centre-cropped to a square and shrunk to [size]px. */
    fun importAvatar(id: String, uri: Uri, size: Int = 320): Boolean = runCatching {
        val bitmap = decode(uri) ?: return false
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        avatarFile(id).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    }.getOrDefault(false)

    private fun decode(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 1600) d.setTargetSampleSize(longest / 1600 + 1)
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }

    fun deleteAvatar(id: String) {
        avatarFile(id).delete()
    }

    fun loadAvatar(id: String): Bitmap? = avatarFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Small base64 JPEG to send to other devices in LAN games. */
    fun avatarForNetwork(id: String): String? {
        val bmp = loadAvatar(id) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 128, 128, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        val palette = listOf(
            0xFFB23A48, 0xFF3A6EA5, 0xFF4E8C4A, 0xFFD08C2B, 0xFF7B4FA0,
            0xFF2F8F8A, 0xFFC0567E, 0xFF6B5B3E, 0xFF3D4F9F, 0xFF9C3D22,
        )

        fun decodeNetworkAvatar(b64: String): Bitmap? = runCatching {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}
