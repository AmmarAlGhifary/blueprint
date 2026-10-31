package io.github.ammaralghifary.blueprint

import kotlinx.serialization.json.JsonObject

/** What [Blueprint] tolerated instead of failing the screen. */
public enum class DecodeErrorKind {
    /** The node's `type` is not in the registry, or is missing. Expected on an older app. */
    UnregisteredType,

    /** A registered component whose fields do not fit its model; it became an [UnknownComponent]. */
    InvalidComponent,

    /** A registered action whose fields do not fit its model; it became an [UnknownAction]. */
    InvalidAction,

    /** A registered component kept, but its malformed `modifier` was dropped. */
    ModifierIgnored,

    /** The screen itself was unreadable (bad JSON, missing body, too deep) or its metadata was dropped. */
    InvalidPayload,
}

/** One thing [Blueprint] tolerated while decoding, handed to its `onDecodeError` callback. */
public class DecodeError(
    public val kind: DecodeErrorKind,
    /** The node's `type` string, or null when it is missing or the problem is not about one node. */
    public val type: String?,
    public val message: String?,
    /** The offending JSON object, when there is one. */
    public val raw: JsonObject?,
)

/** Collects [DecodeError]s for the [Blueprint.decodeScreen] call running on this thread. */
internal class DecodeErrorSink {
    private val current = ThreadLocal<ArrayList<DecodeError>?>()

    /** Reports into the active call; outside [collect] (direct `json` use) it is a no-op. */
    fun report(error: DecodeError) {
        current.get()?.add(error)
    }

    /** Position to [rollback] to, for an attempt that may be retried. */
    fun mark(): Int = current.get()?.size ?: 0

    /** Forgets what a failed attempt reported, so its retry does not report the same nodes twice. */
    fun rollback(mark: Int) {
        current.get()?.let { while (it.size > mark) it.removeAt(it.size - 1) }
    }

    fun <T> collect(block: () -> T): Pair<T, List<DecodeError>> {
        val previous = current.get()
        val errors = ArrayList<DecodeError>()
        current.set(errors)
        try {
            return block() to errors
        } finally {
            current.set(previous)
        }
    }
}
