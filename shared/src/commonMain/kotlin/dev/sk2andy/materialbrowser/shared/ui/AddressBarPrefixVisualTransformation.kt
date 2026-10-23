package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Keeps the shortcut in the editing buffer so an IME can delete an empty query's prefix. */
internal class AddressBarPrefixVisualTransformation(
    private val hiddenPrefixLength: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val prefixLength = hiddenPrefixLength.coerceIn(0, text.length)
        return TransformedText(
            text = text.subSequence(prefixLength, text.length),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    (offset - prefixLength).coerceIn(0, text.length - prefixLength)

                override fun transformedToOriginal(offset: Int): Int =
                    (offset + prefixLength).coerceIn(prefixLength, text.length)
            },
        )
    }
}
