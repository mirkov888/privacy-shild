package com.mora.shield

import android.content.Context
import java.net.URLDecoder

/** Pembersih tautan (gaya ClearURLs) dan pengalih frontend privat (gaya UntrackMe). Semuanya lokal. */
object Linker {
    class Result(val url: String, val removed: List<String>, val unwrapped: Boolean, val rewritten: Boolean)

    private val URL_RE = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    private val GLOBAL_EXACT = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "igshid", "igsh", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "hsctatracking", "mkt_tok", "vero_id",
        "wickedid", "oly_enc_id", "oly_anon_id", "s_cid", "cmpid", "ocid", "ncid", "_ga", "_gl",
        "gad_source", "gad_campaignid", "fb_action_ids", "fb_action_types", "fb_source", "ref_src", "ref_url",
        "__s", "_openstat", "rb_clickid", "spm", "scm"
    )
    private val GLOBAL_PREFIX = listOf("utm_", "pk_", "piwik_", "mtm_", "matomo_", "hmb_", "itm_")

    /** akhiran host -> parameter tambahan yang dibuang. */
    private val HOST_PARAMS: List<Pair<String, Set<String>>> = listOf(
        "youtube.com" to setOf("si", "feature", "pp"),
        "youtu.be" to setOf("si", "feature", "pp"),
        "spotify.com" to setOf("si", "context", "nd"),
        "twitter.com" to setOf("s", "t", "cxt"),
        "x.com" to setOf("s", "t", "cxt"),
        "tiktok.com" to setOf("_t", "_r", "is_from_webapp", "sender_device", "sender_web_id", "share_app_id", "share_link_id", "share_item_id", "source", "social_sharing", "tt_from", "u_code", "user_id", "sec_user_id"),
        "amazon." to setOf("ref", "ref_", "linkcode", "linkid", "ie", "qid", "sr", "sprefix", "crid", "tag", "pf_rd_r", "pf_rd_p", "pf_rd_m", "pf_rd_s", "pf_rd_t", "pf_rd_i", "pd_rd_r", "pd_rd_w", "pd_rd_wg", "pd_rd_i"),
        "linkedin.com" to setOf("trk", "trkinfo", "trackingid", "refid", "lipi", "midtoken", "midsig", "eid", "otptoken"),
        "facebook.com" to setOf("__cft__", "__tn__", "ref", "refsrc", "fref", "hc_ref", "__xts__", "acontext", "_rdr"),
        "reddit.com" to setOf("share_id", "ref", "ref_source", "rdt"),
        "aliexpress." to setOf("aff_fcid", "aff_fsk", "aff_platform", "aff_trace_key", "algo_expid", "algo_pvid", "pvid", "sk", "terminal_id"),
        "google." to setOf("ved", "ei", "sei", "sa", "usg", "sxsrf", "oq", "gs_lcp", "gs_lp", "sclient", "sourceid", "uact", "aqs", "rlz", "iflsig", "biw", "bih", "dpr")
    )

    /** akhiran host -> nama parameter berisi tujuan asli (tautan pengalihan). */
    private val UNWRAP: List<Triple<String, String, List<String>>> = listOf(
        Triple("google.", "/url", listOf("q", "url")),
        Triple("facebook.com", "/l.php", listOf("u")),
        Triple("facebook.com", "/flx/warn/", listOf("u")),
        Triple("reddit.com", "/", listOf("url")),
        Triple("youtube.com", "/redirect", listOf("q")),
        Triple("instagram.com", "/", listOf("u")),
        Triple("steamcommunity.com", "/linkfilter/", listOf("u", "url")),
        Triple("tumblr.com", "/redirect", listOf("z"))
    )

    private fun hostMatches(host: String, key: String): Boolean =
        if (key.endsWith(".")) host.contains("." + key) || host.startsWith(key)
        else host == key || host.endsWith(".$key")

    private fun decode(v: String): String = try { URLDecoder.decode(v, "UTF-8") } catch (e: Exception) { v }

    private class Parts(val scheme: String, val host: String, val path: String, val query: String, val frag: String)

    private fun parse(u: String): Parts? {
        val sep = u.indexOf("://")
        if (sep < 0) return null
        val scheme = u.substring(0, sep).lowercase()
        if (scheme != "http" && scheme != "https") return null
        var rest = u.substring(sep + 3)
        var frag = ""
        val hi = rest.indexOf('#')
        if (hi >= 0) { frag = rest.substring(hi + 1); rest = rest.substring(0, hi) }
        var query = ""
        val qi = rest.indexOf('?')
        if (qi >= 0) { query = rest.substring(qi + 1); rest = rest.substring(0, qi) }
        val si = rest.indexOf('/')
        val host = (if (si >= 0) rest.substring(0, si) else rest).lowercase()
        val path = if (si >= 0) rest.substring(si) else ""
        if (host.isEmpty()) return null
        return Parts(scheme, host, path, query, frag)
    }

    private fun build(p: Parts, query: String, path: String = p.path): String {
        val sb = StringBuilder(p.scheme).append("://").append(p.host).append(path)
        if (query.isNotEmpty()) sb.append('?').append(query)
        if (p.frag.isNotEmpty()) sb.append('#').append(p.frag)
        return sb.toString()
    }

    private fun cleanOne(u0: String, removed: MutableList<String>, depth: Int): Pair<String, Boolean> {
        val p = parse(u0) ?: return Pair(u0, false)
        val hostNoPort = p.host.substringBefore(':')
        // 1) bongkar tautan pengalihan
        if (depth < 3) {
            for ((hk, pathPrefix, names) in UNWRAP) {
                if (hostMatches(hostNoPort, hk) && p.path.startsWith(pathPrefix) && (pathPrefix != "/" || p.path == "/")) {
                    val pairs = p.query.split('&').filter { it.contains('=') }
                    for (n in names) {
                        val v = pairs.firstOrNull { it.substringBefore('=').equals(n, true) }?.substringAfter('=')
                        if (v != null) {
                            val t = decode(v)
                            if (t.startsWith("http://") || t.startsWith("https://")) {
                                val inner = cleanOne(t, removed, depth + 1)
                                return Pair(inner.first, true)
                            }
                        }
                    }
                }
            }
            // AMP: google.com/amp/s/contoh.com/halaman
            if (hostMatches(hostNoPort, "google.") && p.path.startsWith("/amp/s/")) {
                val inner = cleanOne("https://" + p.path.removePrefix("/amp/s/") + (if (p.query.isNotEmpty()) "?" + p.query else ""), removed, depth + 1)
                return Pair(inner.first, true)
            }
        }
        // 2) buang parameter pelacak
        val extra = HashSet<String>()
        for ((hk, set) in HOST_PARAMS) if (hostMatches(hostNoPort, hk)) extra.addAll(set)
        val kept = ArrayList<String>()
        if (p.query.isNotEmpty()) {
            for (kv in p.query.split('&')) {
                if (kv.isEmpty()) continue
                val k = kv.substringBefore('=').lowercase()
                val bad = k in GLOBAL_EXACT || k in extra || GLOBAL_PREFIX.any { k.startsWith(it) } ||
                    (k.startsWith("pf_rd_") || k.startsWith("pd_rd_")) && hostMatches(hostNoPort, "amazon.")
                if (bad) removed.add(kv.substringBefore('=')) else kept.add(kv)
            }
        }
        var path = p.path
        if (hostMatches(hostNoPort, "amazon.")) {
            val np = path.replace(Regex("/ref=[^/?#]*"), "")
            if (np != path) { removed.add("ref"); path = np }
        }
        return Pair(build(p, kept.joinToString("&"), path), false)
    }

    /** Membersihkan semua tautan di dalam teks. Bila teks bukan tautan, dikembalikan apa adanya. */
    fun clean(text: String): Result {
        val removed = ArrayList<String>()
        var unwrapped = false
        val out = URL_RE.replace(text) { m ->
            val raw = m.value
            val trail = raw.takeLastWhile { it in ".,;:)!]" }
            val core = raw.dropLast(trail.length)
            val r = cleanOne(core, removed, 0)
            if (r.second) unwrapped = true
            r.first + trail
        }
        return Result(out, removed, unwrapped, false)
    }

    // ------------------------------------------------------------ Frontend privat

    const val PREFS = "mora"
    fun validHost(h: String): Boolean = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,60})(:[0-9]{1,5})?$").matches(h) && h.contains('.')

    private fun youtubeId(p: Parts): String? {
        val h = p.host.substringBefore(':')
        if (h == "youtu.be") return p.path.removePrefix("/").substringBefore('/').ifEmpty { null }
        val q = p.query.split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
        if (!q.isNullOrEmpty()) return q
        for (pre in listOf("/shorts/", "/embed/", "/live/")) if (p.path.startsWith(pre)) return p.path.removePrefix(pre).substringBefore('/').ifEmpty { null }
        return null
    }

    /** Hasil pengalihan; targetPackage diisi bila harus dibuka di aplikasi tertentu. */
    class Redirect(val url: String, val targetPackage: String?)

    fun redirect(ctx: Context, url: String): Redirect? {
        val pr = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = parse(url) ?: return null
        val h = p.host.substringBefore(':').removePrefix("www.").removePrefix("m.")
        val isYt = h == "youtube.com" || h == "youtu.be"
        if (isYt) {
            val mode = pr.getString("lk_yt", "off") ?: "off"
            val host = pr.getString("lk_yt_host", "") ?: ""
            val id = youtubeId(p)
            val list = p.query.split('&').firstOrNull { it.startsWith("list=") }
            return when (mode) {
                "invidious", "piped" -> {
                    if (!validHost(host)) return null
                    if (id != null) Redirect("https://$host/watch?v=$id" + (if (list != null) "&$list" else ""), null)
                    else Redirect("https://$host${p.path}" + (if (p.query.isNotEmpty()) "?" + p.query else ""), null)
                }
                "newpipe" -> Redirect(url, "org.schabi.newpipe")
                "libretube" -> Redirect(url, "com.github.libretube")
                else -> null
            }
        }
        if (h == "twitter.com" || h == "x.com") {
            val host = pr.getString("lk_tw_host", "") ?: ""
            if ((pr.getString("lk_tw", "off") ?: "off") == "on" && validHost(host)) return Redirect("https://$host${p.path}", null)
            return null
        }
        if (h == "reddit.com" || h == "old.reddit.com") {
            val host = pr.getString("lk_rd_host", "") ?: ""
            if ((pr.getString("lk_rd", "off") ?: "off") == "on" && validHost(host)) {
                return Redirect("https://$host${p.path}" + (if (p.query.isNotEmpty()) "?" + p.query else ""), null)
            }
            return null
        }
        return null
    }
}
