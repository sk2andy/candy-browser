package dev.sk2andy.materialbrowser.feeds

object FeedOpml {
    fun preview(xml: String, sources: List<FeedSource>): FeedImportPreview {
        val root = FeedXml.parse(xml)
        require(root.name == "opml") { "Not an OPML document" }
        val entries = mutableListOf<FeedImportEntry>()
        val known = sources.mapTo(mutableSetOf()) { FeedRules.identity(it.url) }
        fun walk(node: FeedXmlNode, folder: String?) {
            val title =
                node.attributes["title"]
                    .orEmpty()
                    .ifBlank { node.attributes["text"].orEmpty() }
                    .take(500)
            val raw =
                node.attributes["xmlurl"]
                    ?: ""
                        .takeIf {
                            node.attributes["type"].orEmpty().lowercase() in setOf("rss", "atom")
                        }
            if (raw != null) {
                require(entries.size < FeedRules.MAX_SOURCES) { "Too many OPML sources" }
                val url = FeedRules.safeUrl(raw)
                val status =
                    when {
                        url == null -> FeedImportStatus.Invalid
                        !known.add(FeedRules.identity(url)) -> FeedImportStatus.Existing
                        else -> FeedImportStatus.New
                    }
                entries.add(
                    FeedImportEntry(title.ifBlank { raw }, url ?: raw.take(2048), folder, status)
                )
            }
            val nextFolder =
                if (node.name == "outline" && raw == null) title.take(100).ifBlank { folder }
                else folder
            node.children.forEach { walk(it, nextFolder) }
        }
        root.child("body")?.let { walk(it, null) }
        return FeedImportPreview(entries)
    }

    fun export(state: FeedState): String = buildString {
        append(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\"><head><title>Candy Feeds</title></head><body>\n"
        )
        fun source(source: FeedSource) {
            append("<outline type=\"rss\" text=\"")
                .append(escape(source.title))
                .append("\" title=\"")
                .append(escape(source.title))
                .append("\" xmlUrl=\"")
                .append(escape(source.url))
                .append("\" htmlUrl=\"")
                .append(escape(source.siteUrl))
                .append("\"/>\n")
        }
        state.sources
            .filter { source -> state.folders.none { it.id == source.folderId } }
            .forEach(::source)
        state.folders.forEach { folder ->
            append("<outline text=\"").append(escape(folder.name)).append("\">\n")
            state.sources.filter { it.folderId == folder.id }.forEach(::source)
            append("</outline>\n")
        }
        append("</body></opml>")
    }

    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
