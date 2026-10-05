package com.reiniertutoriales.vireolumatv.utils.activemodel

import org.junit.Assert.*
import org.junit.Test

class ActiveModelsRetentionTest {
    class TestModel : ActiveModel() {
        var released = false
        override fun onClear() { released = true }
    }

    @Test fun recreationRetainsTheModelButReleasesTheDestroyedUser() {
        val oldUser = Any()
        val replacement = Any()
        val model = ActiveModelsRepository.get(TestModel::class, oldUser)
        ActiveModelsRepository.markAsNeedlessAllModelsUsedBy(oldUser, retainUnused = true)
        assertSame(model, ActiveModelsRepository.get(TestModel::class, replacement))
        assertFalse(model.released)
        // If the old user was still retained, releasing the replacement would not clear the model.
        ActiveModelsRepository.markAsNeedlessAllModelsUsedBy(replacement)
        assertTrue(model.released)
    }
}
