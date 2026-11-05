package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.material3.Typography
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.NavigateAction
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// docs/schema/blueprint.schema.json (and blueprint.d.ts) tell a server team what the stock components
// accept. If a stock model or token mapper changes and the schema does not, they are told something false.
// :core's SchemaDriftTest covers the envelope, modifier, padding and size; this covers the ten stock
// components and the navigate action. The `button.variant` and `list.orientation` values are not checked:
// their branching is inline in the renderers, not in a mapper a test can call.
/** @author Ammar Al Ghifary */
@OptIn(ExperimentalSerializationApi::class)
class DefaultsSchemaDriftTest {
    private val schema: JsonObject = run {
        // Unit tests run with the module directory as the working directory.
        val file = File("../docs/schema/blueprint.schema.json")
        check(file.isFile) { "schema not found at ${file.absoluteFile}" }
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    private val stock: Map<String, KSerializer<*>> = mapOf(
        "text" to SduiText.serializer(),
        "image" to SduiImage.serializer(),
        "button" to SduiButton.serializer(),
        "card" to SduiCard.serializer(),
        "column" to SduiColumn.serializer(),
        "row" to SduiRow.serializer(),
        "box" to SduiBox.serializer(),
        "list" to SduiList.serializer(),
        "spacer" to SduiSpacer.serializer(),
        "divider" to SduiDivider.serializer(),
    )

    private fun def(name: String): JsonObject = schema["\$defs"]!!.jsonObject[name]!!.jsonObject

    /** type string -> definition name, read from the schema's own dispatch table. */
    private val definitionOf: Map<String, String> = def("Component")["allOf"]!!.jsonArray.associate { rule ->
        val type = rule.jsonObject["if"]!!.jsonObject["properties"]!!.jsonObject["type"]!!.jsonObject["const"]!!.jsonPrimitive.content
        val name = rule.jsonObject["then"]!!.jsonObject["\$ref"]!!.jsonPrimitive.content.substringAfterLast('/')
        type to name
    }

    private fun strings(o: JsonObject, key: String): Set<String> =
        (o[key] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()

    /** The JSON type a schema property resolves to, following a `$ref` such as `int32` or `Modifier`. */
    private fun schemaType(prop: JsonObject): String {
        prop["\$ref"]?.let { return schemaType(def(it.jsonPrimitive.content.substringAfterLast('/'))) }
        return prop["type"]?.jsonPrimitive?.content ?: error("schema property without a type: $prop")
    }

    private fun modelType(d: SerialDescriptor): String = when (d.kind) {
        PrimitiveKind.STRING -> "string"
        PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> "integer"
        PrimitiveKind.BOOLEAN -> "boolean"
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> "number"
        StructureKind.LIST -> "array"
        StructureKind.CLASS, StructureKind.OBJECT, StructureKind.MAP, is PolymorphicKind -> "object"
        else -> error("unmapped kind ${d.kind}")
    }

    private fun elements(s: KSerializer<*>) = (0 until s.descriptor.elementsCount).toList()

    @Test
    fun theRegistryTheSchemaAndThisTestAgreeOnTheStockTypes() {
        assertEquals("registry vs schema dispatch table", DefaultSduiRegistry.componentTypes, definitionOf.keys)
        assertEquals("registry vs the models this test checks", DefaultSduiRegistry.componentTypes, stock.keys)
    }

    @Test
    fun everyStockComponentHasTheFieldsTheSchemaLists() {
        for ((type, serializer) in stock) {
            val node = def(definitionOf.getValue(type))
            val modelFields = elements(serializer).map { serializer.descriptor.getElementName(it) }.toSet()
            assertEquals("$type: fields differ between the schema and the model", modelFields, node["properties"]!!.jsonObject.keys - "type")
        }
    }

    @Test
    fun everyStockComponentRequiresTheFieldsTheSchemaRequires() {
        for ((type, serializer) in stock) {
            val node = def(definitionOf.getValue(type))
            val modelRequired = elements(serializer).filterNot { serializer.descriptor.isElementOptional(it) }
                .map { serializer.descriptor.getElementName(it) }.toSet()
            assertEquals("$type: required fields differ", modelRequired, strings(node, "required") - "type")
        }
    }

    @Test
    fun everyStockFieldHasTheJsonTypeTheSchemaPromises() {
        // Catches an Int that became a String, or a list that became a single object, which the field
        // names alone would not.
        for ((type, serializer) in stock) {
            val props = def(definitionOf.getValue(type))["properties"]!!.jsonObject
            for (i in elements(serializer)) {
                val name = serializer.descriptor.getElementName(i)
                assertEquals("$type.$name: type differs", modelType(serializer.descriptor.getElementDescriptor(i)), schemaType(props.getValue(name).jsonObject))
            }
        }
    }

    @Test
    fun theTypeDiscriminatorOfEachDefinitionIsItsOwnTypeString() {
        for ((type, name) in definitionOf) {
            val const = def(name)["properties"]!!.jsonObject["type"]!!.jsonObject["const"]!!.jsonPrimitive.content
            assertEquals("definition $name", type, const)
        }
    }

    @Test
    fun theNavigateActionDecodesAsTheSchemaDescribes() {
        val const = def("NavigateAction")["properties"]!!.jsonObject["type"]!!.jsonObject["const"]!!.jsonPrimitive.content
        val screen = Blueprint(DefaultSduiRegistry).decodeScreen(
            """{"body":{"type":"button","text":"go","action":{"type":"$const","destination":"home","params":{"k":"v"}}}}""",
        )
        val action = (screen.body as SduiButton).action
        assertEquals(NavigateAction("home", mapOf("k" to "v")), action)
        assertEquals("schema requires", setOf("type", "destination"), strings(def("NavigateAction"), "required"))
    }

    // Enum-like strings: the schema's lists against what the token mappers recognise.

    private val unknown = "\u0000not-a-value"

    private fun enumValues(type: String, field: String): Set<String> =
        def(definitionOf.getValue(type))["properties"]!!.jsonObject.getValue(field).jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }.toSet()

    private val modifierAlignment: Set<String> =
        def("Modifier")["properties"]!!.jsonObject.getValue("alignment").jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }.toSet()

