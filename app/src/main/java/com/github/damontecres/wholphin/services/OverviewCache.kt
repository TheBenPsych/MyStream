package com.github.damontecres.wholphin.services

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix: Home rows load without description text, which keeps the first load light.
 * The description of the tile you stop on is fetched here (and remembered) instead.
 */
@Singleton
class OverviewCache
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        private val cache = LruCache<UUID, String>(MAX_ITEMS)

        fun cached(itemId: UUID): String? = cache.get(itemId)

        suspend fun get(itemId: UUID): String? {
            cache.get(itemId)?.let { return it }
            return withContext(WholphinDispatchers.IO) {
                try {
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                ids = listOf(itemId),
                                fields = listOf(ItemFields.OVERVIEW),
                                enableImages = false,
                                enableUserData = false,
                            ),
                        ).content.items
                        .firstOrNull()
                        ?.overview
                        ?.also { cache.put(itemId, it) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "Could not get the description of %s", itemId)
                    null
                }
            }
        }

        companion object {
            private const val MAX_ITEMS = 500
        }
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverviewCacheEntryPoint {
    fun overviewCache(): OverviewCache
}

fun overviewCache(context: Context): OverviewCache =
    EntryPointAccessors.fromApplication(context.applicationContext, OverviewCacheEntryPoint::class.java).overviewCache()

/** The item's description: right away if it came with the item, otherwise fetched after a short rest on it */
@Composable
fun rememberOverview(item: BaseItem?): String? {
    val context = LocalContext.current
    val cache = remember { overviewCache(context) }
    val given = item?.data?.overview
    var text by remember(item?.id) { mutableStateOf(given ?: item?.id?.let { cache.cached(it) }) }
    LaunchedEffect(item?.id, given) {
        if (given != null) {
            text = given
        } else if (item != null && text == null) {
            delay(OVERVIEW_REST_MS)
            text = cache.get(item.id)
        }
    }
    return text
}

private const val OVERVIEW_REST_MS = 150L
