package io.github.ammaralghifary.blueprint.model

import kotlin.native.concurrent.ThreadLocal

/**
 * Every slot's value for the running thread. A cleared slot is removed, so nothing outlives its call.
 *
 * @author Ammar Al Ghifary
 */
@ThreadLocal
private object ThreadSlots {
    val values = HashMap<ThreadSlot<*>, Any>()
}

internal actual fun <T : Any> threadSlot(): ThreadSlot<T> = object : ThreadSlot<T> {
    @Suppress("UNCHECKED_CAST") // only this slot's setter writes under its key
    override var value: T?
        get() = ThreadSlots.values[this] as T?
        set(value) {
            if (value == null) ThreadSlots.values.remove(this) else ThreadSlots.values[this] = value
        }
}

internal actual fun Throwable.isStackOverflow(): Boolean = false
