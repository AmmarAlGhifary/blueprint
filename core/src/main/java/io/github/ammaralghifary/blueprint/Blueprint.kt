package io.github.ammaralghifary.blueprint

import kotlinx.serialization.json.Json
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
 */
public class Blueprint(
    /** Type strings this instance decodes and renders. */
    public val registry: SduiRegistry,
    json: Json = Json,
) {
    /** The [Json] used for decoding, derived from the one passed in. */
    public val json: Json = Json(from = json) {
        ignoreUnknownKeys = true
        coerceInputValues = true
        serializersModule = json.serializersModule + SerializersModule {
            polymorphic(SduiComponent::class) {
                defaultDeserializer { type ->
                    registry.componentSerializer(type)?.let(::LenientComponentSerializer) ?: UnknownComponentSerializer
                }
            }
            polymorphic(SduiAction::class) {
                defaultDeserializer { type ->
                    registry.actionSerializer(type)?.let(::LenientActionSerializer) ?: UnknownActionSerializer
                }
            }
        }
    }

    /**
     * Decodes [jsonString] into a screen. Never throws on bad input: a payload that cannot be read
     * as a screen (malformed JSON, a root that is not an object, a missing, null or non-object
     * `body`) comes back as a screen whose body is an [UnknownComponent] carrying the reason in
     * [UnknownComponent.error]. Render it like any other unknown node, or check
     * `screen.body is UnknownComponent` first to show your own error state.
     */
    public fun decodeScreen(jsonString: String): SduiScreen {
        val element = try {
            json.parseToJsonElement(jsonString)
        } catch (e: IllegalArgumentException) { // SerializationException extends it
            return brokenScreen(JsonObject(emptyMap()), e)
        }
        return decodeScreen(element)
    }

    /** Same contract as the [String] overload: bad input degrades to an [UnknownComponent] body. */
    public fun decodeScreen(element: JsonElement): SduiScreen =
        try {
            json.decodeFromJsonElement(SduiScreen.serializer(), element)
        } catch (e: IllegalArgumentException) {
            salvageScreen(element, e)
        }

    /**
     * The envelope failed to decode. Keep whatever is still usable: a bad `screen_id` or `version`
     * must not throw away a perfectly good body.
     */
    private fun salvageScreen(element: JsonElement, error: IllegalArgumentException): SduiScreen {
        val root = element as? JsonObject ?: JsonObject(emptyMap())
        val body = (root["body"] as? JsonObject)?.let {
            try {
                json.decodeFromJsonElement(SduiComponent.serializer(), it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        return SduiScreen(
            id = (root["screen_id"] as? JsonPrimitive)?.contentOrNull,
            body = body ?: UnknownComponent(null, root, error = error.message ?: error::class.simpleName),
            version = (root["version"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun brokenScreen(raw: JsonObject, error: IllegalArgumentException): SduiScreen =
        SduiScreen(body = UnknownComponent(null, raw, error = error.message ?: error::class.simpleName))
}
