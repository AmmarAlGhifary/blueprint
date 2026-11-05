package io.github.ammaralghifary.blueprint.model

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
 * (missing required field, wrong value type). A fallback renderer can inspect [raw]. [error] is null
 * for an unregistered type; for a registered type that failed it is a message safe to log.
 *
 * @author Ammar Al Ghifary
 */
public class UnknownComponent(
    /** The payload's `type` string, or null when it is missing. */
    public val type: String?,
    /** The untouched JSON object. It carries payload text: keep it out of analytics and crash reports. */
    public val raw: JsonObject,
    override val modifier: SduiModifier? = null,
    /** Why this node degraded (never contains payload values), or null when its type is simply unregistered. */
    public val error: String? = null,
) : SduiComponent()

/**
 * Action counterpart of [UnknownComponent]: unregistered type, or a registered one that failed to decode.
 *
 * @author Ammar Al Ghifary
 */
public class UnknownAction(
    /** The payload's `type` string, or null when it is missing. */
    public val type: String?,
    /** The untouched JSON object. It carries payload text: keep it out of analytics and crash reports. */
    public val raw: JsonObject,
    /** Why this action degraded (never contains payload values), or null when its type is simply unregistered. */
    public val error: String? = null,
) : SduiAction()

/** @author Ammar Al Ghifary */
private object UnknownDescriptors {
    val component: SerialDescriptor = buildClassSerialDescriptor("io.github.ammaralghifary.blueprint.UnknownComponent")
    val action: SerialDescriptor = buildClassSerialDescriptor("io.github.ammaralghifary.blueprint.UnknownAction")
}

private fun unknownComponent(json: Json, raw: JsonObject, error: String?): UnknownComponent {
    val modifier = raw["modifier"]?.let {
        // A malformed modifier must not take the whole component down.
        runCatching { json.decodeFromJsonElement(SduiModifier.serializer(), it) }.getOrNull()
    }
    return UnknownComponent(raw.typeName(), raw, modifier, error)
}

/** @author Ammar Al Ghifary */
internal class UnknownComponentSerializer(
    private val sink: DecodeErrorSink? = null,
) : KSerializer<UnknownComponent> {
    override val descriptor: SerialDescriptor = UnknownDescriptors.component

    override fun deserialize(decoder: Decoder): UnknownComponent {
        val input = decoder.asJsonDecoder()
        val raw = input.decodeJsonObject()
        sink?.report(DecodeError(DecodeErrorKind.UnregisteredType, raw.typeName(), "No component registered for this type", raw))
        return unknownComponent(input.json, raw, error = null)
    }

    override fun serialize(encoder: Encoder, value: UnknownComponent) {
        (encoder as? JsonEncoder ?: throw SerializationException("Blueprint only supports JSON"))
            .encodeJsonElement(value.raw)
    }
}

/** @author Ammar Al Ghifary */
internal class UnknownActionSerializer(
    private val sink: DecodeErrorSink? = null,
) : KSerializer<UnknownAction> {
    override val descriptor: SerialDescriptor = UnknownDescriptors.action

    override fun deserialize(decoder: Decoder): UnknownAction {
        val raw = decoder.asJsonDecoder().decodeJsonObject()
        sink?.report(DecodeError(DecodeErrorKind.UnregisteredType, raw.typeName(), "No action registered for this type", raw))
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
 * decoded through the same Json, so each child degrades on its own. A malformed `modifier` alone
 * only costs the modifier, not the component.
 *
 * @author Ammar Al Ghifary
 */
internal class LenientComponentSerializer(
    private val delegate: KSerializer<out SduiComponent>,
    private val sink: DecodeErrorSink? = null,
) : KSerializer<SduiComponent> {
    override val descriptor: SerialDescriptor = UnknownDescriptors.component

    override fun deserialize(decoder: Decoder): SduiComponent {
        val input = decoder.asJsonDecoder()
        val raw = input.decodeJsonObject()
        val mark = sink?.mark() ?: 0
        return try {
            input.json.decodeFromJsonElement(delegate, raw)
        } catch (e: IllegalArgumentException) { // SerializationException extends it
            sink?.rollback(mark)
            val message = describeFailure(e, sink?.fieldNames.orEmpty())
            withoutModifier(input.json, raw)?.also {
                sink?.report(DecodeError(DecodeErrorKind.ModifierIgnored, raw.typeName(), message, raw))
            } ?: run {
                sink?.rollback(mark)
                sink?.report(DecodeError(DecodeErrorKind.InvalidComponent, raw.typeName(), message, raw))
                unknownComponent(input.json, raw, message)
            }
        }
    }

    /** Retries without `modifier`; null when that is not the problem (or the model requires one). */
    private fun withoutModifier(json: Json, raw: JsonObject): SduiComponent? {
        if ("modifier" !in raw) return null
        return try {
            json.decodeFromJsonElement(delegate, JsonObject(raw - "modifier"))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: SduiComponent): Unit =
        throw SerializationException("Blueprint only decodes payloads")
}

/**
 * Action counterpart of [LenientComponentSerializer].
 *
 * @author Ammar Al Ghifary
 */
internal class LenientActionSerializer(
    private val delegate: KSerializer<out SduiAction>,
    private val sink: DecodeErrorSink? = null,
) : KSerializer<SduiAction> {
    override val descriptor: SerialDescriptor = UnknownDescriptors.action

    override fun deserialize(decoder: Decoder): SduiAction {
        val input = decoder.asJsonDecoder()
        val raw = input.decodeJsonObject()
        return try {
            input.json.decodeFromJsonElement(delegate, raw)
        } catch (e: IllegalArgumentException) {
            val message = describeFailure(e, sink?.fieldNames.orEmpty())
            sink?.report(DecodeError(DecodeErrorKind.InvalidAction, raw.typeName(), message, raw))
            UnknownAction(raw.typeName(), raw, message)
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
