package com.github.damontecres.wholphin.ui.main

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Clips on phones: swiping the feed moves between clips, even when the clip itself is tappable */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ClipSwipesTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var pagerState: PagerState
    private var taps = 0

    private fun setFeed() {
        rule.setContent {
            pagerState = rememberPagerState { 5 }
            val scope = rememberCoroutineScope()
            VerticalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("feed")
                        .clipSwipes(pagerState, scope),
            ) {
                // Like a clip card: the whole page is tappable (mute)
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { taps++ },
                )
            }
        }
    }

    @Test
    fun swipeUpGoesToNextClipAndDownGoesBack() {
        setFeed()
        rule.onNodeWithTag("feed").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(1, pagerState.currentPage)
        rule.onNodeWithTag("feed").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(2, pagerState.currentPage)
        rule.onNodeWithTag("feed").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(1, pagerState.currentPage)
        assertEquals(0, taps)
    }

    @Test
    fun cannotSwipeAboveTheFirstClip() {
        setFeed()
        rule.onNodeWithTag("feed").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(0, pagerState.currentPage)
    }

    @Test
    fun tapStillReachesTheClip() {
        setFeed()
        rule.onNodeWithTag("feed").performClick()
        rule.waitForIdle()
        assertEquals(1, taps)
        assertEquals(0, pagerState.currentPage)
    }
}
