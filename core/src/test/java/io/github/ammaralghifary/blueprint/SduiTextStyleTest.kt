package io.github.ammaralghifary.blueprint

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SduiTextStyleTest {
    private val base = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Color.Black)

    @Test
    fun typographyFieldsDecode() {
        val m = Json { ignoreUnknownKeys = true }.decodeFromString(
            SduiModifier.serializer(),
            """{"text_color":"#FF0000","font_size":20,"font_weight":700}""",
        )
        assertEquals("#FF0000", m.textColor)
        assertEquals(20, m.fontSize)
        assertEquals(700, m.fontWeight)
        assertNull(SduiModifier().textColor)
    }

    @Test
    fun toTextStyleAppliesColorSizeWeight() {
        val s = SduiModifier(textColor = "#FF0000", fontSize = 20, fontWeight = 700).toTextStyle(base)
        assertEquals(Color(0xFFFF0000), s.color)
        assertEquals(20.sp, s.fontSize)
        assertEquals(FontWeight(700), s.fontWeight)
    }

    @Test
    fun toTextStyleIgnoresBadValues() {
        assertEquals(base, null.toTextStyle(base))
        val s = SduiModifier(textColor = "nope", fontSize = -3).toTextStyle(base)
        assertEquals(base, s)
    }

    @Test
    fun toTextStyleClampsWeight() {
        assertEquals(FontWeight(900), SduiModifier(fontWeight = 5000).toTextStyle(base).fontWeight)
        assertEquals(FontWeight(100), SduiModifier(fontWeight = -1).toTextStyle(base).fontWeight)
    }
}
