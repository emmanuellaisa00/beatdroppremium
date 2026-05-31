package com.beatdrop.kt.youtube

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

// Chrome 124 Android UA — same spoof as the React Native app
private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

// ─── IFrame HTML (direct port of YoutubeWebPlayer.tsx) ──────────────────────
// Key: defines window.ReactNativeWebView shim → delegates to @JavascriptInterface
private val YT_IFRAME_HTML = """<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>*{margin:0;padding:0;background:#000;overflow:hidden}</style>
</head><body><div id="player"></div><script>
window.ReactNativeWebView={postMessage:function(m){try{window.AndroidBridge.onMessage(m)}catch(e){}}};
var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';document.head.appendChild(tag);
window.__ytPlayer=null;window.__ytQueue=[];window.__ytQueueIdx=0;window.__pending=null;window.__progressTimer=null;
function post(t,e){try{window.ReactNativeWebView.postMessage(JSON.stringify(Object.assign({type:t},e||{})))}catch(e){}}
function sendProgress(){if(!window.__ytPlayer||!window.__ytPlayer.getCurrentTime)return;post('progress',{position:window.__ytPlayer.getCurrentTime()||0,duration:window.__ytPlayer.getDuration()||0})}
function startProgress(){stopProgress();window.__progressTimer=setInterval(sendProgress,500)}
function stopProgress(){if(window.__progressTimer){clearInterval(window.__progressTimer);window.__progressTimer=null}}
function onYouTubeIframeAPIReady(){
  window.__ytPlayer=new YT.Player('player',{height:'1',width:'1',
    playerVars:{autoplay:1,controls:0,playsinline:1,rel:0,iv_load_policy:3,disablekb:1,modestbranding:1,fs:0,origin:'https://www.youtube.com'},
    events:{
      onReady:function(){post('ready',{});if(window.__pending){window.__ytPlayer.loadVideoById(window.__pending);window.__pending=null}},
      onStateChange:function(e){post('state',{state:e.data});if(e.data===1){startProgress()}else{stopProgress();sendProgress();if(e.data===0&&window.__ytQueue.length>1){window.__ytQueueIdx=(window.__ytQueueIdx+1)%window.__ytQueue.length;var n=window.__ytQueue[window.__ytQueueIdx];if(n)window.__ytPlayer.loadVideoById(n)}}},
      onError:function(e){stopProgress();post('error',{code:e.data})}
    }
  });
}
window.__ytCmd={
  load:function(id){if(!window.__ytPlayer||!window.__ytPlayer.loadVideoById){window.__pending=id;return}window.__ytQueue=[id];window.__ytQueueIdx=0;window.__ytPlayer.loadVideoById(id)},
  loadQueue:function(ids,idx){if(!ids||!ids.length)return;window.__ytQueue=ids;window.__ytQueueIdx=idx||0;var id=ids[window.__ytQueueIdx];if(!id)return;if(!window.__ytPlayer||!window.__ytPlayer.loadVideoById){window.__pending=id;return}window.__ytPlayer.loadVideoById(id)},
  play:function(){window.__ytPlayer&&window.__ytPlayer.playVideo&&window.__ytPlayer.playVideo()},
  pause:function(){window.__ytPlayer&&window.__ytPlayer.pauseVideo&&window.__ytPlayer.pauseVideo()},
  stop:function(){window.__ytPlayer&&window.__ytPlayer.stopVideo&&window.__ytPlayer.stopVideo()},
  seek:function(s){window.__ytPlayer&&window.__ytPlayer.seekTo&&window.__ytPlayer.seekTo(s,true)},
  volume:function(v){window.__ytPlayer&&window.__ytPlayer.setVolume&&window.__ytPlayer.setVolume(Math.round(v*100))},
  next:function(){if(!window.__ytQueue.length)return;window.__ytQueueIdx=(window.__ytQueueIdx+1)%window.__ytQueue.length;var id=window.__ytQueue[window.__ytQueueIdx];if(id)window.__ytPlayer.loadVideoById(id);post('queueIdx',{index:window.__ytQueueIdx})},
  prev:function(){if(!window.__ytQueue.length)return;window.__ytQueueIdx=Math.max(0,window.__ytQueueIdx-1);var id=window.__ytQueue[window.__ytQueueIdx];if(id)window.__ytPlayer.loadVideoById(id);post('queueIdx',{index:window.__ytQueueIdx})}
};
</script></body></html>"""

