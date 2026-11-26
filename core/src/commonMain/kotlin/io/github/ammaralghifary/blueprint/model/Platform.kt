package io.github.ammaralghifary.blueprint.model

/**
 * A value each thread sees its own copy of. Null means the thread holds nothing.
 *
 * @author Ammar Al Ghifary
 */
internal interface ThreadSlot<T : Any> {
    var value: T?
}

internal expect fun <T : Any> threadSlot(): ThreadSlot<T>

/**
 * True when this is a stack overflow the program can recover from. Only the JVM throws one; on
 * Kotlin/Native an overflow ends the process, so the payload depth limit is what protects it there.
 */
internal expect fun Throwable.isStackOverflow(): Boolean
