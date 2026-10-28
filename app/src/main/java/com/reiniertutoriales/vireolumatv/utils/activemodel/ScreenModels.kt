package com.reiniertutoriales.vireolumatv.utils.activemodel

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import kotlin.reflect.KClass

/**
 * One instance per model class, shared by its users. Activities are released automatically when
 * destroyed; any other user (a service, a view) must call [markAsNeedless] when it is done.
 */
@MainThread
object ScreenModels {
    private class Entry(val model: ScreenModel) {
        val users = ArrayList<Any>()
    }

    private val entries = HashMap<KClass<out ScreenModel>, Entry>()

    fun init(app: Application) = app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        // A rotating Activity is released too, so its old views are not kept; only its models stay
        // until the new Activity asks for them again.
        override fun onActivityDestroyed(activity: Activity) =
            markAsNeedlessAllModelsUsedBy(activity, retainUnused = activity.isChangingConfigurations)

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    })

    /** The shared [clazz] model, created on first use; [user] is recorded as holding it. */
    @Suppress("UNCHECKED_CAST")
    fun <T : ScreenModel> get(clazz: KClass<T>, user: Any): T {
        val entry = entries.getOrPut(clazz) { Entry(clazz.java.getDeclaredConstructor().newInstance()) }
        if (user !in entry.users) entry.users += user
        return entry.model as T
    }

    fun markAsNeedless(model: ScreenModel, byUser: Any) {
        val entry = entries[model::class] ?: return
        if (entry.users.remove(byUser) && entry.users.isEmpty()) {
            entries.remove(model::class)
            entry.model.clear()
        }
    }

    /** Releases every model [user] holds; with [retainUnused] a model left without users is kept. */
    fun markAsNeedlessAllModelsUsedBy(user: Any, retainUnused: Boolean = false) {
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.users.remove(user) && entry.users.isEmpty() && !retainUnused) {
                iterator.remove()
                entry.model.clear()
            }
        }
    }

    /** Makes [model] the shared [clazz] instance; the returned call puts the previous one back. */
    @VisibleForTesting
    fun <T : ScreenModel> replaceForTest(clazz: KClass<T>, model: T): () -> Unit {
        val previous = entries.put(clazz, Entry(model))
        return { if (previous == null) entries.remove(clazz) else entries[clazz] = previous }
    }
}
