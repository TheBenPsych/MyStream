package com.github.damontecres.wholphin.ui.fit

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The fit robot on a pretend 1080p TV (960 x 540 dp at 2x). See [FitRobot]. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvFitRobotTest(
    private val scene: String,
) {
    @get:Rule(order = 1)
    val tv =
        object : TestWatcher() {
            override fun starting(description: Description?) = FitRobot.pretendTv(true)
        }

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<FitActivity>()

    @Test
    fun allTextFits() = runScene(rule, "tv", scene, tv = true)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun scenes(): List<Array<Any>> = FitScenes.all().map { arrayOf(it.name) }
    }
}

/** The fit robot on a pretend phone (411 x 891 dp). See [FitRobot]. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PhoneFitRobotTest(
    private val scene: String,
) {
    @get:Rule(order = 1)
    val phone =
        object : TestWatcher() {
            override fun starting(description: Description?) = FitRobot.pretendTv(false)
        }

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<FitActivity>()

    @Test
    fun allTextFits() = runScene(rule, "phone", scene, tv = false)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun scenes(): List<Array<Any>> = FitScenes.all().filter { it.focusText == null }.map { arrayOf(it.name) }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun runScene(
    rule: AndroidComposeTestRule<ActivityScenarioRule<FitActivity>, FitActivity>,
    device: String,
    name: String,
    tv: Boolean,
) {
    // Only runs when asked for (the fit-robot workflow), so normal PR checks stay quick
    Assume.assumeTrue("fit robot is off (run with -PbenflixFit=1)", System.getProperty("benflix.fit") == "1")
    val scene = FitScenes.all().first { it.name == name }
    FitRobot.run(
        rule = rule,
        device = device,
        scene = name,
        content = { FitFrame(tv = tv) { scene.content() } },
        mustShow = scene.mustShow,
        after =
            scene.focusText?.let { words ->
                {
                    val inside = hasClickAction().and(hasAnyDescendant(hasText(words, substring = true)))
                    // Episode pictures have their name under the picture, not on it
                    val beside = hasClickAction().and(hasParent(hasAnyDescendant(hasText(words, substring = true))))
                    val direct = rule.onAllNodes(inside, useUnmergedTree = true).fetchSemanticsNodes()
                    rule
                        .onAllNodes(if (direct.isNotEmpty()) inside else beside, useUnmergedTree = true)
                        .onFirst()
                        .requestFocus()
                }
            },
    )
}
