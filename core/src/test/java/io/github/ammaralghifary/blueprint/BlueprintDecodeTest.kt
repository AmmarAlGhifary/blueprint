package io.github.ammaralghifary.blueprint

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@Serializable
data class TestText(val text: String, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class TestColumn(val children: List<SduiComponent>, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class TestAction(val destination: String) : SduiAction()

private val testRegistry = sduiRegistry {
    component<TestText>("text") { _, _, _ -> }
    component<TestColumn>("column") { _, _, _ -> }
    action<TestAction>("navigation_action")
}

private fun blueprint() = Blueprint(testRegistry)

class BlueprintDecodeTest {

    @Test
    fun knownTypeDecodesToTypedClass() {
        val screen = blueprint().decodeScreen("""{"screen_id":"s","body":{"type":"text","text":"hi"}}""")
        assertEquals("s", screen.id)
        assertEquals(TestText("hi"), screen.body)
    }

    @Test
    fun unknownTopLevelTypeBecomesUnknownComponentAndKeepsRaw() {
        val screen = blueprint().decodeScreen("""{"body":{"type":"hologram","x":1,"modifier":{"padding":{"all":4}}}}""")
        val unknown = screen.body as UnknownComponent
        assertEquals("hologram", unknown.type)
        assertEquals("hologram", (unknown.raw["type"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(4, unknown.modifier?.padding?.all)
    }

    @Test
    fun unknownNestedChildOnlyAffectsThatChild() {
        val screen = blueprint().decodeScreen(
            """{"body":{"type":"column","children":[
                {"type":"text","text":"a"},{"type":"nope"},{"type":"text","text":"b"}]}}""",
        )
        val children = (screen.body as TestColumn).children
        assertEquals(TestText("a"), children[0])
        assertTrue(children[1] is UnknownComponent)
        assertEquals(TestText("b"), children[2])
    }

    @Test
    fun missingTypeBecomesUnknownWithNullType() {
        val screen = blueprint().decodeScreen("""{"body":{"text":"no type here"}}""")
        assertNull((screen.body as UnknownComponent).type)
    }

    @Test
    fun malformedModifierOnUnknownComponentDoesNotThrow() {
        val screen = blueprint().decodeScreen("""{"body":{"type":"nope","modifier":{"padding":"oops"}}}""")
        assertNull((screen.body as UnknownComponent).modifier)
    }

    @Test
    fun registeredTypeMissingRequiredFieldDegradesToUnknownWithError() {
        val screen = blueprint().decodeScreen(
            """{"body":{"type":"column","children":[
                {"type":"text","text":"a"},
                {"type":"text","modifier":{"padding":{"all":6}}},
                {"type":"text","text":"b"}]}}""",
        )
        val children = (screen.body as TestColumn).children
        assertEquals(TestText("a"), children[0])
        val broken = children[1] as UnknownComponent
        assertEquals("text", broken.type)
        assertNotNull(broken.error)
        assertEquals(6, broken.modifier?.padding?.all)
        assertEquals(TestText("b"), children[2])
    }

    @Test
    fun registeredTypeWithWrongValueTypeDegradesToUnknown() {
        val screen = blueprint().decodeScreen("""{"body":{"type":"text","text":{"nested":1}}}""")
        val broken = screen.body as UnknownComponent
        assertEquals("text", broken.type)
        assertNotNull(broken.error)
    }

    @Test
    fun brokenChildDoesNotTakeDownItsParent() {
        val screen = blueprint().decodeScreen(
            """{"body":{"type":"column","children":[
                {"type":"column","children":"not a list"},{"type":"text","text":"ok"}]}}""",
        )
        val children = (screen.body as TestColumn).children
        assertTrue(children[0] is UnknownComponent)
        assertEquals(TestText("ok"), children[1])
    }

    @Test
    fun unregisteredTypeHasNoError() {
        val screen = blueprint().decodeScreen("""{"body":{"type":"nope"}}""")
        assertNull((screen.body as UnknownComponent).error)
    }

    @Test
    fun registeredActionMissingFieldDegradesToUnknownAction() {
        val action = blueprint().json.decodeFromString(SduiAction.serializer(), """{"type":"navigation_action"}""")
        val unknown = action as UnknownAction
        assertEquals("navigation_action", unknown.type)
        assertNotNull(unknown.error)
    }

    @Test
    fun actionsDecodeAndUnknownActionsFallBack() {
        val json = blueprint().json
        val known = json.decodeFromString(SduiAction.serializer(), """{"type":"navigation_action","destination":"home"}""")
        assertEquals(TestAction("home"), known)
        val unknown = json.decodeFromString(SduiAction.serializer(), """{"type":"teleport"}""")
        assertEquals("teleport", (unknown as UnknownAction).type)
    }

    @Test
    fun realStudentDeskHomePayloadSurvivesWithUaiTypesAsUnknown() {
        // Only "column" is registered, so every UAI-specific child must fall back to Unknown.
        val bp = Blueprint(sduiRegistry { component<TestColumn>("column") { _, _, _ -> } })
        val json = javaClass.classLoader!!.getResource("home_dashboard.json")!!.readText()
        val screen = bp.decodeScreen(json)
        assertEquals("home_dashboard", screen.id)
        val children = (screen.body as TestColumn).children
        assertEquals(6, children.size)
        assertTrue(children.all { it is UnknownComponent })
        val banner = children[2] as UnknownComponent
        assertEquals("warning_banner", banner.type)
        assertEquals("#930b20", banner.modifier?.backgroundColor)
        assertEquals(16, banner.modifier?.margin?.resolvedStart)
    }
}

class SduiRegistryTest {
    @Test
    fun duplicateTypeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            sduiRegistry {
                component<TestText>("text") { _, _, _ -> }
                component<TestColumn>("text") { _, _, _ -> }
            }
        }
    }

    @Test
    fun sameClassUnderTwoTypesThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            sduiRegistry {
                component<TestText>("a") { _, _, _ -> }
                component<TestText>("b") { _, _, _ -> }
            }
        }
    }

    @Test
    fun includeMergesAndReplaceOverrides() {
        val base = sduiRegistry { component<TestText>("text") { _, _, _ -> } }
        val merged = sduiRegistry {
            include(base)
            component<TestColumn>("column") { _, _, _ -> }
        }
        assertEquals(setOf("text", "column"), merged.componentTypes)
        // Replacing a type with the same class is allowed.
        val replaced = sduiRegistry {
            include(base)
            component<TestText>("text", replace = true) { _, _, _ -> }
        }
        assertEquals(setOf("text"), replaced.componentTypes)
        assertThrows(IllegalArgumentException::class.java) {
            sduiRegistry { include(base); include(base) }
        }
    }
}

class SduiModifierTest {
    @Test
    fun paddingResolutionOrder() {
        val p = SduiPadding(all = 1, horizontal = 2, start = 3)
        assertEquals(3, p.resolvedStart)
        assertEquals(2, p.resolvedEnd)
        assertEquals(1, p.resolvedTop)
        assertEquals(1, p.resolvedBottom)
    }

    @Test
    fun colorParsing() {
        assertEquals(0xFF930B20.toInt(), parseColorArgb("#930b20"))
        assertEquals(0x80112233.toInt(), parseColorArgb("#80112233"))
        assertEquals(0xFFAABBCC.toInt(), parseColorArgb("#abc"))
        assertNull(parseColorArgb("red"))
        assertNull(parseColorArgb("#12"))
        assertNull(parseColorArgb("#zzzzzz"))
        assertNull(parseColorArgb(null))
    }
}

class DecodeScreenEnvelopeTest {
    private fun UnknownComponent.assertBroken() {
        assertNull(type)
        assertNotNull("the reason must be kept", error)
    }

    @Test
    fun missingBodyDegradesInsteadOfThrowing() {
        val screen = blueprint().decodeScreen("""{"screen_id":"s","toolbar":{"type":"app_bar"}}""")
        assertEquals("s", screen.id)
        val body = screen.body as UnknownComponent
        body.assertBroken()
        // The whole envelope is kept as raw, so logging and fallbacks can still see what arrived.
        assertEquals("s", (body.raw["screen_id"] as JsonPrimitive).content)
        assertNotNull(body.raw["toolbar"])
    }

    @Test
    fun nullAndNonObjectBodiesDegrade() {
        for (payload in listOf("""{"body":null}""", """{"body":5}""", """{"body":"text"}""", """{"body":[1]}""")) {
            (blueprint().decodeScreen(payload).body as UnknownComponent).assertBroken()
        }
    }

    @Test
    fun rootThatIsNotAnObjectDegrades() {
        for (payload in listOf("[1,2]", "42", "\"hello\"", "null")) {
            (blueprint().decodeScreen(payload).body as UnknownComponent).assertBroken()
        }
    }

    @Test
    fun malformedJsonDegrades() {
        for (payload in listOf("", "not json", """{"body":""", """{"body":{"type":"text",""")) {
            (blueprint().decodeScreen(payload).body as UnknownComponent).assertBroken()
        }
    }

    @Test
    fun elementOverloadNeverThrowsEither() {
        (blueprint().decodeScreen(JsonNull).body as UnknownComponent).assertBroken()
        (blueprint().decodeScreen(JsonPrimitive("x")).body as UnknownComponent).assertBroken()
    }

    @Test
    fun badMetadataDoesNotThrowAwayAGoodBody() {
        val screen = blueprint().decodeScreen(
            """{"screen_id":{"nested":1},"version":"abc","body":{"type":"text","text":"hi"}}""",
        )
        assertEquals(TestText("hi"), screen.body)
        assertNull(screen.id)
        assertNull(screen.version)
    }

    @Test
    fun goodMetadataSurvivesWhenOnlyOneFieldIsBad() {
        val screen = blueprint().decodeScreen(
            """{"screen_id":"s","version":"abc","body":{"type":"text","text":"hi"}}""",
        )
        assertEquals("s", screen.id)
        assertEquals(TestText("hi"), screen.body)
    }

    @Test
    fun validPayloadStillDecodesWithItsMetadata() {
        val screen = blueprint().decodeScreen("""{"screen_id":"s","version":3,"body":{"type":"text","text":"hi"}}""")
        assertEquals("s", screen.id)
        assertEquals(3, screen.version)
        assertEquals(TestText("hi"), screen.body)
    }
}
