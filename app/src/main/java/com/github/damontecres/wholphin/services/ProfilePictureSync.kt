package com.github.damontecres.wholphin.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.util.Base64
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.setup.AvatarChoices
import com.github.damontecres.wholphin.ui.setup.BenflixAvatars
import com.github.damontecres.wholphin.ui.setup.avatarBackgroundFor
import com.github.damontecres.wholphin.ui.setup.avatarInkFor
import com.github.damontecres.wholphin.ui.theme.ProfileColours
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix profile pictures follow the person to every TV and phone, and survive a reinstall.
 *
 * When someone picks a picture it is:
 *  - uploaded to their Jellyfin account as their profile picture, so the Who's watching screen
 *    shows it even before anyone has signed in on a new device (Jellyfin's own apps show it too), and
 *  - noted in their Jellyfin display settings by number, so Benflix knows which one is picked.
 *
 * Picking "Use automatic picture" takes the picture off their Jellyfin account again.
 */
@Singleton
class ProfilePictureSync
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
        private val jellyfin: Jellyfin,
        private val clientInfo: ClientInfo,
        private val deviceInfo: DeviceInfo,
    ) {
        /**
         * Save [index] (null = automatic) as [user]'s picture on this device and on the server at [serverUrl].
         * Returns true if the server has it too.
         */
        suspend fun pick(
            serverUrl: String,
            user: JellyfinUser,
            index: Int?,
        ): Boolean {
            setHere(user.id, index)
            val token = user.accessToken ?: return false
            return upload(serverUrl, user.id, token, index)
        }

        /**
         * Called after someone signs in: if the server knows their picture, use it here.
         * If only this device knows it (picked before pictures were saved to the server), send it up once.
         */
        suspend fun restore(
            serverUrl: String,
            user: JellyfinUser,
            customPrefs: Map<String, String?>,
        ) {
            AvatarChoices.init(context)
            val saved = customPrefs[KEY]
            if (saved != null) {
                val index = saved.toIntOrNull()?.takeIf { it in BenflixAvatars.indices }
                if (AvatarChoices.get(user.id) != index) setHere(user.id, index)
                // Pictures sent while the old full-colour pictures were still in the app came out as a
                // plain square, so send their picture again once, drawn properly on their colour
                val redrawn = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val redrawnKey = "redrawn-v2-${user.id}"
                val token = user.accessToken
                if (index != null && token != null && !redrawn.getBoolean(redrawnKey, false)) {
                    if (upload(serverUrl, user.id, token, index)) {
                        redrawn.edit { putBoolean(redrawnKey, true) }
                    }
                }
            } else {
                val local = AvatarChoices.get(user.id) ?: return
                val token = user.accessToken ?: return
                upload(serverUrl, user.id, token, local)
            }
        }

        /**
         * The picture as a PNG on the person's colour, the same way Benflix draws it, so Jellyfin
         * (and Who's watching on a new device) shows it with the right background.
         * Runs on the main thread because it reads the colours the tiles read.
         */
        private fun onTheirColour(
            userId: UUID,
            res: Int,
        ): ByteArray {
            ProfileColours.init(context)
            val size = 256
            val background = avatarBackgroundFor(userId, res)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(background.toArgb())
            val shade =
                Paint().apply {
                    shader =
                        LinearGradient(
                            0f,
                            0f,
                            0f,
                            size.toFloat(),
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.argb(41, 0, 0, 0),
                            Shader.TileMode.CLAMP,
                        )
                }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), shade)
            ContextCompat.getDrawable(context, res)?.mutate()?.let { drawable ->
                drawable.setBounds(0, 0, size, size)
                drawable.colorFilter = PorterDuffColorFilter(avatarInkFor(background).toArgb(), PorterDuff.Mode.SRC_IN)
                drawable.draw(canvas)
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            return out.toByteArray()
        }

        /** The picture tiles read this, so change it on the main thread */
        private suspend fun setHere(
            id: UUID,
            index: Int?,
        ) = withContext(Dispatchers.Main) {
            AvatarChoices.init(context)
            AvatarChoices.set(id, index)
        }

        private suspend fun upload(
            serverUrl: String,
            userId: UUID,
            token: String,
            index: Int?,
        ): Boolean =
            withContext(WholphinDispatchers.IO) {
                try {
                    val auth =
                        AuthorizationHeaderBuilder.buildHeader(
                            clientName = clientInfo.name,
                            clientVersion = clientInfo.version,
                            deviceId = deviceInfo.id,
                            deviceName = deviceInfo.name,
                            accessToken = token,
                        )
                    val url =
                        serverUrl
                            .trimEnd('/')
                            .plus("/UserImage")
                            .toHttpUrl()
                            .newBuilder()
                            .addQueryParameter("userId", userId.toString())
                            .build()
                    val res = index?.let { BenflixAvatars.getOrNull(it) }
                    val request =
                        if (res != null) {
                            // Jellyfin wants the picture's bytes written out as base64 text
                            val bytes = withContext(Dispatchers.Main) { onTheirColour(userId, res) }
                            val body =
                                Base64
                                    .encodeToString(bytes, Base64.NO_WRAP)
                                    .toRequestBody("image/png".toMediaType())
                            Request
                                .Builder()
                                .url(url)
                                .header("Authorization", auth)
                                .post(body)
                                .build()
                        } else {
                            Request
                                .Builder()
                                .url(url)
                                .header("Authorization", auth)
                                .delete()
                                .build()
                        }
                    val uploaded =
                        okHttpClient.newCall(request).execute().use { response ->
                            // Taking off a picture that isn't there is fine
                            val ok = response.isSuccessful || (res == null && response.code == 404)
                            if (!ok) Timber.w("Could not save the profile picture: %s", response.code)
                            ok
                        }
                    // Remember which one it is, so other devices can highlight it and draw it crisply.
                    // Same lock as Settings follow you, so neither undoes the other's change.
                    val api = jellyfin.createApi(serverUrl, accessToken = token)
                    DisplayPreferencesService.WRITE_LOCK.withLock {
                        val current =
                            api.displayPreferencesApi
                                .getDisplayPreferences(
                                    displayPreferencesId = DisplayPreferencesService.DEFAULT_DISPLAY_PREF_ID,
                                    userId = userId,
                                    client = DisplayPreferencesService.DEFAULT_CLIENT,
                                ).content
                        val customPrefs =
                            current.customPrefs.toMutableMap().apply {
                                put(KEY, (index ?: AUTOMATIC).toString())
                            }
                        api.displayPreferencesApi.updateDisplayPreferences(
                            displayPreferencesId = DisplayPreferencesService.DEFAULT_DISPLAY_PREF_ID,
                            userId = userId,
                            client = DisplayPreferencesService.DEFAULT_CLIENT,
                            data = current.copy(customPrefs = customPrefs),
                        )
                    }
                    uploaded
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not save the profile picture to the server")
                    false
                }
            }

        companion object {
            const val KEY = "benflix.avatar"
            private const val PREFS = "benflix_avatar_sync"
            private const val AUTOMATIC = -1
        }
    }
