package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.RequestApi
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.main.OrderedSeen
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Benflix: how many family requests are waiting for the admin's yes or no. Shown as a small note on
 * Requests in the profile menu. The Requests page updates it right away after approving or declining.
 *
 * The white dot on your picture only counts the waiting ones you haven't seen yet ([unseen]): opening
 * Requests (or "Mark all as seen" in the profile menu) counts them as seen, so the dot goes away and only
 * comes back when someone asks for something new.
 */
object RequestsWaiting {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    private val _unseen = MutableStateFlow(0)

    /** Waiting requests the admin hasn't seen yet (these light up the dot) */
    val unseen: StateFlow<Int> = _unseen

    private val _lookYourself = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Admin: approved requests that have been "still looking" for [LOOK_YOURSELF_DAYS] days or more with nothing found,
     * so Ben knows to start hunting for them himself. By "MOVIE-123" / "TV-456" key.
     */
    val lookYourself: StateFlow<Set<String>> = _lookYourself

    private val _lookYourselfUnseen = MutableStateFlow(0)

    /** Ones of [lookYourself] the admin hasn't seen on Requests yet (these light up the dot too) */
    val lookYourselfUnseen: StateFlow<Int> = _lookYourselfUnseen

    /** Days of "Still looking" before a request is flagged for Ben to look for himself (Ben: "maybe 2 weeks") */
    const val LOOK_YOURSELF_DAYS = 14L

    private var prefs: SharedPreferences? = null
    private var userKey: String = "none"
    private var ids: List<Int> = emptyList()

    /** Which profile is looking (each profile remembers what it has seen) */
    fun switchTo(
        context: Context,
        user: String?,
    ) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        userKey = user ?: "none"
        ids = emptyList()
        _count.value = 0
        _unseen.value = 0
        _lookYourself.value = emptySet()
        _lookYourselfUnseen.value = 0
    }

    private fun seenKey() = "waiting-seen-$userKey"

    private fun lookSeenKey() = "look-yourself-seen-$userKey"

    /** Admin: the requests stuck long enough for Ben to look for them himself, right now */
    fun setLookYourself(keys: Set<String>) {
        _lookYourself.value = keys
        val seen = prefs?.let { OrderedSeen.read(it, lookSeenKey()).toSet() }.orEmpty()
        _lookYourselfUnseen.value = keys.count { it !in seen }
    }

    /** The waiting requests right now, by request id */
    fun setWaiting(waiting: List<Int>) {
        ids = waiting.distinct()
        _count.value = ids.size
        val seen = prefs?.let { OrderedSeen.read(it, seenKey()).toSet() }.orEmpty()
        _unseen.value = ids.count { it.toString() !in seen }
    }

    /** Only the number changed (one was approved or declined, or put back) */
    fun set(count: Int) {
        _count.value = count.coerceAtLeast(0)
        _unseen.value = _unseen.value.coerceAtMost(_count.value)
    }

    /** Everything waiting now counts as seen, so the dot goes */
    fun markSeen() {
        prefs?.let { p -> if (ids.isNotEmpty()) OrderedSeen.add(p, seenKey(), ids.map { it.toString() }, MAX_SEEN) }
        _unseen.value = 0
        val look = _lookYourself.value
        prefs?.let { p -> if (look.isNotEmpty()) OrderedSeen.add(p, lookSeenKey(), look.toList(), MAX_SEEN) }
        _lookYourselfUnseen.value = 0
    }

    private const val PREFS = "benflix_dot"
    private const val MAX_SEEN = 500
}

/** Checks the waiting count every few minutes while Benflix is open, for admins only */
@HiltViewModel
class RequestsBadgeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                serverRepository.currentUserDtoFlow
                    .map { it?.id to (it?.policy?.isAdministrator == true) }
                    .distinctUntilChanged()
                    .collectLatest {
                        // Benflix: a different profile never sees the last person's number, and gets a fresh count straight away
                        RequestsWaiting.switchTo(context, it.first?.toString())
                        delay(1_000)
                        while (isActive) {
                            refresh()
                            delay(5 * 60_000L)
                        }
                    }
            }
        }

        private suspend fun refresh() {
            try {
                val admin = serverRepository.currentUserDto?.policy?.isAdministrator == true
                if (!admin || !seerrService.active.first()) {
                    RequestsWaiting.setWaiting(emptyList())
                    return
                }
                val waiting =
                    seerrService.api.requestApi
                        .requestGet(take = 100, skip = 0, filter = RequestApi.FilterRequestGet.PENDING)
                        .results
                        .orEmpty()
                        .filter { RequestStatus.from(it.status) == RequestStatus.PENDING }
                        .mapNotNull { it.id }
                RequestsWaiting.setWaiting(waiting)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not count waiting requests")
            }
        }
    }
