package com.reiniertutoriales.vireolumatv.activity.main

/** Visible state of the rules currently protecting the selected tab. */
internal enum class BlockerIndicator { PAUSED, LOADING, REFRESHING, ACTIVE, PARTIAL, SAVED, UNAVAILABLE }

internal fun blockerIndicator(
    enabled: Boolean,
    rulesReady: Boolean,
    loading: Boolean,
    result: AdblockModel.UpdateResult,
): BlockerIndicator = when {
    !enabled -> BlockerIndicator.PAUSED
    !rulesReady && loading -> BlockerIndicator.LOADING
    !rulesReady -> BlockerIndicator.UNAVAILABLE
    loading -> BlockerIndicator.REFRESHING
    result == AdblockModel.UpdateResult.PARTIAL -> BlockerIndicator.PARTIAL
    result == AdblockModel.UpdateResult.CACHED ||
        result == AdblockModel.UpdateResult.ERROR -> BlockerIndicator.SAVED
    else -> BlockerIndicator.ACTIVE
}
