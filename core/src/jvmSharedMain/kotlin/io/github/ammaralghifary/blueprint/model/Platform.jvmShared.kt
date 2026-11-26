package io.github.ammaralghifary.blueprint.model

internal actual fun <T : Any> threadSlot(): ThreadSlot<T> = object : ThreadSlot<T> {
    private val local = ThreadLocal<T?>()

    override var value: T?
        get() = local.get()
        set(value) = local.set(value)
}

internal actual fun Throwable.isStackOverflow(): Boolean = this is StackOverflowError
