package com.wiggyserver.immichwallpaper.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** First-run setup: server → sources → offline set → background. */
enum class OnboardingStep {
    SERVER, SOURCES, OFFLINE_SET, BACKGROUND;

    val number: Int get() = ordinal + 1
}

class OnboardingModel(private val app: AppPort) {
    private val _step = MutableStateFlow(OnboardingStep.SERVER)
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    /** Advances; on the last step, marks setup as done (the app then shows Home). */
    fun next() {
        val s = _step.value
        if (s == OnboardingStep.entries.last()) app.updateConfig { it.copy(onboarded = true) } else _step.value = OnboardingStep.entries[s.ordinal + 1]
    }

    /** False on the first step (nothing to go back to). */
    fun back(): Boolean {
        val s = _step.value
        if (s.ordinal == 0) return false
        _step.value = OnboardingStep.entries[s.ordinal - 1]
        return true
    }
}
