package com.reiniertutoriales.vireolumatv.activity.main

/** Visible state of the rules currently protecting the selected tab. */
internal enum class BlockerIndicator { PAUSED, LOADING, REFRESHING, ACTIVE, PARTIAL, SAVED, UNAVAILABLE }

internal fun blockerIndicator(
    enabled: Boolean,
    rulesReady: Boolean,
    loading: Boolean,
    result: AdBlocker.UpdateResult,
): BlockerIndicator = when {
    !enabled -> BlockerIndicator.PAUSED
    !rulesReady && loading -> BlockerIndicator.LOADING
    !rulesReady -> BlockerIndicator.UNAVAILABLE
    loading -> BlockerIndicator.REFRESHING
    result == AdBlocker.UpdateResult.PARTIAL -> BlockerIndicator.PARTIAL
    result == AdBlocker.UpdateResult.CACHED ||
        result == AdBlocker.UpdateResult.ERROR -> BlockerIndicator.SAVED
    else -> BlockerIndicator.ACTIVE
}
