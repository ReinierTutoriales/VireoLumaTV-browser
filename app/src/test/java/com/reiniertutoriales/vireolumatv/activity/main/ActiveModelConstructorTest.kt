package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModel
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveModelConstructorTest {
    class OverloadedModel @JvmOverloads constructor(val initial: Int = 42) : ScreenModel()

    @Test fun repositoryExplicitlyUsesTheNoArgumentConstructor() {
        val user = Any()
        val model = ScreenModels.get(OverloadedModel::class, user)
        try { assertEquals(42, model.initial) }
        finally { ScreenModels.discard(model, user) }
    }
}
