package com.reiniertutoriales.vireolumatv.utils.activemodel

import org.junit.Assert.*
import org.junit.Test

class ActiveModelsRetentionTest {
    class TestModel : ScreenModel() {
        var released = false
        override fun onClear() { released = true }
    }

    @Test fun recreationRetainsTheModelButReleasesTheDestroyedUser() {
        val oldUser = Any()
        val replacement = Any()
        val model = ScreenModels.get(TestModel::class, oldUser)
        ScreenModels.markAsNeedlessAllModelsUsedBy(oldUser, retainUnused = true)
        assertSame(model, ScreenModels.get(TestModel::class, replacement))
        assertFalse(model.released)
        // If the old user was still retained, releasing the replacement would not clear the model.
        ScreenModels.markAsNeedlessAllModelsUsedBy(replacement)
        assertTrue(model.released)
    }
}