// ─── Stream extraction JS (port of EXTRACT_JS from YoutubeStreamExtractor.tsx)
private fun makeExtractJs(videoId: String): String {
    val v = JSONObject.quote(videoId)
    return """(function(){var _v=$v,_t=0,_i=setInterval(function(){_t++;var pr=window.ytInitialPlayerResponse;if(pr&&pr.streamingData){clearInterval(_i);var af=(pr.streamingData.adaptiveFormats||[]).filter(function(f){var m=(f.mimeType||f.type||'').toLowerCase();return m.indexOf('audio/')===0&&!!f.url}).sort(function(a,b){return(b.bitrate||b.averageBitrate||0)-(a.bitrate||a.averageBitrate||0)});var url='';if(af.length){url=af[0].url}else{var fs=(pr.streamingData.formats||[]).filter(function(f){return!!f.url});if(fs.length)url=fs[0].url}window.AndroidBridge.onStreamResult(url);return}if(pr&&pr.playabilityStatus&&pr.playabilityStatus.status!=='OK'){clearInterval(_i);window.AndroidBridge.onStreamError(pr.playabilityStatus.reason||pr.playabilityStatus.status||'unavailable');return}if(_t>=30){clearInterval(_i);window.AndroidBridge.onStreamError('timeout')}},500)})();true;"""
}

// ─── IFrame Player Service ────────────────────────────────────────────────────
/**
 * Singleton that controls the hidden YoutubeIFramePlayerHost via evaluateJavascript.
 * Mirrors YoutubePlayerService.ts — same API: load, play, pause, seek, etc.
 */
object YoutubePlayerService {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile internal var webView: WebView? = null

    var onReady:    (() -> Unit)?                   = null
    var onState:    ((Int) -> Unit)?                = null
    var onProgress: ((Double, Double) -> Unit)?     = null
    var onError:    ((Int) -> Unit)?                = null

    private fun inject(expr: String) {
        val wv = webView ?: return
        mainHandler.post { wv.evaluateJavascript("(function(){$expr})();true;", null) }
    }

    fun load(videoId: String)  = inject("window.__ytCmd&&window.__ytCmd.load(${JSONObject.quote(videoId)})")
    fun loadQueue(ids: List<String>, idx: Int = 0) {
        val arr = ids.joinToString(",") { JSONObject.quote(it) }
        inject("window.__ytCmd&&window.__ytCmd.loadQueue([$arr],$idx)")
    }
    fun play()                 = inject("window.__ytCmd&&window.__ytCmd.play()")
    fun pause()                = inject("window.__ytCmd&&window.__ytCmd.pause()")
    fun stop()                 = inject("window.__ytCmd&&window.__ytCmd.stop()")
    fun seek(sec: Double)      = inject("window.__ytCmd&&window.__ytCmd.seek($sec)")
    fun setVolume(vol: Double) = inject("window.__ytCmd&&window.__ytCmd.volume($vol)")
    fun next()                 = inject("window.__ytCmd&&window.__ytCmd.next()")
    fun prev()                 = inject("window.__ytCmd&&window.__ytCmd.prev()")

    internal fun handleMessage(json: String) {
        try {
            val m = JSONObject(json)
            when (m.getString("type")) {
                "ready"    -> mainHandler.post { onReady?.invoke() }
                "state"    -> mainHandler.post { onState?.invoke(m.getInt("state")) }
                "progress" -> mainHandler.post { onProgress?.invoke(m.getDouble("position"), m.getDouble("duration")) }
                "error"    -> mainHandler.post { onError?.invoke(m.optInt("code", -1)) }
            }
        } catch (_: Exception) {}
    }

    // YT player state constants (mirrors YT_STATE in YoutubePlayerService.ts)
    const val UNSTARTED = -1; const val ENDED = 0; const val PLAYING = 1
    const val PAUSED = 2; const val BUFFERING = 3; const val CUED = 5
}

// ─── Stream Extractor Singleton ───────────────────────────────────────────────
/**
 * Mirrors YoutubeStreamExtractor.tsx:
 *   1. Loads the real YouTube embed page in a hidden WebView so Chromium's BotGuard
 *      and PO-token run natively — same approach as Snaptube / the RN app.
 *   2. Injects JS that polls ytInitialPlayerResponse.streamingData.
 *   3. Returns the highest-bitrate audio-only URL.
 */
object YoutubeExtractor {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutex = Mutex()

    @Volatile private var pending: CompletableDeferred<String>? = null
    @Volatile internal var pendingVideoId: String? = null
    @Volatile internal var webView: WebView? = null

    val isConfigured: Boolean get() = webView != null

