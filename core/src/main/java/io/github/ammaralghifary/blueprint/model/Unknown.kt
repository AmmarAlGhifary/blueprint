package io.github.ammaralghifary.blueprint

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What a component decodes to when it cannot be used as a typed component: its `type` is not in
 * the registry (or is missing), or the type is registered but the payload does not fit the model
 * (missing required field, wrong value type). It keeps the untouched [raw] JSON so a fallback
 * renderer or your logging can inspect it. [error] is null for an unregistered type and holds the
 * decoding message for a registered type that failed.
 */
public class UnknownComponent(
    public val type: String?,
    public val raw: JsonObject,
    override val modifier: SduiModifier? = null,
    public val error: String? = null,
) : SduiComponent()

/** Action counterpart of [UnknownComponent]: unregistered type, or a registered one that failed to decode. */
public class UnknownAction(
    public val type: String?,
    public val raw: JsonObject,
    public val error: String? = null,
) : SduiAction()

private fun unknownComponent(json: Json, raw: JsonObject, error: String?): UnknownComponent {
    val modifier = raw["modifier"]?.let {
        // A malformed modifier must not take the whole component down.
        runCatching { json.decodeFromJsonElement(SduiModifier.serializer(), it) }.getOrNull()
    }
    return UnknownComponent(raw.typeName(), raw, modifier, error)
}

internal object UnknownComponentSerializer : KSerializer<UnknownComponent> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.github.ammaralghifary.blueprint.UnknownComponent")

    override fun deserialize(decoder: Decoder): UnknownComponent {
        val input = decoder.asJsonDecoder()
        return unknownComponent(input.json, input.decodeJsonObject(), error = null)
    }

    override fun serialize(encoder: Encoder, value: UnknownComponent) {
        (encoder as? JsonEncoder ?: throw SerializationException("Blueprint only supports JSON"))
            .encodeJsonElement(value.raw)
    }
}

internal object UnknownActionSerializer : KSerializer<UnknownAction> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.github.ammaralghifary.blueprint.UnknownAction")

    override fun deserialize(decoder: Decoder): UnknownAction {
        val raw = decoder.asJsonDecoder().decodeJsonObject()
        return UnknownAction(raw.typeName(), raw)
    }

    override fun serialize(encoder: Encoder, value: UnknownAction) {
        (encoder as? JsonEncoder ?: throw SerializationException("Blueprint only supports JSON"))
            .encodeJsonElement(value.raw)
    }
}

/**
 * Wraps a registered component serializer so a payload that does not fit the model degrades to an
 * [UnknownComponent] (keeping the error) instead of failing the whole screen. Nested children are
 * decoded through the same Json, so each child degrades on its own.
 */
internal class LenientComponentSerializer(
    private val delegate: KSerializer<out SduiComponent>,
) : KSerializer<SduiComponent> {
    override val descriptor: SerialDescriptor = UnknownComponentSerializer.descriptor

    override fun deserialize(decoder: Decoder): SduiComponent {
        val input = decoder.asJsonDecoder()
        val raw = input.decodeJsonObject()
        return try {
            input.json.decodeFromJsonElement(delegate, raw)
        } catch (e: IllegalArgumentException) { // SerializationException extends it
            unknownComponent(input.json, raw, e.message ?: e::class.simpleName)
        }
    }

    override fun serialize(encoder: Encoder, value: SduiComponent): Unit =
        throw SerializationException("Blueprint only decodes payloads")
}

/** Action counterpart of [LenientComponentSerializer]. */
internal class LenientActionSerializer(
    private val delegate: KSerializer<out SduiAction>,
) : KSerializer<SduiAction> {
    override val descriptor: SerialDescriptor = UnknownActionSerializer.descriptor

    override fun deserialize(decoder: Decoder): SduiAction {
        val input = decoder.asJsonDecoder()
        val raw = input.decodeJsonObject()
        return try {
            input.json.decodeFromJsonElement(delegate, raw)
        } catch (e: IllegalArgumentException) {
            UnknownAction(raw.typeName(), raw, e.message ?: e::class.simpleName)
        }
    }

    override fun serialize(encoder: Encoder, value: SduiAction): Unit =
        throw SerializationException("Blueprint only decodes payloads")
}

private fun Decoder.asJsonDecoder(): JsonDecoder =
    this as? JsonDecoder ?: throw SerializationException("Blueprint only supports JSON")

private fun JsonDecoder.decodeJsonObject(): JsonObject {
    val element: JsonElement = decodeJsonElement()
    return element as? JsonObject ?: throw SerializationException("Expected a JSON object but got $element")
}

private fun JsonObject.typeName(): String? = (this["type"] as? JsonPrimitive)?.contentOrNull
