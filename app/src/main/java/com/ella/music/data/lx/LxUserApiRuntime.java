package com.ella.music.data.lx;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.ella.music.data.model.Song;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.net.URLDecoder;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class LxUserApiRuntime implements AutoCloseable {
    private static final String TAG = "LxUserApiRuntime";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0";

    private final Context context;
    private final OkHttpClient client;
    private final String key = UUID.randomUUID().toString();
    private QuickJSContext jsContext;
    private JSONObject initInfo;
    private JSONObject requestResponse;
    private String lastError;
    private final PriorityQueue<PendingTimeout> pendingTimeouts =
            new PriorityQueue<>(Comparator.comparingLong(timeout -> timeout.runAtMs));

    public LxUserApiRuntime(Context context, OkHttpClient client) {
        this.context = context.getApplicationContext();
        this.client = client;
    }

    public JSONObject load(String script, String id, String name, String url) throws Exception {
        QuickJSLoader.init();
        jsContext = QuickJSContext.create();
        jsContext.setConsole(new QuickJSContext.Console() {
            @Override
            public void log(String message) {
                Log.d(TAG, message);
            }

            @Override
            public void info(String message) {
                Log.i(TAG, message);
            }

            @Override
            public void warn(String message) {
                Log.w(TAG, message);
            }

            @Override
            public void error(String message) {
                Log.e(TAG, message);
            }
        });
        createEnv();
        jsContext.evaluate(readPreloadScript());
        jsContext.getGlobalObject().getJSFunction("lx_setup").call(
                key,
                id == null ? "" : id,
                name == null || name.isEmpty() ? "LX源" : name,
                url == null ? "" : url,
                "",
                "",
                url == null ? "" : url,
                script
        );
        jsContext.evaluate(script);
        waitFor(() -> initInfo != null, 8_000L);
        if (initInfo == null) {
            throw new IllegalStateException(lastError != null ? lastError : "源未调用 lx.send(EVENT_NAMES.inited)");
        }
        if (!initInfo.optBoolean("status")) {
            throw new IllegalStateException(initInfo.optString("errorMessage", "源初始化失败"));
        }
        return initInfo.optJSONObject("info");
    }

    public String requestMusicUrl(LxOnlineSong item, String script, String sourceName) throws Exception {
        JSONObject info = load(script, sourceName, sourceName, "");
        JSONObject sources = info == null ? null : info.optJSONObject("sources");
        JSONObject source = sources == null ? null : sources.optJSONObject(item.getSource());
        if (source == null) {
            throw new IllegalStateException("当前源不支持 " + item.getSource());
        }
        if (!contains(source.optJSONArray("actions"), "musicUrl")) {
            throw new IllegalStateException("当前源不支持播放地址解析");
        }
        String quality = bestQuality(source.optJSONArray("qualitys"), item.getQuality());
        requestResponse = null;
        lastError = null;

        JSONObject request = new JSONObject()
                .put("requestKey", "ella_" + System.nanoTime())
                .put("data", new JSONObject()
                        .put("source", item.getSource())
                        .put("action", "musicUrl")
                        .put("info", new JSONObject()
                                .put("type", quality)
                                .put("musicInfo", buildMusicInfo(item.getSong(), item.getSource(), item.getSongmid(), quality))));

        callJs("request", request.toString());
        waitFor(() -> requestResponse != null || lastError != null, 25_000L);
        if (requestResponse == null) {
            throw new IllegalStateException(lastError != null ? lastError : "源没有返回播放地址");
        }
        if (!requestResponse.optBoolean("status")) {
            throw new IllegalStateException(requestResponse.optString("errorMessage", "源解析失败"));
        }
        JSONObject result = requestResponse.optJSONObject("result");
        JSONObject data = result == null ? null : result.optJSONObject("data");
        String url = data == null ? "" : data.optString("url");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalStateException("源返回的播放地址无效");
        }
        return url;
    }

    private JSONObject buildMusicInfo(Song song, String source, String songmid, String quality) throws Exception {
        String interval = formatDuration(song.getDuration());
        JSONObject qualityInfo = new JSONObject().put("type", quality).put("size", JSONObject.NULL);
        JSONArray qualitys = new JSONArray().put(qualityInfo);
        JSONObject qualityMap = new JSONObject().put(quality, new JSONObject().put("size", JSONObject.NULL));
        JSONObject meta = new JSONObject()
                .put("songId", songmid)
                .put("albumName", song.getAlbum())
                .put("picUrl", song.getCoverUrl().isEmpty() ? JSONObject.NULL : song.getCoverUrl())
                .put("qualitys", qualitys)
                .put("_qualitys", qualityMap);

        return new JSONObject()
                .put("id", source + "_" + songmid)
                .put("name", song.getTitle())
                .put("singer", song.getArtist())
                .put("source", source)
                .put("songmid", songmid)
                .put("albumName", song.getAlbum())
                .put("interval", interval)
                .put("types", qualitys)
                .put("_types", qualityMap)
                .put("typeUrl", new JSONObject())
                .put("meta", meta);
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void createEnv() {
        jsContext.getGlobalObject().setProperty("__lx_native_call__", args -> {
            if (key.equals(String.valueOf(args[0]))) {
                callNative(String.valueOf(args[1]), String.valueOf(args[2]));
            }
            return null;
        });
        jsContext.getGlobalObject().setProperty("__lx_native_call__set_timeout", args -> {
            long delayMs = Math.max(0L, Long.parseLong(String.valueOf(args[1])));
            pendingTimeouts.add(new PendingTimeout(String.valueOf(args[0]), System.currentTimeMillis() + delayMs));
            return null;
        });
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_str2b64", args ->
                Base64.encodeToString(String.valueOf(args[0]).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_b642buf", args -> {
            byte[] bytes = Base64.decode(String.valueOf(args[0]), Base64.NO_WRAP);
            JSONArray array = new JSONArray();
            for (byte b : bytes) array.put((int) b);
            return array.toString();
        });
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_str2md5", args ->
                safeString(() -> md5(String.valueOf(args[0]))));
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_aes_encrypt", args ->
                safeString(() -> aesEncrypt(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]), String.valueOf(args[3]))));
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_rsa_encrypt", args ->
                safeString(() -> rsaEncrypt(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]))));
    }

    private void callNative(String action, String data) {
        try {
            switch (action) {
                case "init":
                    initInfo = new JSONObject(data);
                    break;
                case "request":
                    handleScriptHttpRequest(new JSONObject(data));
                    break;
                case "response":
                    requestResponse = new JSONObject(data);
                    break;
                case "showUpdateAlert":
                case "cancelRequest":
                    break;
                default:
                    Log.d(TAG, "Unknown script action: " + action);
                    break;
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            Log.w(TAG, "Script action failed: " + action, e);
        }
    }

    private void handleScriptHttpRequest(JSONObject data) throws Exception {
        String requestKey = data.optString("requestKey");
        String url = data.optString("url");
        JSONObject options = data.optJSONObject("options");
        if (options == null) options = new JSONObject();

        Request.Builder builder = new Request.Builder().url(url);
        JSONObject headers = options.optJSONObject("headers");
        if (headers != null) {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String header = keys.next();
                String value = headers.optString(header);
                if (!value.isEmpty()) builder.header(header, value);
            }
        }
        if (headerValue(headers, "User-Agent").isEmpty()) {
            builder.header("User-Agent", USER_AGENT);
        }

        String method = options.optString("method", "get").toUpperCase(Locale.US);
        RequestBody body = buildRequestBody(options);
        if ("GET".equals(method)) builder.get();
        else builder.method(method, body != null ? body : RequestBody.create(new byte[0]));

        JSONObject responseData = new JSONObject().put("requestKey", requestKey);
        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            JSONObject headersJson = new JSONObject();
            for (String name : response.headers().names()) {
                headersJson.put(name, response.header(name));
            }
            Object bodyValue = response.body() == null ? "" : response.body().string();
            if (!options.optBoolean("binary")) {
                String bodyString = String.valueOf(bodyValue);
                try {
                    bodyValue = bodyString.startsWith("[") ? new JSONArray(bodyString) : new JSONObject(bodyString);
                } catch (Exception ignored) {
                    bodyValue = bodyString;
                }
            }
            responseData
                    .put("error", JSONObject.NULL)
                    .put("response", new JSONObject()
                            .put("statusCode", response.code())
                            .put("statusMessage", response.message())
                            .put("headers", headersJson)
                            .put("body", bodyValue));
        } catch (Exception e) {
            responseData.put("error", e.getMessage()).put("response", JSONObject.NULL);
        }
        callJs("response", responseData.toString());
    }

    private RequestBody buildRequestBody(JSONObject options) {
        if (options.has("form")) {
            JSONObject form = options.optJSONObject("form");
            FormBody.Builder builder = new FormBody.Builder();
            if (form != null) {
                Iterator<String> keys = form.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    builder.add(key, form.optString(key));
                }
            }
            return builder.build();
        }
        if (options.has("body")) {
            Object body = options.opt("body");
            if (body == null || body == JSONObject.NULL) return null;
            String contentType = headerValue(options.optJSONObject("headers"), "Content-Type");
            if (contentType.isEmpty()) contentType = "application/json";
            return RequestBody.create(String.valueOf(body), MediaType.parse(contentType));
        }
        if (options.has("formData")) {
            Object body = options.opt("formData");
            if (body == null || body == JSONObject.NULL) return null;
            String contentType = headerValue(options.optJSONObject("headers"), "Content-Type");
            if (contentType.isEmpty()) contentType = "multipart/form-data";
            return RequestBody.create(String.valueOf(body), MediaType.parse(contentType));
        }
        return null;
    }

    private String headerValue(JSONObject headers, String name) {
        if (headers == null) return "";
        Iterator<String> keys = headers.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (name.equalsIgnoreCase(key)) return headers.optString(key);
        }
        return "";
    }

    private Object callJs(String action, String data) {
        return jsContext.getGlobalObject().getJSFunction("__lx_native__").call(key, action, data);
    }

    private void waitFor(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            if (!runNextDueTimeout(deadline)) {
                Thread.sleep(Math.min(8L, Math.max(1L, deadline - System.currentTimeMillis())));
            }
        }
    }

    private boolean runNextDueTimeout(long deadline) throws Exception {
        PendingTimeout timeout = pendingTimeouts.peek();
        if (timeout == null) return false;
        long now = System.currentTimeMillis();
        long waitMs = Math.min(timeout.runAtMs, deadline) - now;
        if (waitMs > 0L) Thread.sleep(waitMs);
        if (System.currentTimeMillis() < timeout.runAtMs) return false;
        pendingTimeouts.poll();
        callJs("__set_timeout__", timeout.id);
        return true;
    }

    private String readPreloadScript() throws Exception {
        try (InputStream input = context.getAssets().open("script/user-api-preload.js")) {
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }

    private boolean contains(JSONArray array, String value) {
        if (array == null) return false;
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) return true;
        }
        return false;
    }

    private String bestQuality(JSONArray available, String requested) {
        if (contains(available, requested)) return requested;
        String[] preference = new String[]{"flac24bit", "flac", "320k", "128k"};
        for (String quality : preference) {
            if (contains(available, quality)) return quality;
        }
        return requested;
    }

    private String md5(String input) throws Exception {
        String decoded = URLDecoder.decode(input, "UTF-8");
        byte[] digest = MessageDigest.getInstance("MD5").digest(decoded.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : digest) builder.append(String.format(Locale.US, "%02x", b));
        return builder.toString();
    }

    private interface ThrowingStringSupplier {
        String get() throws Exception;
    }

    private String safeString(ThrowingStringSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            Log.w(TAG, "Native util failed", e);
            return "";
        }
    }

    private String aesEncrypt(String dataB64, String keyB64, String ivB64, String transformation) throws Exception {
        byte[] data = Base64.decode(dataB64, Base64.NO_WRAP);
        byte[] keyBytes = Base64.decode(keyB64, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(transformation.replace("PKCS7Padding", "PKCS5Padding"));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        if (transformation.contains("/CBC/")) {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(Base64.decode(ivB64, Base64.NO_WRAP)));
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        }
        return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP);
    }

    private String rsaEncrypt(String dataB64, String publicKeyB64, String transformation) throws Exception {
        byte[] keyBytes = Base64.decode(publicKeyB64, Base64.NO_WRAP);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, new SecureRandom());
        return Base64.encodeToString(cipher.doFinal(Base64.decode(dataB64, Base64.NO_WRAP)), Base64.NO_WRAP);
    }

    @Override
    public void close() {
        pendingTimeouts.clear();
        if (jsContext != null) {
            jsContext.destroy();
            jsContext = null;
        }
    }

    private static final class PendingTimeout {
        final String id;
        final long runAtMs;

        PendingTimeout(String id, long runAtMs) {
            this.id = id;
            this.runAtMs = runAtMs;
        }
    }
}
