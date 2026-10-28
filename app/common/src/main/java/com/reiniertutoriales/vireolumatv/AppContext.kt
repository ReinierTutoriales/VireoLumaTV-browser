package com.reiniertutoriales.vireolumatv

import android.app.Application
import android.content.Context

/** The application and its settings, for code without a context of its own. Set once at start-up. */
object AppContext {
    private var application: Application? = null
    private var settings: Config? = null

    fun init(application: Application, config: Config) {
        this.application = application
        settings = config
    }

    val context: Context get() = checkNotNull(application) { "AppContext.init was not called" }
    val config: Config get() = checkNotNull(settings) { "AppContext.init was not called" }
}
