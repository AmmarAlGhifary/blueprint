package io.github.ammaralghifary.blueprint.actions

import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.UnknownAction
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** @author Ammar Al Ghifary */
@OptIn(ExperimentalSerializationApi::class)
class ActionsSchemaDriftTest {
    private val schema: JsonObject = run {
        // Unit tests run with the module directory as the working directory.
        val file = File("../docs/schema/blueprint.schema.json")
        check(file.isFile) { "schema not found at ${file.absoluteFile}" }
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    private val actions: Map<String, KSerializer<*>> = mapOf(
        "open_url" to OpenUrlAction.serializer(),
        "submit" to SubmitAction.serializer(),
        "set_state" to SetStateAction.serializer(),
    )

    private fun def(name: String): JsonObject = schema["\$defs"]!!.jsonObject[name]!!.jsonObject

    /** type string -> definition name, read from the schema's own dispatch table. */
    private val definitionOf: Map<String, String> = def("Action")["allOf"]!!.jsonArray.associate { rule ->
        val type = rule.jsonObject["if"]!!.jsonObject["properties"]!!.jsonObject["type"]!!.jsonObject["const"]!!.jsonPrimitive.content
        val name = rule.jsonObject["then"]!!.jsonObject["\$ref"]!!.jsonPrimitive.content.substringAfterLast('/')
        type to name
    }

    private fun node(type: String) = def(definitionOf.getValue(type))

    private fun properties(type: String) = node(type)["properties"]!!.jsonObject

    private fun strings(element: JsonElement?): Set<String> = (element as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()

    private fun names(serializer: KSerializer<*>) =
        (0 until serializer.descriptor.elementsCount).map { serializer.descriptor.getElementName(it) }

    @Test
    fun theSchemaListsNavigateAndTheseActions() =
        assertEquals(actions.keys + "navigate", definitionOf.keys)

    @Test
    fun everySchemaTypeIsRegisteredUnderThatName() {
        // A value the schema accepts for each required field, so only the type string can make it unknown.
        val samples = mapOf(
            "open_url" to "\"url\":\"u\"",
            "submit" to "\"target\":\"t\"",
            "set_state" to "\"key\":\"k\",\"value\":1",
        )
        val blueprint = Blueprint(sduiRegistry { component<Trigger>("trigger") { _, _, _ -> }; sduiActions() })
        for ((type, fields) in samples) {
            val body = blueprint.decodeScreen("""{"body":{"type":"trigger","action":{"type":"$type",$fields}}}""").body
            val action = (body as Trigger).action
            assertFalse("$type decoded as unknown", action is UnknownAction)
        }
        assertEquals(actions.keys, samples.keys)
    }

    @Test
    fun everyActionHasTheFieldsTheSchemaListsAndRequires() {
        for ((type, serializer) in actions) {
            assertEquals("$type: fields differ", names(serializer).toSet(), properties(type).keys - "type")
            val required = names(serializer).filterIndexed { i, _ -> !serializer.descriptor.isElementOptional(i) }.toSet()
            assertEquals("$type: required fields differ", required, strings(node(type)["required"]) - "type")
        }
    }

    @Test
    fun theTypeDiscriminatorOfEachDefinitionIsItsOwnTypeString() {
        for (type in actions.keys) {
            assertEquals(type, properties(type)["type"]!!.jsonObject["const"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun theFieldTypesMatch() {
        fun typeOf(type: String, field: String) = properties(type).getValue(field).jsonObject["type"]
        assertEquals("string", typeOf("open_url", "url")!!.jsonPrimitive.content)
        assertEquals("string", typeOf("submit", "target")!!.jsonPrimitive.content)
        assertEquals("object", typeOf("submit", "params")!!.jsonPrimitive.content)
        assertEquals("string", typeOf("set_state", "key")!!.jsonPrimitive.content)
        assertEquals(setOf("string", "number", "boolean", "null"), strings(typeOf("set_state", "value")))
    }
}