    suspend fun extractStreamUrl(videoId: String, timeoutMs: Long = 15_000): String? {
        val deferred = CompletableDeferred<String>()
        mutex.withLock { pending = deferred; pendingVideoId = videoId }
        mainHandler.post {
            webView?.loadUrl(
                "https://www.youtube.com/embed/$videoId?autoplay=0&enablejsapi=1&origin=https://www.youtube.com"
            )
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (_: Exception) {
            mutex.withLock { pending = null; pendingVideoId = null }
            null
        }
    }

    // Called via @JavascriptInterface from the extractor WebView
    @JavascriptInterface
    fun onStreamResult(url: String) {
        val d = pending; pending = null; pendingVideoId = null
        if (url.isNotBlank()) d?.complete(url)
        else d?.completeExceptionally(Exception("empty_url"))
    }

    @JavascriptInterface
    fun onStreamError(error: String) {
        val d = pending; pending = null; pendingVideoId = null
        d?.completeExceptionally(Exception(error))
    }
}

// ─── IFrame Player Composable ─────────────────────────────────────────────────
/**
 * Hidden 1×1 WebView that runs YouTube's IFrame API.
 * Mount ONCE in MainActivity — never unmounts so audio survives navigation.
 * Mirrors YoutubeWebPlayer.tsx from the React Native app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeIFramePlayerHost(modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { onDispose { YoutubePlayerService.webView = null } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false  // allow autoplay
                    userAgentString = CHROME_UA
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    allowFileAccess = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = false
                }
                val bridge = object {
                    @JavascriptInterface
                    fun onMessage(json: String) = YoutubePlayerService.handleMessage(json)
                }
                addJavascriptInterface(bridge, "AndroidBridge")
                YoutubePlayerService.webView = this
                loadDataWithBaseURL("https://www.youtube.com", YT_IFRAME_HTML, "text/html", "UTF-8", null)
            }
        },
        update = { YoutubePlayerService.webView = it },
    )
}

// ─── Stream Extractor Composable ──────────────────────────────────────────────
/**
 * Hidden 0×0 WebView that loads YouTube embed pages to extract stream URLs.
 * Mount ONCE in MainActivity alongside YoutubeIFramePlayerHost.
 * Mirrors YoutubeStreamExtractor.tsx from the React Native app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeStreamExtractorHost(modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { onDispose { YoutubeExtractor.webView = null } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = true  // extractor never plays audio
                    userAgentString = CHROME_UA
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                // Third-party cookies needed for YouTube BotGuard / PO token
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                // YoutubeExtractor itself is the bridge (has @JavascriptInterface methods)
                addJavascriptInterface(YoutubeExtractor, "AndroidBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = false
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        val vid = YoutubeExtractor.pendingVideoId ?: return
                        if (url.contains("youtube.com/embed")) {
                            view.evaluateJavascript(makeExtractJs(vid), null)
                        }
                    }
                    override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                        super.onReceivedError(v, req, err)
                        if (req.isForMainFrame) YoutubeExtractor.onStreamError("page_load_error")
                    }
                }
                YoutubeExtractor.webView = this
            }
        },
        update = { YoutubeExtractor.webView = it },
    )
}

// ─── Safe programmatic initializer (outside Compose) ──────────────────────────
/**
 * Initializes both YouTube WebViews in a hidden 0×0 [FrameLayout] added via
 * [android.app.Activity.addContentView]. This avoids Compose compositing
 * bugs caused by negative-offset [AndroidView] mounts.
 *
 * Call from [androidx.activity.ComponentActivity.onCreate] **before** [setContent].
 * Invoke the returned cleanup lambda from [onDestroy].
 */
@SuppressLint("SetJavaScriptEnabled")
fun initHiddenYoutubeWebViews(activity: ComponentActivity): () -> Unit {
    val container = android.widget.FrameLayout(activity).apply {
        visibility = android.view.View.GONE
    }

    // IFrame player
    val playerWv = WebView(activity).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = CHROME_UA
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.allowFileAccess = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = false
        }
        val bridge = object {
            @JavascriptInterface
            fun onMessage(json: String) = YoutubePlayerService.handleMessage(json)
        }
        addJavascriptInterface(bridge, "AndroidBridge")
        YoutubePlayerService.webView = this
        loadDataWithBaseURL("https://www.youtube.com", YT_IFRAME_HTML, "text/html", "UTF-8", null)
    }
    container.addView(playerWv, android.view.ViewGroup.LayoutParams(1, 1))

    // Stream extractor
    val extractWv = WebView(activity).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.userAgentString = CHROME_UA
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(YoutubeExtractor, "AndroidBridge")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = false
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val vid = YoutubeExtractor.pendingVideoId ?: return
                if (url.contains("youtube.com/embed")) {
                    view.evaluateJavascript(makeExtractJs(vid), null)
                }
            }
            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                super.onReceivedError(v, req, err)
                if (req.isForMainFrame) YoutubeExtractor.onStreamError("page_load_error")
            }
        }
        YoutubeExtractor.webView = this
    }
    container.addView(extractWv, android.view.ViewGroup.LayoutParams(1, 1))

    activity.addContentView(container, android.view.ViewGroup.LayoutParams(0, 0))

    return {
        container.removeAllViews()
        YoutubePlayerService.webView = null
        YoutubeExtractor.webView = null
    }
}
