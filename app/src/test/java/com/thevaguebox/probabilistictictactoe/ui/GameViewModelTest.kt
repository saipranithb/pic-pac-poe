package com.thevaguebox.probabilistictictactoe.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.thevaguebox.picpac.core.PicPacPhase
import com.thevaguebox.picpac.core.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `local turn hands off then reveals before placement`() {
        val viewModel = GameViewModel(application, SavedStateHandle())
        viewModel.startPicPacLocal()
        assertEquals(TurnStage.HANDOFF, viewModel.uiState.value.stage)

        viewModel.readyForReveal()
        val revealed = viewModel.uiState.value
        assertEquals(TurnStage.REVEALING, revealed.stage)
        assertNotNull(revealed.heldSymbol)
        assertEquals(9, revealed.picPac!!.hiddenTotal)

        viewModel.revealAnimationFinished()
        assertEquals(TurnStage.PLAYING, viewModel.uiState.value.stage)
        viewModel.place(4)
        assertEquals(1, viewModel.uiState.value.board.occupiedCount)
        assertEquals(TurnStage.HANDOFF, viewModel.uiState.value.stage)
    }

    @Test fun `rapid duplicate placement is accepted once`() {
        val viewModel = GameViewModel(application, SavedStateHandle())
        viewModel.startPicPacLocal()
        viewModel.readyForReveal()
        viewModel.revealAnimationFinished()
        viewModel.place(0)
        viewModel.place(1)
        assertEquals(1, viewModel.uiState.value.board.occupiedCount)
    }

    @Test fun `rematch alternates starting player`() {
        val viewModel = GameViewModel(application, SavedStateHandle())
        viewModel.startClassic()
        assertEquals(Player.ONE, viewModel.uiState.value.activePlayer)
        viewModel.rematch()
        assertEquals(Player.TWO, viewModel.uiState.value.activePlayer)
        viewModel.rematch()
        assertEquals(Player.ONE, viewModel.uiState.value.activePlayer)
    }

    @Test fun `saved held piece survives recreation`() {
        val handle = SavedStateHandle()
        val original = GameViewModel(application, handle)
        original.startPicPacLocal()
        original.readyForReveal()
        val before = original.uiState.value.picPac!!
        val restoredViewModel = GameViewModel(application, handle)
        val restored = restoredViewModel.uiState.value.picPac!!
        assertEquals(before, restored)
        assertEquals(TurnStage.REVEALING, restoredViewModel.uiState.value.stage)
        assertTrue(restored.phase is PicPacPhase.AwaitingPlacement)
    }
}
