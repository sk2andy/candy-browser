package dev.sk2andy.materialbrowser.feeds

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedCatalogClientTest {
    @Test
    fun `text search uses source results and original feeds instead of provider copies`() =
        runBlocking {
            val requests = mutableListOf<String>()
            val client =
                FeedCatalogClient(
                    fetch = { address ->
                        synchronized(requests) { requests.add(address) }
                        val url = address.toHttpUrl()
                        if (url.encodedPath == "/api/search") {
                            assertEquals("日本の文化 & art", url.queryParameter("q"))
                            assertEquals("12", url.queryParameter("limit"))
                            """{"blogs":[{"slug":"culture","category":"blog"}],"posts":[{"slug":"wrong","title":"Not a source"}]}"""
                        } else source("culture", language = "ja").toString()
                    }
                )
            val result =
                client.search(FeedCatalogQuery(text = "日本の文化 & art", kind = FeedCatalogKind.Blog))
            assertEquals(1, result.sources.size)
            assertEquals("https://culture.example/feed.xml", result.sources.single().feedUrl)
            assertEquals("ja", result.sources.single().language)
            assertNull(result.nextOffset)
            assertEquals(2, requests.size)
        }

    @Test
    fun `news sources remain supported in the providers blogs array`() = runBlocking {
        val client =
            FeedCatalogClient(
                fetch = { address ->
                    if (address.toHttpUrl().encodedPath == "/api/search") {
                        assertEquals("news", address.toHttpUrl().queryParameter("kind"))
                        """{"blogs":[{"slug":"news","category":"news"}]}"""
                    } else source("news", kind = "news").toString()
                }
            )
        val page = client.search(FeedCatalogQuery(text = "science", kind = FeedCatalogKind.News))
        assertEquals(FeedCatalogKind.News, page.sources.single().kind)
    }

    @Test
    fun `unsafe metadata cannot become a subscription or endpoint`() = runBlocking {
        val details = AtomicInteger()
        val client =
            FeedCatalogClient(
                fetch = { address ->
                    if (address.toHttpUrl().encodedPath == "/api/search") {
                        """{"blogs":[{"slug":"../secrets","category":"blog"},{"slug":"bad-feed","category":"blog"},{"slug":"podcast","category":"podcast"},{"slug":"good","category":"blog"}]}"""
                    } else {
                        details.incrementAndGet()
                        val slug = address.toHttpUrl().pathSegments.last()
                        source(slug)
                            .apply {
                                if (slug == "bad-feed")
                                    put("feedUrl", "https://user:secret@example.org/feed")
                                else {
                                    put("title", "<b>Good &amp; calm</b><script>secret()</script>")
                                    put(
                                        "description",
                                        "<img onerror='bad()'>A useful <em>story</em>",
                                    )
                                    put("siteUrl", "javascript:alert(1)")
                                }
                            }
                            .toString()
                    }
                }
            )
        val page = client.search(FeedCatalogQuery(text = "good", kind = FeedCatalogKind.Blog))
        val valid = page.sources.single()
        assertEquals("good", valid.slug)
        assertEquals("Good & calm", valid.title)
        assertEquals("A useful story", valid.description)
        assertEquals("", valid.siteUrl)
        assertEquals(2, details.get())
    }

    @Test
    fun `equivalent feed urls are deduplicated across result types`() = runBlocking {
        val client =
            FeedCatalogClient(
                fetch = { address ->
                    val url = address.toHttpUrl()
                    if (url.encodedPath == "/api/search") {
                        val slug = if (url.queryParameter("kind") == "blogs") "first" else "second"
                        """{"blogs":[{"slug":"$slug","category":"blog"}]}"""
                    } else
                        source(url.pathSegments.last())
                            .apply { put("feedUrl", "https://same.example/feed#fragment") }
                            .toString()
                }
            )
        val page = client.search(FeedCatalogQuery(text = "same"))
        assertEquals(1, page.sources.size)
    }

    @Test
    fun `topics use supported groups and the providers real offset`() = runBlocking {
        val client =
            FeedCatalogClient(
                fetch = { address ->
                    val url = address.toHttpUrl()
                    if (url.encodedPath.startsWith("/api/topics/")) {
                        assertEquals("blogs", url.queryParameter("group"))
                        assertEquals("16", url.queryParameter("offset"))
                        JSONObject()
                            .put("total", 48)
                            .put(
                                "feeds",
                                JSONArray().apply { repeat(16) { put(source("source-$it")) } },
                            )
                            .toString()
                    } else source(url.pathSegments.last()).toString()
                }
            )
        val page =
            client.search(
                FeedCatalogQuery(topic = "science", kind = FeedCatalogKind.Blog, offset = 16)
            )
        assertEquals(32, page.nextOffset)
        assertEquals(16, page.sources.size)
    }

    @Test
    fun `short topic page ends pagination without global counts`() = runBlocking {
        val client =
            FeedCatalogClient(
                fetch = { address ->
                    if (address.toHttpUrl().encodedPath.startsWith("/api/topics/"))
                        """{"feeds":[{"slug":"one","kind":"blog"}],"topicTotal":999999}"""
                    else source("one").toString()
                }
            )
        val page = client.search(FeedCatalogQuery(topic = "science", kind = FeedCatalogKind.Blog))
        assertNull(page.nextOffset)
    }

    @Test
    fun `topic group without sources does not hide the other groups sources`() = runBlocking {
        val client =
            FeedCatalogClient(
                fetch = { address ->
                    val url = address.toHttpUrl()
                    if (url.encodedPath.startsWith("/api/topics/")) {
                        if (url.queryParameter("group") == "news")
                            throw FeedCatalogHttpException(404)
                        JSONObject().put("feeds", JSONArray().put(source("one"))).toString()
                    } else source("one").toString()
                }
            )
        val page = client.search(FeedCatalogQuery(topic = "niche"))
        assertEquals("one", page.sources.single().slug)
        assertNull(page.nextOffset)
    }

    @Test
    fun `blank input and rejected private access never make requests`() = runBlocking {
        val calls = AtomicInteger()
        val client =
            FeedCatalogClient(
                fetch = {
                    calls.incrementAndGet()
                    error("Unexpected network")
                }
            )
        assertEquals(emptyList<FeedCatalogSource>(), client.search(FeedCatalogQuery()).sources)
        val denied =
            FeedCatalogClient(
                fetch = {
                    calls.incrementAndGet()
                    error("Unexpected network")
                },
                requireAccess = { FeedCatalogRules.requireNormalProfile(isPrivate = true) },
            )
        val result = runCatching { denied.search(FeedCatalogQuery(text = "private words")) }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, calls.get())
    }

    @Test
    fun `profile access is checked again after receiving a response`() = runBlocking {
        var accessible = true
        val calls = AtomicInteger()
        val client =
            FeedCatalogClient(
                fetch = {
                    calls.incrementAndGet()
                    accessible = false
                    """{"blogs":[{"slug":"private","kind":"blog"}]}"""
                },
                requireAccess = { check(accessible) },
            )
        val result = runCatching {
            client.search(FeedCatalogQuery(text = "secret", kind = FeedCatalogKind.Blog))
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(1, calls.get())
    }

    @Test
    fun `cancellation is not translated into a successful empty result`() = runBlocking {
        val client = FeedCatalogClient(fetch = { throw CancellationException("closed") })
        val result = runCatching {
            client.search(FeedCatalogQuery(text = "cancel", kind = FeedCatalogKind.Blog))
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `failed metadata reads retain safe topic entries but all failed search details fail visibly`() =
        runBlocking {
            val topicClient =
                FeedCatalogClient(
                    fetch = { address ->
                        if (address.toHttpUrl().encodedPath.startsWith("/api/topics/"))
                            JSONObject()
                                .put("feeds", JSONArray().put(source("available")))
                                .toString()
                        else throw IOException("Offline")
                    }
                )
            val topicPage =
                topicClient.search(FeedCatalogQuery(topic = "science", kind = FeedCatalogKind.Blog))
            assertEquals("available", topicPage.sources.single().slug)
            val searchClient =
                FeedCatalogClient(
                    fetch = { address ->
                        if (address.toHttpUrl().encodedPath == "/api/search")
                            """{"blogs":[{"slug":"unavailable","category":"blog"}]}"""
                        else throw IOException("Offline")
                    }
                )
            val result = runCatching {
                searchClient.search(FeedCatalogQuery(text = "offline", kind = FeedCatalogKind.Blog))
            }
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun `metadata hydration has at most four concurrent requests`() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val client =
            FeedCatalogClient(
                fetch = { address ->
                    val url = address.toHttpUrl()
                    if (url.encodedPath == "/api/search")
                        JSONObject()
                            .put(
                                "blogs",
                                JSONArray().apply { repeat(12) { put(source("source-$it")) } },
                            )
                            .toString()
                    else {
                        val count = active.incrementAndGet()
                        peak.updateAndGet { maxOf(it, count) }
                        delay(10)
                        active.decrementAndGet()
                        source(url.pathSegments.last()).toString()
                    }
                }
            )
        assertEquals(
            12,
            client
                .search(FeedCatalogQuery(text = "bounded", kind = FeedCatalogKind.Blog))
                .sources
                .size,
        )
        assertEquals(4, peak.get())
    }

    @Test
    fun `malformed response and false search pagination fail before returning sources`() =
        runBlocking {
            val client = FeedCatalogClient(fetch = { "not-json" })
            assertTrue(
                runCatching {
                        client.search(FeedCatalogQuery(text = "bad", kind = FeedCatalogKind.Blog))
                    }
                    .exceptionOrNull() is IOException
            )
            assertTrue(
                runCatching { client.search(FeedCatalogQuery(text = "page", offset = 12)) }
                    .exceptionOrNull() is IllegalArgumentException
            )
        }

    @Test
    fun `query bounds preserve international text and reject path injection`() {
        assertEquals("ニュース", FeedCatalogRules.validate(FeedCatalogQuery(text = " ニュース ")).text)
        assertTrue(
            runCatching { FeedCatalogRules.validate(FeedCatalogQuery(topic = "../search")) }
                .isFailure
        )
        assertTrue(
            runCatching { FeedCatalogRules.validate(FeedCatalogQuery(text = "x".repeat(201))) }
                .isFailure
        )
        assertTrue(
            runCatching { FeedCatalogRules.validate(FeedCatalogQuery(text = "one\ntwo")) }.isFailure
        )
        assertNull(FeedCatalogRules.safeSourceUrl("javascript:alert(1)"))
        assertNull(FeedCatalogRules.safeSourceUrl("https://example.org/" + "x".repeat(2048)))
        assertNull(FeedCatalogRules.safeSlug("a".repeat(241)))
        assertEquals("pt-BR", FeedCatalogRules.language("pt_br"))
        assertEquals("", FeedCatalogRules.language("<script>"))
        assertFalse(FeedCatalogRules.safeSourceUrl("https://example.org/feed").isNullOrEmpty())
        assertEquals(
            "https://xn--r8jz45g.xn--zckzah/rss",
            FeedCatalogRules.safeSourceUrl("https://例え.テスト/rss"),
        )
    }

    private fun source(slug: String, language: String = "en", kind: String = "blog") =
        JSONObject()
            .put("slug", slug)
            .put("title", "Source $slug")
            .put("description", "A story from $slug")
            .put("siteUrl", "https://$slug.example/")
            .put("feedUrl", "https://$slug.example/feed.xml")
            .put("language", language)
            .put("kind", kind)
}
