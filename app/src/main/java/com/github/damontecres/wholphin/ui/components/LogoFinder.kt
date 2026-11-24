package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Ben 2026-11-24 (missing logos): a movie or show details page with no title logo, or one that won't load, asks
 * Tower for one. Tower says if Jellyfin has a working logo, or puts on the best English one from TMDB / TheTVDB /
 * Fanart.tv right then (never replacing one that works). Each title is asked about once while Glowfin is open.
 */
object LogoFinder {
    private const val NONE = ""
    private val answers = ConcurrentHashMap<UUID, String>()

    suspend fun find(
        helper: GameLibraryClient,
        images: ImageUrlService,
        id: UUID,
    ): String? {
        answers[id]?.let { return it.ifEmpty { null } }
        val url =
            try {
                val json = helper.postJson("/api/posters/logo?id=${id.toString().replace("-", "")}")
                if (json.optBoolean("has")) {
                    images.getItemImageUrl(
                        itemId = id,
                        imageType = ImageType.LOGO,
                        tag = json.optString("tag").ifBlank { null },
                    )
                } else {
                    null
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // An older Tower without this, or Tower can't be reached: the name shows, like before
                Timber.d(ex, "Logo finder")
                return null
            }
        answers[id] = url ?: NONE
        return url
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LogoFinderEntryPoint {
    fun helper(): GameLibraryClient
}

private fun logoEntry(context: Context): LogoFinderEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, LogoFinderEntryPoint::class.java)

/** For a movie or show's details page: asks Tower for a logo when it has none (null for anything else) */
@Composable
fun rememberLogoFinder(item: BaseItem?): (suspend () -> String?)? {
    val context = LocalContext.current
    val images = LocalImageUrlService.current
    val id = item?.id
    val fits = item?.type == BaseItemKind.MOVIE || item?.type == BaseItemKind.SERIES
    // "Use the name instead" in a title's More list: leave it alone
    if (id == null || !fits || LogoChoice.usesName(id)) return null
    return remember(id, images) {
        val entry = logoEntry(context)
        val finder: suspend () -> String? = { LogoFinder.find(entry.helper(), images, id) }
        finder
    }
}
