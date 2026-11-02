package com.github.damontecres.wholphin.services

import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.InterfacePreferences
import com.github.damontecres.wholphin.preferences.PlaybackPreferences
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix "Settings follow you": a person's own settings (subtitle look, skipping, autoplay,
 * profile colour, late-night look and so on) are saved to their Jellyfin account. When they
 * sign in on another TV or phone, or pick their profile on a shared TV, their settings come back.
 *
 * Things that only make sense on one device (player choice, Lite mode, download folders) are not synced.
 * Changes made while the Kids profile is open are never saved to the grown-up's account.
 */
@Singleton
class BenflixSettingsSync
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val displayPreferencesService: DisplayPreferencesService,
        private val preferencesDataStore: DataStore<AppPreferences>,
        private val serverRepository: ServerRepository,
    ) {
        /** The user whose saved settings have been loaded on this device (uploads wait until then) */
        @Volatile private var readyUserId: UUID? = null

        private val _ready = MutableStateFlow<UUID?>(null)

        /** The person whose saved settings have finished loading (the welcome setup waits for this) */
        val ready: StateFlow<UUID?> = _ready

        @Volatile private var lastSynced: String? = null

        /** Called when someone signs in or a profile is picked */
        suspend fun restoreFor(userId: UUID) {
            readyUserId = null
            _ready.value = null
            var nextUpMoved = true
            try {
                val customPrefs =
                    displayPreferencesService
                        .getDisplayPreferences(userId)
                        .customPrefs
                nextUpMoved = customPrefs[NEXT_UP_MOVED_KEY] != null
                val saved = customPrefs[KEY]
                if (saved != null) {
                    val parsed = AppPreferences.parseFrom(Base64.decode(saved, Base64.NO_WRAP))
                    var changed = false
                    preferencesDataStore.updateData { current ->
                        val merged = current.withPersonal(parsed)
                        changed = merged.personalPart() != current.personalPart()
                        merged
                    }
                    lastSynced = saved
                    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val shownKey = "restored_$userId"
                    if (changed && !prefs.getBoolean(shownKey, false)) {
                        prefs.edit { putBoolean(shownKey, true) }
                        showToast(context, context.getString(R.string.benflix_settings_restored), Toast.LENGTH_LONG)
                    }
                } else {
                    lastSynced = null
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not load saved Benflix settings")
                lastSynced = null
            }
            if (!nextUpMoved) moveNextUpCountdown(userId)
            readyUserId = userId
            _ready.value = userId
            // Save this device's settings as theirs if they have none saved yet
            upload(preferencesDataStore.data.first())
        }

        /**
         * The next-episode countdown used to start at 15 seconds. Ben wanted 5, so anyone still on the
         * old 15 is moved to 5 once (someone who picks 15 again later keeps it).
         */
        private suspend fun moveNextUpCountdown(userId: UUID) {
            try {
                preferencesDataStore.updateData { current ->
                    if (current.playbackPreferences.autoPlayNextDelaySeconds == OLD_NEXT_UP_SECONDS) {
                        current
                            .toBuilder()
                            .setPlaybackPreferences(
                                current.playbackPreferences
                                    .toBuilder()
                                    .setAutoPlayNextDelaySeconds(AppPreference.AutoPlayNextDelay.defaultValue)
                                    .build(),
                            ).build()
                    } else {
                        current
                    }
                }
                displayPreferencesService.updateDisplayPreferences(userId) {
                    put(NEXT_UP_MOVED_KEY, "1")
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not move the next episode countdown")
            }
        }

        /** Watches for setting changes and saves them to the person's account a few seconds later */
        @OptIn(FlowPreview::class)
        suspend fun watchForChanges() {
            preferencesDataStore.data
                .distinctUntilChanged { old, new -> old.personalPart() == new.personalPart() }
                .debounce(3_000)
                .collect { upload(it) }
        }

        private suspend fun upload(prefs: AppPreferences) {
            val userId = serverRepository.currentUser?.id ?: return
            if (userId != readyUserId || KidsMode.isActive) return
            val encoded = Base64.encodeToString(prefs.personalPart().toByteArray(), Base64.NO_WRAP)
            if (encoded == lastSynced) return
            try {
                displayPreferencesService.updateDisplayPreferences(userId) {
                    put(KEY, encoded)
                }
                lastSynced = encoded
                Timber.i("Saved Benflix settings to the account")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not save Benflix settings")
            }
        }

        companion object {
            private const val KEY = "benflix.settings.v1"
            private const val PREFS = "benflix_settings_sync"
            private const val NEXT_UP_MOVED_KEY = "benflix.nextup5"
            private const val OLD_NEXT_UP_SECONDS = 15L
        }
    }

/** Just the settings that belong to the person (not the device) */
fun AppPreferences.personalPart(): AppPreferences {
    val playback = playbackPreferences
    val ui = interfacePreferences
    return AppPreferences
        .newBuilder()
        .setBenflixPreferences(benflixPreferences)
        .setPlaybackPreferences(
            PlaybackPreferences
                .newBuilder()
                .setSkipForwardMs(playback.skipForwardMs)
                .setSkipBackMs(playback.skipBackMs)
                .setSkipBackOnResumeSeconds(playback.skipBackOnResumeSeconds)
                .setAutoPlayNext(playback.autoPlayNext)
                .setAutoPlayNextDelaySeconds(playback.autoPlayNextDelaySeconds)
                .setPassOutProtectionMs(playback.passOutProtectionMs)
                .setShowNextUpWhenValue(playback.showNextUpWhenValue)
                .setSkipIntrosValue(playback.skipIntrosValue)
                .setSkipOutrosValue(playback.skipOutrosValue)
                .setSkipCommercialsValue(playback.skipCommercialsValue)
                .setSkipRecapsValue(playback.skipRecapsValue)
                .setSkipPreviewsValue(playback.skipPreviewsValue)
                .build(),
        ).setInterfacePreferences(
            InterfacePreferences
                .newBuilder()
                .setSubtitlesPreferences(ui.subtitlesPreferences)
                .setPlayThemeSongsValue(ui.playThemeSongsValue)
                .setShowClock(ui.showClock)
                .setAppThemeColorsValue(ui.appThemeColorsValue)
                .build(),
        ).build()
}

/** These settings with the person's own settings from [saved] put on top */
fun AppPreferences.withPersonal(saved: AppPreferences): AppPreferences {
    val p = saved.playbackPreferences
    val ui = saved.interfacePreferences
    return toBuilder()
        .setBenflixPreferences(saved.benflixPreferences)
        .setPlaybackPreferences(
            playbackPreferences
                .toBuilder()
                .setSkipForwardMs(p.skipForwardMs)
                .setSkipBackMs(p.skipBackMs)
                .setSkipBackOnResumeSeconds(p.skipBackOnResumeSeconds)
                .setAutoPlayNext(p.autoPlayNext)
                .setAutoPlayNextDelaySeconds(p.autoPlayNextDelaySeconds)
                .setPassOutProtectionMs(p.passOutProtectionMs)
                .setShowNextUpWhenValue(p.showNextUpWhenValue)
                .setSkipIntrosValue(p.skipIntrosValue)
                .setSkipOutrosValue(p.skipOutrosValue)
                .setSkipCommercialsValue(p.skipCommercialsValue)
                .setSkipRecapsValue(p.skipRecapsValue)
                .setSkipPreviewsValue(p.skipPreviewsValue)
                .build(),
        ).setInterfacePreferences(
            interfacePreferences
                .toBuilder()
                .setSubtitlesPreferences(ui.subtitlesPreferences)
                .setPlayThemeSongsValue(ui.playThemeSongsValue)
                .setShowClock(ui.showClock)
                .setAppThemeColorsValue(ui.appThemeColorsValue)
                .build(),
        ).build()
}
