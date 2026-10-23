package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class AddressBarPrefixVisualTransformationTest {
    @Test
    fun hidesShortcutAndMapsVisibleCursorToQuery() {
        val transformed = AddressBarPrefixVisualTransformation(4)
            .filter(AnnotatedString("!gh compose"))

        assertEquals("compose", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(0, transformed.offsetMapping.originalToTransformed(4))
        assertEquals(7, transformed.offsetMapping.originalToTransformed(11))
        assertEquals(4, transformed.offsetMapping.transformedToOriginal(0))
        assertEquals(11, transformed.offsetMapping.transformedToOriginal(7))
    }

    @Test
    fun emptyQueryKeepsCursorAfterHiddenPrefixForImeDeletion() {
        val transformed = AddressBarPrefixVisualTransformation(4)
            .filter(AnnotatedString("!gh "))

        assertEquals("", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.originalToTransformed(4))
        assertEquals(4, transformed.offsetMapping.transformedToOriginal(0))
    }

    @Test
    fun stalePrefixLengthStaysWithinBuffer() {
        val transformed = AddressBarPrefixVisualTransformation(4)
            .filter(AnnotatedString("!gh"))

        assertEquals("", transformed.text.text)
        assertEquals(3, transformed.offsetMapping.transformedToOriginal(0))
        assertEquals(0, transformed.offsetMapping.originalToTransformed(3))
    }

    @Test
    fun absentPrefixLeavesTextAndOffsetsUnchanged() {
        val transformed = AddressBarPrefixVisualTransformation(0)
            .filter(AnnotatedString("hello"))

        assertEquals("hello", transformed.text.text)
        assertEquals(2, transformed.offsetMapping.originalToTransformed(2))
        assertEquals(2, transformed.offsetMapping.transformedToOriginal(2))
    }
}
