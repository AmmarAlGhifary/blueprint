package io.github.ammaralghifary.blueprint

import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.model.SduiPadding
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.model.SduiSize
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

// docs/schema/blueprint.schema.json describes what the models below accept. If a field is added,
// renamed or made required in :core and the schema (and blueprint.d.ts) is not updated, a server team
// validating against it would be told something false. The :defaults stock components are not covered
// here because :core tests cannot see them.
@OptIn(ExperimentalSerializationApi::class)
class SchemaDriftTest {
    private val schema: JsonObject = run {
        // Unit tests run with the module directory as the working directory.
        val file = File("../docs/schema/blueprint.schema.json")
        check(file.isFile) { "schema not found at ${file.absoluteFile}" }
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun def(name: String): JsonObject = schema["\$defs"]!!.jsonObject[name]!!.jsonObject

    private fun names(o: JsonObject, key: String): Set<String> =
        (o[key] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()

    /**
     * Payload keys the schema documents. An action or component also lists its `type` discriminator,
     * which the model has no field for; `Size` has a real field called `type`, so this is opt-in.
     */
    private fun schemaFields(o: JsonObject, discriminated: Boolean): Set<String> =
        o["properties"]!!.jsonObject.keys - (if (discriminated) setOf("type") else emptySet())

    private fun schemaRequired(o: JsonObject, discriminated: Boolean): Set<String> =
        names(o, "required") - (if (discriminated) setOf("type") else emptySet())

    private fun fields(serializer: KSerializer<*>): Set<String> =
        (0 until serializer.descriptor.elementsCount).map { serializer.descriptor.getElementName(it) }.toSet()

    private fun required(serializer: KSerializer<*>): Set<String> =
        (0 until serializer.descriptor.elementsCount)
            .filterNot { serializer.descriptor.isElementOptional(it) }
            .map { serializer.descriptor.getElementName(it) }
            .toSet()

    private fun assertMatches(label: String, node: JsonObject, serializer: KSerializer<*>, discriminated: Boolean = false) {
        assertEquals("$label: fields differ between the schema and the model", fields(serializer), schemaFields(node, discriminated))
        assertEquals("$label: required fields differ", required(serializer), schemaRequired(node, discriminated))
    }

    @Test
    fun theModifierMatches() = assertMatches("Modifier", def("Modifier"), SduiModifier.serializer())

    @Test
    fun thePaddingMatches() = assertMatches("Padding", def("Padding"), SduiPadding.serializer())

    @Test
    fun theSizeMatches() = assertMatches("Size", def("Size"), SduiSize.serializer())

    @Test
    fun theNavigateActionMatches() = assertMatches("NavigateAction", def("NavigateAction"), NavigateAction.serializer(), discriminated = true)

    @Test
    fun theScreenEnvelopeMatches() = assertMatches("Screen", schema, SduiScreen.serializer())

    @Test
    fun everyDefinitionACoreModelPointsAtExists() {
        // A $ref that names a missing definition would make every validator fail on load.
        val refs = Regex("\"#/\\\$defs/(\\w+)\"").findAll(schema.toString()).map { it.groupValues[1] }.toSet()
        val defined = schema["\$defs"]!!.jsonObject.keys
        assertEquals("dangling \$ref targets", emptySet<String>(), refs - defined)
    }
}
