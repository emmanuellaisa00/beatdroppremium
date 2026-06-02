# Optional self-hosted resolver backend

BeatDrop's in-app resolver (Innertube + Piped + WebView + Invidious) handles
~95% of YouTube tracks. The remaining ~5% — heavily-monetized music, newly-
uploaded VEVO content, age-restricted videos — need a server-side
PO-Token generator to play reliably. That's the missing piece SnapTube has
(`api.snaptube.in`) and we don't ship with the app.

**You can close that gap by deploying your own tiny backend** and pasting its
URL into Settings → Streaming → Resolver backend. BeatDrop will use it as
Strategy 0 (ahead of all in-app strategies) when configured. Median latency
~200 ms when reachable. Empty field = disabled, behaves exactly like before.

## Protocol (intentionally trivial)

```
GET  {baseUrl}/resolve?id=<videoId>
200  application/json
     {
       "url":     "https://...googlevideo.com/videoplayback?...",
       "ua":      "<optional user-agent string>",        // optional
       "headers": { "Referer": "...", "Origin": "..." }  // optional
     }
404  → BeatDrop falls through to next in-app strategy
5xx  → BeatDrop falls through to next in-app strategy
```

That's the entire contract. As long as your endpoint returns JSON in this
shape, anything goes.

## Option A — Cloudflare Worker + yt-dlp Web Service (recommended)

Free tier: 100,000 requests/day. Setup time: ~10 minutes.

You'll deploy two things:
1. A public yt-dlp HTTP service somewhere (e.g. on Render's free tier).
2. A Cloudflare Worker that just proxies + shapes the response.

### Step 1: Deploy yt-dlp on Render

Create `render.yaml`:

```yaml
services:
  - type: web
    name: ytdlp-resolver
    env: docker
    plan: free
    dockerfilePath: ./Dockerfile
```

Create `Dockerfile`:

```dockerfile
FROM python:3.12-slim
RUN pip install --no-cache-dir yt-dlp flask bgutil-ytdlp-pot-provider
COPY app.py /app/app.py
WORKDIR /app
CMD ["python", "app.py"]
```

Create `app.py`:

```python
import json, subprocess, os
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route("/resolve")
def resolve():
    vid = request.args.get("id", "").strip()
    if len(vid) != 11:
        return jsonify(error="bad id"), 400
    try:
        # yt-dlp -j prints a single JSON manifest with the best audio URL.
        result = subprocess.run(
            ["yt-dlp", "-j", "-f", "bestaudio/best",
             "--no-warnings", "--no-playlist",
             "--extractor-args", "youtube:player_client=default,ios,android_vr",
             f"https://www.youtube.com/watch?v={vid}"],
            capture_output=True, text=True, timeout=15,
        )
        if result.returncode != 0:
            return jsonify(error=result.stderr[:200]), 502
        data = json.loads(result.stdout)
        return jsonify(
            url=data["url"],
            ua=data.get("http_headers", {}).get("User-Agent", ""),
            headers={k: v for k, v in data.get("http_headers", {}).items()
                     if k.lower() in ("referer", "origin")},
        )
    except subprocess.TimeoutExpired:
        return jsonify(error="timeout"), 504
    except Exception as e:
        return jsonify(error=str(e)), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 10000)))
```

Push to GitHub, connect Render → it builds + deploys automatically. You'll
get a URL like `https://ytdlp-resolver.onrender.com`.

### Step 2: (optional) Cloudflare Worker in front

If you want CDN-edge caching + DDoS protection, put a Worker in front:

```javascript
// worker.js
const UPSTREAM = "https://ytdlp-resolver.onrender.com";

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname !== "/resolve") return new Response("Not found", { status: 404 });
    const id = url.searchParams.get("id");
    if (!id) return new Response(JSON.stringify({ error: "no id" }), { status: 400 });

    // 30-minute edge cache per videoId.
    const cache = caches.default;
    const cacheKey = new Request(`https://cache/${id}`, request);
    let resp = await cache.match(cacheKey);
    if (resp) return resp;

    resp = await fetch(`${UPSTREAM}/resolve?id=${id}`);
    if (resp.ok) {
      resp = new Response(resp.body, resp);
      resp.headers.set("Cache-Control", "public, max-age=1800");
      await cache.put(cacheKey, resp.clone());
    }
    return resp;
  },
};
```

Deploy with `wrangler deploy`. You'll get a URL like
`https://beatdrop-resolver.<your-account>.workers.dev`.

### Step 3: Paste the URL into BeatDrop

Open BeatDrop → **Settings → Streaming → Resolver backend (optional)** →
paste either the Render URL or the Cloudflare Worker URL → tap **Save**.

That's it. From now on every YouTube tap goes through your backend first.
You'll see `✅ Backend resolved → ...` in the Debug Log.

## Option B — Use someone else's backend

If you don't want to deploy anything, you can paste any compatible URL. The
risk is that the operator can log which videos you play. Don't paste random
URLs from forums.

## Option C — Don't deploy anything

Leave the field blank. BeatDrop falls back to its built-in 5-strategy
resolver chain (Innertube + Piped + WebView + Piped exhaustive + Invidious),
which handles ~95% of tracks.
