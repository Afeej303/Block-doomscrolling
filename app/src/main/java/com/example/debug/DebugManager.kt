package com.example.debug

import kotlinx.coroutines.flow.MutableStateFlow

data class DebugState(
    val isEnabled: Boolean = false,
    val currentPackage: String = "",
    val currentActivity: String = "",
    val viewIds: String = "",
    val texts: String = "",
    val contentDescriptions: String = "",
    val matchedRule: String = "",
    val reason: String = "",
    val actionTaken: String = ""
)

object DebugManager {
    val state = MutableStateFlow(DebugState())

    fun toggleDebugMode() {
        state.value = state.value.copy(isEnabled = !state.value.isEnabled)
    }

    fun updateInfo(
        currentPackage: String = state.value.currentPackage,
        currentActivity: String = state.value.currentActivity,
        viewIds: String = state.value.viewIds,
        texts: String = state.value.texts,
        contentDescriptions: String = state.value.contentDescriptions,
        matchedRule: String = state.value.matchedRule,
        reason: String = state.value.reason,
        actionTaken: String = state.value.actionTaken
    ) {
        if (!state.value.isEnabled) return
        state.value = state.value.copy(
            currentPackage = currentPackage,
            currentActivity = currentActivity,
            viewIds = viewIds,
            texts = texts,
            contentDescriptions = contentDescriptions,
            matchedRule = matchedRule,
            reason = reason,
            actionTaken = actionTaken
        )
    }
}
