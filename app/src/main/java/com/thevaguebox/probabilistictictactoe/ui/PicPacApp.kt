package com.thevaguebox.probabilistictictactoe.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thevaguebox.probabilistictictactoe.settings.AppSettings
import com.thevaguebox.probabilistictictactoe.settings.SettingsStore
import com.thevaguebox.probabilistictictactoe.ui.theme.PicPacTheme
import kotlinx.coroutines.launch

@Composable
fun PicPacApp(viewModel: GameViewModel = viewModel()) {
    val game by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsStore = remember(context) { SettingsStore(context) }
    val settings by settingsStore.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    BackHandler(enabled = game.screen != AppScreen.HOME) { viewModel.goHome() }

    PicPacTheme(settings.theme) {
        FeedbackEffects(game, settings)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = game.screen,
                transitionSpec = {
                    if (settings.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else fadeIn() togetherWith fadeOut()
                },
                label = "screen",
            ) { screen ->
                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        onClassic = viewModel::startClassic,
                        onPicPacLocal = viewModel::startPicPacLocal,
                        onPicPacAi = viewModel::startPicPacAi,
                        onHowTo = { viewModel.show(AppScreen.HOW_TO) },
                        onSettings = { viewModel.show(AppScreen.SETTINGS) },
                        onAiLab = { viewModel.show(AppScreen.AI_LAB) },
                    )
                    AppScreen.GAME -> GameScreen(
                        state = game,
                        reducedMotion = settings.reducedMotion,
                        onHome = viewModel::goHome,
                        onReady = viewModel::readyForReveal,
                        onRevealFinished = viewModel::revealAnimationFinished,
                        onCell = viewModel::place,
                        onRematch = viewModel::rematch,
                    )
                    AppScreen.HOW_TO -> HowToPlayScreen(onBack = viewModel::goHome)
                    AppScreen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onBack = viewModel::goHome,
                        onSound = { scope.launch { settingsStore.setSound(it) } },
                        onHaptics = { scope.launch { settingsStore.setHaptics(it) } },
                        onReducedMotion = { scope.launch { settingsStore.setReducedMotion(it) } },
                        onTheme = { scope.launch { settingsStore.setTheme(it) } },
                    )
                    AppScreen.AI_LAB -> AiLabScreen(onBack = viewModel::goHome)
                }
            }
        }
    }
}

@Composable
private fun FeedbackEffects(state: GameUiState, settings: AppSettings) {
    val haptic = LocalHapticFeedback.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }
    DisposableEffect(tone) { onDispose { tone.release() } }
    LaunchedEffect(state.effectId) {
        val effect = state.effect ?: return@LaunchedEffect
        if (settings.haptics) {
            haptic.performHapticFeedback(
                if (effect == UiEffect.WIN) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
            )
        }
        if (settings.sound) {
            val sound = when (effect) {
                UiEffect.REVEAL -> ToneGenerator.TONE_PROP_BEEP
                UiEffect.PLACE -> ToneGenerator.TONE_PROP_ACK
                UiEffect.WIN -> ToneGenerator.TONE_PROP_BEEP2
                UiEffect.DRAW -> ToneGenerator.TONE_PROP_NACK
            }
            tone.startTone(sound, if (effect == UiEffect.WIN) 180 else 70)
        }
    }
}
