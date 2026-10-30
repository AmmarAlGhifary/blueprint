package io.github.ammaralghifary.blueprint

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What a component decodes to when its `type` is not in the registry (or is missing).
 * It keeps the untouched [raw] JSON so a fallback renderer or your logging can inspect it.
 */
public class UnknownComponent(
    public val type: String?,
    public val raw: JsonObject,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

/** What an action decodes to when its `type` is not in the registry (or is missing). */
public class UnknownAction(
    public val type: String?,
    public val raw: JsonObject,
) : SduiAction()

internal object UnknownComponentSerializer : KSerializer<UnknownComponent> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.github.ammaralghifary.blueprint.UnknownComponent")

    override fun deserialize(decoder: Decoder): UnknownComponent {
        val input = decoder.asJsonDecoder()
        val raw = input.decodeJsonObject()
        val modifier = raw["modifier"]?.let {
            // A malformed modifier must not take the whole component down.
            runCatching { input.json.decodeFromJsonElement(SduiModifier.serializer(), it) }.getOrNull()
        }
        return UnknownComponent(raw.typeName(), raw, modifier)
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

private fun Decoder.asJsonDecoder(): JsonDecoder =
    this as? JsonDecoder ?: throw SerializationException("Blueprint only supports JSON")

private fun JsonDecoder.decodeJsonObject(): JsonObject {
    val element: JsonElement = decodeJsonElement()
    return element as? JsonObject ?: throw SerializationException("Expected a JSON object but got $element")
}

private fun JsonObject.typeName(): String? = (this["type"] as? JsonPrimitive)?.contentOrNull
