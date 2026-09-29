package com.thevaguebox.probabilistictictactoe

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thevaguebox.probabilistictictactoe.settings.AppSettings
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference
import com.thevaguebox.probabilistictictactoe.ui.SettingsScreen
import com.thevaguebox.probabilistictictactoe.ui.theme.PicPacTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPrivacyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun policyOpensOnlyOnRequestAndOfflineSummaryRemainsReadableAtLargeText() {
        val theme = mutableStateOf(ThemePreference.DARK)
        val openedUris = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) { openedUris += uri }
        }
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalDensity provides Density(constraints.maxWidth / 320f, 2f),
                    LocalUriHandler provides handler,
                ) {
                    key(theme.value) {
                        PicPacTheme(theme.value, reducedMotion = true) {
                            Surface(Modifier.fillMaxSize()) {
                                SettingsScreen(AppSettings(theme = theme.value), {}, {}, {}, {}, {})
                            }
                        }
                    }
                }
            }
        }
        for ((index, preference) in listOf(ThemePreference.DARK, ThemePreference.LIGHT).withIndex()) {
            compose.runOnIdle { theme.value = preference }
            compose.onNodeWithText(OFFLINE_SUMMARY).performScrollTo().assertIsDisplayed()
            compose.runOnIdle { assertEquals(index, openedUris.size) }
            val link = compose.onNodeWithText("Privacy policy")
            link.performScrollTo().assertIsDisplayed().assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val bounds = link.fetchSemanticsNode().boundsInRoot
            val density = compose.onRoot().fetchSemanticsNode().boundsInRoot.width / 320f
            assertTrue("Link needs a 48dp touch target", bounds.height / density >= 48f)
            captureSettledDevice("settings-privacy-320dp-200pct-${preference.name.lowercase()}")
            link.performClick()
            compose.runOnIdle {
                assertEquals(List(index + 1) { "https://saipranith.dev/picpacpoe/privacy" }, openedUris)
            }
            compose.onNodeWithText(OFFLINE_SUMMARY).performScrollTo().assertIsDisplayed()
        }
    }

    private companion object {
        const val OFFLINE_SUMMARY = "Everything stays on this device. No account, ads, analytics, or Internet permission."
    }
}
