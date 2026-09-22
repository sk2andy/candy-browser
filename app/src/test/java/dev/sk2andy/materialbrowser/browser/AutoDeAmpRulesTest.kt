package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoDeAmpRulesTest {
    @Test
    fun `unwraps trusted Google HTTPS and HTTP AMP pages`() {
        assertEquals(
            "https://news.example.com/story?edition=de#comments",
            AutoDeAmpRules.publisherUrlFor(
                "https://www.google.com/amp/s/news.example.com/story?edition=de#comments",
            ),
        )
        assertEquals(
            "http://example.com/story",
            AutoDeAmpRules.publisherUrlFor("https://google.com/amp/example.com/story"),
        )
    }

    @Test
    fun `unwraps matching Google AMP cache document hosts`() {
        assertEquals(
            "https://news.example.com/story?edition=de#comments",
            AutoDeAmpRules.publisherUrlFor(
                "https://news-example-com.cdn.ampproject.org/c/s/" +
                    "news.example.com/story?edition=de#comments",
            ),
        )
        assertEquals(
            "http://foo-bar.example.com/story",
            AutoDeAmpRules.publisherUrlFor(
                "https://foo--bar-example-com.cdn.ampproject.org/c/" +
                    "foo-bar.example.com/story",
            ),
        )
        assertEquals(
            "https://news.example.com/live#latest",
            AutoDeAmpRules.publisherUrlFor(
                "https://news-example-com.cdn.ampproject.org/v/s/" +
                    "news.example.com/live#latest",
            ),
        )
        assertEquals(
            "https://en-us.example.com/story",
            AutoDeAmpRules.publisherUrlFor(
                "https://0-en--us-example-com-0.cdn.ampproject.org/c/s/" +
                    "en-us.example.com/story",
            ),
        )
    }

    @Test
    fun `rejects lookalike and unsupported wrapper URLs`() {
        listOf(
            "https://www.google.com.evil.example/amp/s/news.example/story",
            "http://www.google.com/amp/s/news.example/story",
            "https://www.google.com:8443/amp/s/news.example/story",
            "https://user:secret@www.google.com/amp/s/news.example/story",
            "https://www.google.com/search?q=amp",
            "https://www.google.com/amp/i/news.example/image.png",
            "https://cdn.ampproject.org/c/s/news.example/story",
            "https://one.two.cdn.ampproject.org/c/s/news.example/story",
            "https://news-example-com.cdn.ampproject.org/wp/s/news.example/story",
            "https://news-example-com.cdn.ampproject.org/i/s/news.example/image.png",
        ).forEach { url -> assertNull(url, AutoDeAmpRules.publisherUrlFor(url)) }
    }

    @Test
    fun `rejects mismatched or irreversible cache publisher hosts`() {
        assertNull(
            AutoDeAmpRules.publisherUrlFor(
                "https://evil-example.cdn.ampproject.org/c/s/news.example/story",
            ),
        )
        assertNull(
            AutoDeAmpRules.publisherUrlFor(
                "https://v2c4ucasgcskftbjt4c7phpkbqedcdcqo23tkamleapoa5o6fygq." +
                    "cdn.ampproject.org/c/s/news.example/story",
            ),
        )
    }

    @Test
    fun `separates publisher parameters from viewer metadata`() {
        assertEquals(
            "https://news.example.com/story?edition=de",
            AutoDeAmpRules.publisherUrlFor(
                "https://www.google.com/amp/s/news.example.com/story" +
                    "?usqp=cache-token&edition=de",
            ),
        )
        assertEquals(
            "https://news.example.com/story?amp_js_v=publisher-value#amp_tf=publisher-anchor",
            AutoDeAmpRules.publisherUrlFor(
                "https://news-example-com.cdn.ampproject.org/c/s/" +
                    "news.example.com/story" +
                    "?amp_js_v=publisher-value#amp_tf=publisher-anchor",
            ),
        )
        assertEquals(
            "https://news.example.com/story?edition=de#comments",
            AutoDeAmpRules.publisherUrlFor(
                "https://news-example-com.cdn.ampproject.org/v/s/" +
                    "news.example.com/story?amp_js_v=0.1&edition=de" +
                    "#amp_tf=From%20%251%24s&referrer=https%3A%2F%2Fgoogle.com&comments",
            ),
        )
        assertEquals(
            "https://news.example.com/story",
            AutoDeAmpRules.publisherUrlFor(
                "https://www.google.com/amp/s/news.example.com/story" +
                    "?usqp=cache-token&amp_js_v=0.1" +
                    "#ampshare=https%3A%2F%2Fnews.example.com%2Fstory&aoh=123",
            ),
        )
    }

    @Test
    fun `rejects unsafe ambiguous and local publisher targets`() {
        listOf(
            "https://www.google.com/amp/s/user:secret@news.example/story",
            "https://www.google.com/amp/s/https://news.example/story",
            "https://www.google.com/amp/s//news.example/story",
            "https://www.google.com/amp/s/localhost/story",
            "https://www.google.com/amp/s/127.0.0.1/story",
            "https://www.google.com/amp/s/news.example:8443/story",
            "https://www.google.com/amp/s/news.example/line%0Abreak",
            "https://www.google.com/amp/s/news.example/tab%09break",
        ).forEach { url -> assertNull(url, AutoDeAmpRules.publisherUrlFor(url)) }
    }

    @Test
    fun `rejects nested AMP wrappers to prevent replacement loops`() {
        assertNull(
            AutoDeAmpRules.publisherUrlFor(
                "https://www.google.com/amp/s/" +
                    "news-example-com.cdn.ampproject.org/c/s/news.example.com/story",
            ),
        )
        assertNull(
            AutoDeAmpRules.publisherUrlFor(
                "https://www-google-com.cdn.ampproject.org/c/s/" +
                    "www.google.com/amp/s/news.example/story",
            ),
        )
        assertNull(
            AutoDeAmpRules.publisherUrlFor(
                "https://www.google.com/amp/www.google.com/amp/s/news.example/story",
            ),
        )
    }
}
