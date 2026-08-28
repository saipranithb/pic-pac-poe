package com.thevaguebox.probabilistictictactoe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun localModeRequiresReadyBeforeReveal() {
        compose.onNodeWithText("Pic-Pac Local").performClick()
        compose.onNodeWithText("Player 1, you're up.").assertIsDisplayed()
        compose.onNodeWithText("Ready").performClick()
        compose.onNodeWithText("You drew", substring = true).assertIsDisplayed()
    }

    @Test fun tutorialExplainsPlayerSymbolSeparation() {
        compose.onNodeWithText("How to play").performClick()
        compose.onNodeWithText("You're not X. You're not O.").assertIsDisplayed()
    }
}
