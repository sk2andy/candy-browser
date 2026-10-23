package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationMarkdownBlock
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationMarkdownInline
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationMarkdownRules
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationMarkdownStyle
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

@Composable
internal fun WebAnnotationMarkdown(
    note: String,
    modifier: Modifier = Modifier,
    onOpenLink: ((String) -> Unit)? = null,
) {
    val blocks = remember(note) { WebAnnotationMarkdownRules.parse(note) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is WebAnnotationMarkdownBlock.Heading -> AnnotationMarkdownText(
                    content = block.content,
                    modifier = Modifier.semantics { heading() },
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(fontWeight = FontWeight.Bold),
                    onOpenLink = onOpenLink,
                )
                is WebAnnotationMarkdownBlock.Paragraph -> AnnotationMarkdownText(
                    content = block.content,
                    onOpenLink = onOpenLink,
                )
                is WebAnnotationMarkdownBlock.ListItem -> Row {
                    Text(
                        text = block.marker,
                        modifier = Modifier.width(30.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AnnotationMarkdownText(
                        content = block.content,
                        modifier = Modifier.weight(1f),
                        onOpenLink = onOpenLink,
                    )
                }
                is WebAnnotationMarkdownBlock.Quote -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ) {
                    AnnotationMarkdownText(
                        content = block.content,
                        modifier = Modifier.padding(12.dp),
                        onOpenLink = onOpenLink,
                    )
                }
                is WebAnnotationMarkdownBlock.Code -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = block.content,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                WebAnnotationMarkdownBlock.Divider -> HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AnnotationMarkdownText(
    content: WebAnnotationMarkdownInline,
    onOpenLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val annotated = annotationMarkdownAnnotatedString(
        content = content,
        linkColor = MaterialTheme.colorScheme.primary,
        codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        onOpenLink = onOpenLink,
    )
    Text(text = annotated, modifier = modifier, style = style)
}

private fun annotationMarkdownAnnotatedString(
    content: WebAnnotationMarkdownInline,
    linkColor: Color,
    codeBackground: Color,
    onOpenLink: ((String) -> Unit)?,
): AnnotatedString = buildAnnotatedString {
    append(content.text)
    content.decorations.forEach { decoration ->
        val style = when (decoration.style) {
            WebAnnotationMarkdownStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            WebAnnotationMarkdownStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            WebAnnotationMarkdownStyle.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            WebAnnotationMarkdownStyle.Link -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        }
        addStyle(style, decoration.start, decoration.end)
        if (decoration.style == WebAnnotationMarkdownStyle.Link && decoration.target != null && onOpenLink != null) {
            addLink(
                url = LinkAnnotation.Url(decoration.target) { link ->
                    BrowserUriPolicy.normalizeHttpUrl((link as LinkAnnotation.Url).url)?.let(onOpenLink)
                },
                start = decoration.start,
                end = decoration.end,
            )
        }
    }
}
