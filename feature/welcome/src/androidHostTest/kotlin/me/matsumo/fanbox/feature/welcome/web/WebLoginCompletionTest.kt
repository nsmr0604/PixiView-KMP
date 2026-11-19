package me.matsumo.fanbox.feature.welcome.web

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WebView でのログインが終わったとみなす条件を検証するテスト。
 *
 * 二段階認証などの途中の画面を挟むと、FANBOX は `return_to` とは別のページへ戻すことがある。
 * 戻り先の URL との一致だけで判定すると、ログインを終えても確かめに行かず、利用者が WebView に取り残される。
 */
class WebLoginCompletionTest {

    private val redirectUrl = "https://www.fanbox.cc/creators/find"

    private fun shouldCheck(
        url: String?,
        sessionId: String? = "session",
        checkedSessionId: String? = null,
    ) = shouldCheckWebLogin(
        url = url,
        redirectUrl = redirectUrl,
        sessionId = sessionId,
        checkedSessionId = checkedSessionId,
    )

    @Test
    fun redirectUrlIsAlwaysChecked() {
        assertTrue(shouldCheck(url = redirectUrl, sessionId = null))
        assertTrue(shouldCheck(url = redirectUrl, checkedSessionId = "session"))
    }

    @Test
    fun otherFanboxPageWithUncheckedSessionIsChecked() {
        assertTrue(shouldCheck(url = "https://www.fanbox.cc/"))
        assertTrue(shouldCheck(url = "https://www.fanbox.cc/creators/find?lang=ja"))
        assertTrue(shouldCheck(url = "https://creator.fanbox.cc/posts/1"))
    }

    @Test
    fun checkedSessionIsNotCheckedAgain() {
        assertFalse(shouldCheck(url = "https://www.fanbox.cc/", checkedSessionId = "session"))
        assertTrue(shouldCheck(url = "https://www.fanbox.cc/", sessionId = "new-session", checkedSessionId = "session"))
    }

    @Test
    fun otherFanboxPageWithoutSessionIsNotChecked() {
        assertFalse(shouldCheck(url = "https://www.fanbox.cc/", sessionId = null))
    }

    @Test
    fun authCallbackIsDetected() {
        assertTrue("https://www.fanbox.cc/auth/callback?code=x&state=y".isFanboxAuthCallback())
        assertTrue("https://fanbox.cc/auth/callback?code=x".isFanboxAuthCallback())
        assertTrue("https://www.fanbox.cc/auth/callback/sub?code=x".isFanboxAuthCallback())
        assertFalse("https://www.fanbox.cc/auth/callback-elsewhere?code=x".isFanboxAuthCallback())
        assertFalse("https://www.fanbox.cc/auth/start".isFanboxAuthCallback())
        assertFalse("https://accounts.pixiv.net/auth/callback?code=x".isFanboxAuthCallback())
        assertFalse("https://www.fanbox.cc/".isFanboxAuthCallback())
        assertFalse("https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback?code=x".isFanboxAuthCallback())
    }

    @Test
    fun authCallbackIsCheckedLikeOtherFanboxPages() {
        assertTrue(shouldCheck(url = "https://www.fanbox.cc/auth/callback?code=x&state=y"))
    }

    @Test
    fun loginPageAndNonFanboxPagesAreNotChecked() {
        assertFalse(shouldCheck(url = "https://www.fanbox.cc/login?return_to=$redirectUrl"))
        assertFalse(shouldCheck(url = "https://accounts.pixiv.net/login"))
        assertFalse(shouldCheck(url = "https://www.pixiv.net/"))
        assertFalse(shouldCheck(url = "https://www.fanbox.cc.example.com/"))
        assertFalse(shouldCheck(url = "http://www.fanbox.cc/"))
        assertFalse(shouldCheck(url = null))
    }

    @Test
    fun urlHostIsExtracted() {
        assertEquals("accounts.pixiv.net", "https://accounts.pixiv.net/login?return_to=x".urlHost())
        assertEquals("www.fanbox.cc", "https://www.fanbox.cc?x=1".urlHost())
        assertNull("about:blank".urlHost())
    }

    @Test
    fun webLoginUrlEncodesReturnTo() {
        assertEquals(
            "https://www.fanbox.cc/login?return_to=https%3A%2F%2Fwww.fanbox.cc%2Fcreators%2Ffind",
            webLoginUrl(redirectUrl),
        )
    }

    @Test
    fun webLoginUrlKeepsQueryStructureWithUnsafeReturnTo() {
        val url = webLoginUrl("https://www.fanbox.cc/creators/find?a=1&b=2#top")

        assertTrue(url.startsWith("https://www.fanbox.cc/login?return_to="))
        assertEquals(1, url.count { it == '?' })
    }

    @Test
    fun encodeReturnToQueryValueKeepsUnreservedCharacters() {
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~".encodeReturnToQueryValue(),
        )
    }

    @Test
    fun encodeReturnToQueryValueEncodesReservedAndMultiByteCharacters() {
        assertEquals("a%26b%3Fc%23d", "a&b?c#d".encodeReturnToQueryValue())
        assertEquals("https%3A%2F%2Fwww.fanbox.cc%2F%E3%81%82", "https://www.fanbox.cc/あ".encodeReturnToQueryValue())
    }
}
