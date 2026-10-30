package io.github.ammaralghifary.blueprint

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic

/**
 * Binds a [SduiRegistry] to a [Json] that can decode payloads. No networking: fetch the JSON
 * however you like, then call [decodeScreen].
 *
 * Decoding is tolerant by contract: unknown fields are ignored, and unknown component/action
 * types become [UnknownComponent]/[UnknownAction] instead of failing the whole payload. Decoding
 * is the supported direction; encoding payloads is not.
 */
public class Blueprint(
    public val registry: SduiRegistry,
    json: Json = Json,
) {
    public val json: Json = Json(from = json) {
        ignoreUnknownKeys = true
        coerceInputValues = true
        serializersModule = json.serializersModule + SerializersModule {
            polymorphic(SduiComponent::class) {
                defaultDeserializer { type -> registry.componentSerializer(type) ?: UnknownComponentSerializer }
            }
            polymorphic(SduiAction::class) {
                defaultDeserializer { type -> registry.actionSerializer(type) ?: UnknownActionSerializer }
            }
        }
    }

    public fun decodeScreen(jsonString: String): SduiScreen = json.decodeFromString(SduiScreen.serializer(), jsonString)

    public fun decodeScreen(element: JsonElement): SduiScreen = json.decodeFromJsonElement(SduiScreen.serializer(), element)
}
