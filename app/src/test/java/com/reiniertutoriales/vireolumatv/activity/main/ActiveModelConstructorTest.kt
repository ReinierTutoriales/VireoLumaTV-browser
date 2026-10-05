package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveModelConstructorTest {
    class OverloadedModel @JvmOverloads constructor(val initial: Int = 42) : ActiveModel()

    @Test fun repositoryExplicitlyUsesTheNoArgumentConstructor() {
        val user = Any()
        val model = ActiveModelsRepository.get(OverloadedModel::class, user)
        try { assertEquals(42, model.initial) }
        finally { ActiveModelsRepository.markAsNeedless(model, user) }
    }
}