    /**
     * Vocabulary the library could plausibly be extended to recognise. A code change that starts accepting
     * one of these while the schema omits it is reported; a value outside this list and the schema is not.
     */
    private val plausible = setOf(
        // text alignment and arrangement
        "left", "right", "middle", "justify", "space", "default", "none", "auto",
        "space_between", "space_around", "space_evenly", "center", "start", "end", "top", "bottom",
        // typography roles
        "small", "large", "body_large", "body_small", "title_large", "title_small", "headline_large",
        "headline_small", "display_large", "display_small",
        // content scales
        "contain", "cover", "inside", "stretch", "fit_width", "fit_height", "fill_width", "fill_height", "fill_bounds",
        // alignments
        "top_start", "top_center", "top_end", "center_start", "center_end", "bottom_start", "bottom_center",
        "bottom_end", "top_left", "top_right", "bottom_left", "bottom_right", "center_left", "center_right",
    )

    /** [recognised] says whether the code does something with the value, apart from falling back. */
    private fun assertEnumMatches(label: String, schemaValues: Set<String>, recognised: (String) -> Boolean) {
        val missingInCode = schemaValues.filterNot(recognised)
        assertEquals("$label: the schema lists values the code does not recognise", emptyList<String>(), missingInCode)
        val missingInSchema = (plausible + schemaValues).filter(recognised).toSet() - schemaValues
        assertEquals("$label: the code recognises values the schema does not list", emptySet<String>(), missingInSchema)
    }

    @Test
    fun textStyleValuesMatchTheTypographyRoles() {
        val t = Typography()
        val fallback = t.forRole(unknown)
        // "body" is the documented default, so it is not told apart from an unknown value by its result.
        assertEnumMatches("text.style", enumValues("text", "style")) { it == "body" || t.forRole(it) != fallback }
    }

    @Test
    fun textAlignValuesMatchTheTextAlignments() =
        assertEnumMatches("text.align", enumValues("text", "align")) { textAlignOf(it) != null }

    @Test
    fun imageScaleValuesMatchTheContentScales() {
        val fallback = contentScaleOf(unknown)
        assertEnumMatches("image.scale", enumValues("image", "scale")) { it == "fit" || contentScaleOf(it) != fallback }
    }

    @Test
    fun columnArrangementValuesMatchTheVerticalArrangements() {
        val fallback = verticalArrangementOf(unknown)
        assertEnumMatches("column.arrangement", enumValues("column", "arrangement")) { it == "start" || verticalArrangementOf(it) != fallback }
    }

    @Test
    fun rowArrangementValuesMatchTheHorizontalArrangements() {
        val fallback = horizontalArrangementOf(unknown)
        assertEnumMatches("row.arrangement", enumValues("row", "arrangement")) { it == "start" || horizontalArrangementOf(it) != fallback }
    }

    @Test
    fun modifierAlignmentValuesAreTheUnionOfWhatColumnsRowsAndBoxesRead() {
        assertEnumMatches("modifier.alignment", modifierAlignment) {
            horizontalAlignmentOf(it) != null || verticalAlignmentOf(it) != null || boxAlignmentOf(it) != null
        }
    }

    @Test
    fun theEnumHelpersThemselvesCatchAMissingValue() {
        // A guard on the guard: a recogniser that forgets a schema value must fail, not pass quietly.
        val failure = runCatching { assertEnumMatches("probe", setOf("a", "b")) { it == "a" } }.exceptionOrNull()
        assertTrue("a missing value went unnoticed", failure is AssertionError)
        val extra = runCatching { assertEnumMatches("probe", setOf("start")) { it == "start" || it == "end" } }.exceptionOrNull()
        assertTrue("an unlisted value went unnoticed", extra is AssertionError)
    }
}
