package app.moti.kosova

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan

/** Font styles the user can pick. Applied to every text in the widget and the app preview. */
object Fonts {

    enum class Role { BODY, DISPLAY, HEAD, STRONG }

    val names = listOf("Standard", "E hollë", "E trashë", "E ngushtë", "Klasike", "Mono")

    // BODY, DISPLAY (big numbers), HEAD (main sentence), STRONG (city, tabs)
    private val table = listOf(
        listOf("sans-serif", "sans-serif-light", "sans-serif", "sans-serif-medium"),
        listOf("sans-serif-light", "sans-serif-thin", "sans-serif-light", "sans-serif"),
        listOf("sans-serif-medium", "sans-serif-medium", "sans-serif-black", "sans-serif-black"),
        listOf("sans-serif-condensed", "sans-serif-condensed-light", "sans-serif-condensed", "sans-serif-condensed-medium"),
        listOf("serif", "serif", "serif", "serif"),
        listOf("monospace", "monospace", "monospace", "monospace"),
    )

    fun family(font: Int, role: Role): String = table[font.coerceIn(0, table.size - 1)][role.ordinal]

    fun span(text: CharSequence, font: Int, role: Role): CharSequence {
        val s = SpannableString(text)
        if (s.isNotEmpty()) s.setSpan(TypefaceSpan(family(font, role)), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    fun typeface(font: Int, role: Role): Typeface = Typeface.create(family(font, role), Typeface.NORMAL)
}
