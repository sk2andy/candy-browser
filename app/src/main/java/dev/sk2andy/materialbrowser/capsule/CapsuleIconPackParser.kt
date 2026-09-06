package dev.sk2andy.materialbrowser.capsule

import org.xmlpull.v1.XmlPullParser

internal object CapsuleIconPackParser {
    fun parse(
        packageName: String,
        parser: XmlPullParser,
    ): List<CapsuleIconPackEntry> {
        val componentsByDrawable = linkedMapOf<String, MutableSet<String>>()
        var event = parser.eventType
        var parsedEvents = 0
        while (
            event != XmlPullParser.END_DOCUMENT &&
            componentsByDrawable.size < MAX_ENTRIES &&
            parsedEvents < MAX_XML_EVENTS
        ) {
            if (event == XmlPullParser.START_TAG && parser.name == ITEM_TAG) {
                val drawableName = parser.getAttributeValue(null, DRAWABLE_ATTRIBUTE)
                    ?.takeIf(::isValidDrawableName)
                if (drawableName != null) {
                    val components = componentsByDrawable.getOrPut(drawableName) { linkedSetOf() }
                    parser.getAttributeValue(null, COMPONENT_ATTRIBUTE)
                        ?.take(MAX_COMPONENT_LENGTH)
                        ?.takeIf(String::isNotBlank)
                        ?.let { component ->
                            if (components.size < MAX_COMPONENTS_PER_DRAWABLE) {
                                components += component
                            }
                        }
                }
            }
            event = parser.next()
            parsedEvents++
        }
        return componentsByDrawable
            .map { (drawableName, components) ->
                CapsuleIconPackRules.entry(
                    packageName = packageName,
                    drawableName = drawableName,
                    components = components,
                )
            }
            .sortedWith(
                compareBy<CapsuleIconPackEntry, String>(
                    String.CASE_INSENSITIVE_ORDER,
                ) { it.label }
                    .thenBy { it.drawableName },
            )
    }

    private fun isValidDrawableName(value: String): Boolean =
        value.length in 1..MAX_DRAWABLE_NAME_LENGTH && value.matches(DRAWABLE_NAME)

    private const val ITEM_TAG = "item"
    private const val DRAWABLE_ATTRIBUTE = "drawable"
    private const val COMPONENT_ATTRIBUTE = "component"
    private const val MAX_ENTRIES = 20_000
    private const val MAX_XML_EVENTS = 100_000
    private const val MAX_COMPONENTS_PER_DRAWABLE = 8
    private const val MAX_COMPONENT_LENGTH = 256
    private const val MAX_DRAWABLE_NAME_LENGTH = 128
    private val DRAWABLE_NAME = Regex("[a-zA-Z0-9_.]+")
}
