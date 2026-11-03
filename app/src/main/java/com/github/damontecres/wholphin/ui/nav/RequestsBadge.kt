package com.github.damontecres.wholphin.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.RequestApi
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * Benflix: how many family requests are waiting for the admin's yes or no. Shown as a small red number on
 * Requests in the menus. The Requests page updates it right away after approving or declining.
 */
object RequestsWaiting {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    fun set(count: Int) {
        _count.value = count.coerceAtLeast(0)
    }
}

/** Checks the waiting count every few minutes while Benflix is open, for admins only */
@HiltViewModel
class RequestsBadgeViewModel
    @Inject
    constructor(
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
                        RequestsWaiting.set(0)
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
                    RequestsWaiting.set(0)
                    return
                }
                val waiting =
                    seerrService.api.requestApi
                        .requestGet(take = 100, skip = 0, filter = RequestApi.FilterRequestGet.PENDING)
                        .results
                        .orEmpty()
                        .count { RequestStatus.from(it.status) == RequestStatus.PENDING }
                RequestsWaiting.set(waiting)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not count waiting requests")
            }
        }
    }
