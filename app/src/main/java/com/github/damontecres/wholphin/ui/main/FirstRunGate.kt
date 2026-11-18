package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BenflixDeviceCheck
import com.github.damontecres.wholphin.services.BenflixSettingsSync
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.DisplayPreferencesService
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.ProfilePictureSync
import com.github.damontecres.wholphin.ui.setup.AvatarChoices
import com.github.damontecres.wholphin.ui.setup.BenflixAvatars
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import javax.inject.Inject

/**
 * Glowfin has no first-time setup any more (Ben 2026-11-18). Instead, quietly, the first time Home opens:
 *  - on a new TV or phone: the device check picks the right speed settings (leaving alone anything set by hand)
 *  - for a profile with no picture yet: a random sea friend, which they can change in Settings
 */
@HiltViewModel
class FirstRunViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val preferences: DataStore<AppPreferences>,
        private val displayPreferencesService: DisplayPreferencesService,
        private val settingsSync: BenflixSettingsSync,
        private val profilePictureSync: ProfilePictureSync,
        private val deviceProfileService: DeviceProfileService,
    ) : ViewModel() {
        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun check() {
            if (!deviceChecked) {
                deviceChecked = true
                viewModelScope.launch(WholphinDispatchers.IO) { checkDevice() }
            }
            if (KidsMode.isActive || GuestMode.isActive) return
            val current = serverRepository.current.value ?: return
            val userId = current.user.id
            if (userId in checked) return
            checked += userId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    // Wait for this person's saved settings (and their picture from the server) first
                    withTimeoutOrNull(SYNC_WAIT_MS) { settingsSync.ready.first { it == userId } }
                    val custom = displayPreferencesService.getDisplayPreferences(userId).customPrefs
                    if (custom[ProfilePictureSync.KEY] != null) return@launch
                    val here =
                        withContext(WholphinDispatchers.Main) {
                            AvatarChoices.init(context)
                            AvatarChoices.get(userId)
                        }
                    if (here != null) return@launch
                    // A photo they put on their Jellyfin account themselves stays
                    if (api.userApi
                            .getCurrentUser()
                            .content.primaryImageTag != null
                    ) {
                        return@launch
                    }
                    profilePictureSync.pick(current.server.url, current.user, BenflixAvatars.indices.random())
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "First run: couldn't give a sea friend")
                }
            }
        }

        private suspend fun checkDevice() {
            if (prefs.getBoolean(DEVICE_KEY, false)) return
            try {
                val report = BenflixDeviceCheck.detect(context, deviceProfileService.mediaCodecCapabilitiesTest)
                BenflixDeviceCheck.apply(context, preferences, report)
                prefs.edit { putBoolean(DEVICE_KEY, true) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "First run: device check failed")
            }
        }

        companion object {
            private const val SYNC_WAIT_MS = 8_000L
            private const val PREFS = "benflix_first_run"
            private const val DEVICE_KEY = "device-checked"

            /** Checked since the app started */
            @Volatile private var deviceChecked = false
            private val checked = mutableSetOf<UUID>()
        }
    }

/** Put on Home: the quiet first-run jobs (draws nothing) */
@Composable
fun FirstRunGate(viewModel: FirstRunViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.check() }
}
