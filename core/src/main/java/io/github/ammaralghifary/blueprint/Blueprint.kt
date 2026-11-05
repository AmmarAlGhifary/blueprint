package io.github.ammaralghifary.blueprint

import io.github.ammaralghifary.blueprint.model.DecodeError
import io.github.ammaralghifary.blueprint.model.DecodeErrorKind
import io.github.ammaralghifary.blueprint.model.DecodeErrorSink
import io.github.ammaralghifary.blueprint.model.LenientActionSerializer
import io.github.ammaralghifary.blueprint.model.LenientComponentSerializer
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.model.UnknownAction
import io.github.ammaralghifary.blueprint.model.UnknownActionSerializer
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.model.UnknownComponentSerializer
import io.github.ammaralghifary.blueprint.model.describeFailure
import io.github.ammaralghifary.blueprint.registry.SduiRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic

/**
 * Binds a [SduiRegistry] to a [Json] that can decode payloads. No networking: fetch the JSON
 * however you like, then call [decodeScreen].
 *
 * Decoding is tolerant by contract: unknown fields are ignored, and a component or action that
 * cannot be used (unregistered type, or a registered type whose fields are missing or have the wrong
 * type) becomes an [UnknownComponent]/[UnknownAction] instead of failing the whole payload. Only the
 * affected node degrades; its siblings and parents still decode. The same holds for the envelope
 * itself, see [decodeScreen]. Decoding is the supported direction; encoding payloads is not.
 *
 * Tolerating is silent in release builds, so [onDecodeError] is how you find out: it is called once
 * per tolerated problem, after [decodeScreen] has finished, on the calling thread. Log it, count it,
 * send it to your crash reporter. It is not called for decoding done directly through [json].
 *
 * [onDecoded] is the per-call summary: once per [decodeScreen], after the errors above were delivered,
 * it receives the screen and every error of that call (an empty list for a clean payload). Use it to
 * log "screen X decoded with N problems" or to decide on an error state. A throwing callback is
 * swallowed; it can never make [decodeScreen] throw. The screen and [DecodeError.raw] carry payload
 * text: keep them out of analytics and crash reporters, and log [DecodeError.message] instead.
 *
 * @author Ammar Al Ghifary
 */
