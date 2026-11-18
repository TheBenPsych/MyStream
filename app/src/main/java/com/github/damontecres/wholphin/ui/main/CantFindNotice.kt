package com.github.damontecres.wholphin.ui.main

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixCantFind
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Benflix: "Couldn't find it". People see it on their Requests page. This only runs the week-old
 * "Still looking" check from Ben's own device, so it happens even if he never opens Requests.
 */
@HiltViewModel
class CantFindNoticeViewModel
    @Inject
    constructor(
        private val cantFind: BenflixCantFind,
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                seerrServerRepository.currentUserId
                    .combine(KidsMode.active) { id, kids -> if (kids) null else id }
                    .collectLatest { seerrUserId ->
                        val admin = serverRepository.currentUserDto?.policy?.isAdministrator == true
                        if (seerrUserId != null && admin && seerrService.active.first()) {
                            delay(20_000)
                            cantFind.autoCheck()
                        }
                    }
            }
        }
    }

/** Sits on every page (draws nothing): starts Ben's auto "Couldn't find it" check */
@Composable
fun CantFindLayer() {
    hiltViewModel<CantFindNoticeViewModel>()
}
