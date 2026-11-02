package com.github.damontecres.wholphin.ui.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Benflix: asks Home to start on the banner (scrolled to the top, banner selected).
 * Used when you press Home in the menu, and when you come back after being away longer than the
 * "Ask Who's watching again after" time.
 */
object HomeJump {
    private val _requests = MutableStateFlow(0)
    val requests: StateFlow<Int> = _requests

    @Volatile private var pending = false

    fun request() {
        pending = true
        _requests.value = _requests.value + 1
    }

    /** True once per request */
    fun consume(): Boolean {
        val was = pending
        pending = false
        return was
    }
}