public class Blueprint(
    /** Type strings this instance decodes and renders. */
    public val registry: SduiRegistry,
    json: Json = Json,
    private val onDecodeError: ((DecodeError) -> Unit)? = null,
    private val onDecoded: ((SduiScreen, List<DecodeError>) -> Unit)? = null,
) {
    private val sink = DecodeErrorSink(registry)

    /** The [Json] used for decoding, derived from the one passed in. */
    public val json: Json = Json(from = json) {
        ignoreUnknownKeys = true
        coerceInputValues = true
        serializersModule = json.serializersModule + SerializersModule {
            polymorphic(SduiComponent::class) {
                defaultDeserializer { type ->
                    registry.componentSerializer(type)?.let { LenientComponentSerializer(it, sink) } ?: UnknownComponentSerializer(sink)
                }
            }
            polymorphic(SduiAction::class) {
                defaultDeserializer { type ->
                    registry.actionSerializer(type)?.let { LenientActionSerializer(it, sink) } ?: UnknownActionSerializer(sink)
                }
            }
        }
    }

    /**
     * Decodes [jsonString] into a screen. Never throws on bad input: a payload that cannot be read
     * as a screen (malformed JSON, a root that is not an object, a missing, null or non-object
     * `body`, or nesting deeper than 100 JSON levels) comes back as a screen whose body is an
     * [UnknownComponent] carrying the reason in [UnknownComponent.error]. Render it like any other
     * unknown node, or check `screen.body is UnknownComponent` first to show your own error state.
     */
    public fun decodeScreen(jsonString: String): SduiScreen {
        // kotlinx's own parser recurses per level, so depth is checked on the text before parsing.
        val tooDeep = isNestedDeeperThan(jsonString, MAX_DEPTH)
        val element = try {
            if (tooDeep) null else json.parseToJsonElement(jsonString)
        } catch (e: IllegalArgumentException) { // SerializationException extends it
            return unreadable(describeFailure(e, sink.fieldNames))
        } catch (e: StackOverflowError) {
            return unreadable("Payload exhausted the stack while parsing")
        }
        return element?.let(::decodeScreen) ?: unreadable("Payload is nested deeper than $MAX_DEPTH levels")
    }

    /** Same contract as the [String] overload: bad input degrades to an [UnknownComponent] body. */
    public fun decodeScreen(element: JsonElement): SduiScreen {
        val (screen, errors) = sink.collect { decodeGuarded(element) }
        return finish(screen, errors)
    }

    private fun decodeGuarded(element: JsonElement): SduiScreen {
        val root = element as? JsonObject ?: JsonObject(emptyMap())
        if (isNestedDeeperThan(element, MAX_DEPTH)) {
            return invalid(root, "Payload is nested deeper than $MAX_DEPTH levels")
        }
        return try {
            try {
                json.decodeFromJsonElement(SduiScreen.serializer(), element)
            } catch (e: IllegalArgumentException) {
                sink.rollback(0)
                salvageScreen(root, e)
            }
        } catch (e: StackOverflowError) {
            // The depth guard bounds nesting, but a small thread stack can still overflow below the limit.
            sink.rollback(0)
            invalid(root, "Payload exhausted the stack while decoding")
        }
    }

    /**
     * The envelope failed to decode. Keep whatever is still usable: a bad `screen_id` or `version`
     * must not throw away a perfectly good body.
     */
    private fun salvageScreen(root: JsonObject, error: IllegalArgumentException): SduiScreen {
        val message = describeFailure(error, sink.fieldNames)
        val body = (root["body"] as? JsonObject)?.let {
            val mark = sink.mark()
            try {
                json.decodeFromJsonElement(SduiComponent.serializer(), it)
            } catch (e: IllegalArgumentException) {
                sink.rollback(mark)
                null
            }
        }
        if (body == null) sink.report(DecodeError(DecodeErrorKind.InvalidPayload, null, message, root))
        else sink.report(DecodeError(DecodeErrorKind.InvalidPayload, null, "Ignored invalid screen metadata: $message", root))
        return SduiScreen(
            id = (root["screen_id"] as? JsonPrimitive)?.contentOrNull,
            body = body ?: UnknownComponent(null, root, error = message),
            version = (root["version"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun invalid(root: JsonObject, message: String?): SduiScreen {
        sink.report(DecodeError(DecodeErrorKind.InvalidPayload, null, message, root))
        return brokenScreen(root, message)
    }

    private fun unreadable(message: String?): SduiScreen =
        finish(
            brokenScreen(JsonObject(emptyMap()), message),
            listOf(DecodeError(DecodeErrorKind.InvalidPayload, null, message, null)),
        )

    private fun brokenScreen(raw: JsonObject, message: String?): SduiScreen =
        SduiScreen(body = UnknownComponent(null, raw, error = message))

    /** The one place a call ends: per-error callback first, then the summary, each at most once. */
    private fun finish(screen: SduiScreen, errors: List<DecodeError>): SduiScreen {
        deliver(errors)
        summarize(screen, errors)
        return screen
    }

    /** A throwing observer must not break the never-throws promise it is there to watch. */
    private fun deliver(errors: List<DecodeError>) {
        val callback = onDecodeError ?: return
        for (error in errors) {
            try {
                callback(error)
            } catch (e: Exception) {
                // dropped on purpose
            }
        }
    }

    private fun summarize(screen: SduiScreen, errors: List<DecodeError>) {
        val callback = onDecoded ?: return
        try {
            callback(screen, errors.toList()) // a copy, so a host holding it cannot see the sink's list change
        } catch (e: Exception) {
            // dropped on purpose, same as deliver
        }
    }

    internal companion object {
        /** JSON levels (objects and arrays) a payload may nest; a component takes two. About 6x real screens. */
        internal const val MAX_DEPTH: Int = 100
    }
}

/** Iterative so that measuring a hostile payload cannot itself overflow the stack. */
private fun isNestedDeeperThan(root: JsonElement, max: Int): Boolean {
    val stack = ArrayDeque<Pair<JsonElement, Int>>()
    stack.addLast(root to 1)
    while (stack.isNotEmpty()) {
        val (node, depth) = stack.removeLast()
        when (node) {
            is JsonObject -> {
                if (depth > max) return true
                node.values.forEach { stack.addLast(it to depth + 1) }
            }
            is JsonArray -> {
                if (depth > max) return true
                node.forEach { stack.addLast(it to depth + 1) }
            }
            else -> Unit
        }
    }
    return false
}

/** Counts `{`/`[` nesting outside string literals; stops as soon as [max] is exceeded. */
private fun isNestedDeeperThan(text: String, max: Int): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (ch in text) {
        if (inString) {
            when {
                escaped -> escaped = false
                ch == '\\' -> escaped = true
                ch == '"' -> inString = false
            }
            continue
        }
        when (ch) {
            '"' -> inString = true
            '{', '[' -> if (++depth > max) return true
            '}', ']' -> if (depth > 0) depth--
        }
    }
    return false
}
