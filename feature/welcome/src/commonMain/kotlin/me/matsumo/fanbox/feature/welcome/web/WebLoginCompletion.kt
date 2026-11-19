package me.matsumo.fanbox.feature.welcome.web

/**
 * WebView でのログインが終わったとみなし、セッションを確かめに行くかを決める。
 *
 * FANBOX はログインを終えると `return_to` の URL へ戻すが、二段階認証やセキュリティ設定のお知らせなどの
 * 途中の画面を挟むと、別の FANBOX のページへ戻ることがある。戻り先の URL に一致した場合に加えて、
 * ログイン画面以外の FANBOX のページで、まだ確かめていない FANBOXSESSID がある場合も確かめる。
 *
 * [url] は WebView が最後に読み込んだ URL、[redirectUrl] は `return_to` に渡した URL、[sessionId] は
 * そのときの FANBOXSESSID、[checkedSessionId] は前に確かめた FANBOXSESSID である。同じセッションを
 * 確かめ直さないため、FANBOX のページを移るたびに確かめることはない。
 */
internal fun shouldCheckWebLogin(
    url: String?,
    redirectUrl: String,
    sessionId: String?,
    checkedSessionId: String?,
): Boolean {
    if (url == null) return false
    if (url == redirectUrl) return true

    val isUncheckedSession = sessionId != null && sessionId != checkedSessionId

    return isUncheckedSession && url.isFanboxPageAfterLogin()
}

/**
 * WebView で開く FANBOX のログイン URL を組み立てる。
 *
 * FANBOX は `/login?return_to=` を受けると、pixiv のログイン画面
 * （`accounts.pixiv.net/login?prompt=select_account&return_to=<オリジン>/auth/start&source=fanbox`）
 * へ案内し、ログインの後に FANBOXSESSID を発行して `return_to` へ戻す。
 * すでに pixiv へログインしている場合は、ログイン画面を挟まず `return_to` へ直行する。
 *
 * `return_to` の値は FANBOX がそのまま `auth/start` のパラメータへ転記するため、
 * [String.encodeReturnToQueryValue] でエンコードしてから埋め込む。
 */
internal fun webLoginUrl(returnTo: String): String {
    val encodedReturnTo = returnTo.encodeReturnToQueryValue()

    return "$FANBOX_LOGIN_ORIGIN/login?return_to=$encodedReturnTo"
}

/** WebView でログインを開く FANBOX のオリジン。ログイン後の Cookie もここから取得する。 */
internal const val FANBOX_LOGIN_ORIGIN: String = "https://www.fanbox.cc"

/**
 * クエリの値として安全なよう、URL 予約文字をパーセントエンコーディングする。
 *
 * `return_to` をエンコードしないと、値の中の `&` や `#` が URL の構造として解釈されて壊れる。
 * RFC 3986 の unreserved 文字（ASCII の英数字と `-` `.` `_` `~`）以外をすべてエンコードする。
 */
internal fun String.encodeReturnToQueryValue(): String = buildString {
    for (byte in encodeToByteArray()) {
        val unsigned = byte.toInt() and 0xFF
        val character = unsigned.toChar()

        if (character.isUnreservedQueryCharacter()) {
            append(character)
        } else {
            append('%')
            append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

/** クエリの値としてそのまま使える ASCII の文字。RFC 3986 の unreserved 文字と一致する。 */
private fun Char.isUnreservedQueryCharacter(): Boolean {
    val isAsciiLetterOrDigit = isLetterOrDigit() && code < 128
    val isUrlSafePunctuation = this in "-._~"

    return isAsciiLetterOrDigit || isUrlSafePunctuation
}

/** URL のホストを返す。`https://` で始まらない場合は null を返す。 */
internal fun String.urlHost(): String? {
    if (!startsWith("https://")) return null

    return removePrefix("https://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
}

private fun String.isFanboxPageAfterLogin(): Boolean {
    val host = urlHost() ?: return false
    val path = removePrefix("https://").removePrefix(host)
    val isFanboxHost = host == "fanbox.cc" || host.endsWith(".fanbox.cc")

    return isFanboxHost && !path.startsWith("/login")
}

/**
 * FANBOX の OAuth コールバック (`/auth/callback`) かどうか。
 *
 * 公式のログイン処理では、pixiv アカウントでの認証のあとにこの URL へ認可コードが届き、
 * ここで FANBOXSESSID が発行される。ログイン処理の完了地点として扱う。
 */
internal fun String.isFanboxAuthCallback(): Boolean {
    val host = urlHost() ?: return false
    val path = removePrefix("https://").removePrefix(host).substringBefore('?')
    val isFanboxHost = host == "fanbox.cc" || host.endsWith(".fanbox.cc")

    return isFanboxHost && (path == "/auth/callback" || path.startsWith("/auth/callback/"))
}
