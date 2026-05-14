package com.ella.music.data.musicfree;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class MusicFreePluginRuntime implements AutoCloseable {
    private static final String TAG = "MusicFreeRuntime";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0";

    private final OkHttpClient client;
    private QuickJSContext jsContext;
    private String callResult;

    public MusicFreePluginRuntime(Context context, OkHttpClient client) {
        this.client = client;
    }

    public JSONArray search(String script, String keyword, int page) throws Exception {
        load(script);
        callResult = null;
        jsContext.getGlobalObject().getJSFunction("__mf_call_search").call(keyword, page);
        waitFor(() -> callResult != null, 25_000L);
        JSONObject result = readResult("搜索失败");
        JSONObject value = result.optJSONObject("value");
        if (value == null) return new JSONArray();
        JSONArray data = value.optJSONArray("data");
        return data == null ? new JSONArray() : data;
    }

    public JSONObject getMediaSource(String script, String musicItemJson, String quality) throws Exception {
        load(script);
        callResult = null;
        jsContext.getGlobalObject().getJSFunction("__mf_call_media_source").call(musicItemJson, quality);
        waitFor(() -> callResult != null, 25_000L);
        JSONObject result = readResult("解析播放地址失败");
        JSONObject value = result.optJSONObject("value");
        return value == null ? new JSONObject() : value;
    }

    private void load(String script) throws Exception {
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
        jsContext.evaluate(prelude());
        jsContext.evaluate("(function(){\n" + script + "\n;globalThis.__mf_plugin=(module.exports&&module.exports.default)||module.exports||exports;})();");
        jsContext.evaluate(bridge());
    }

    private void createEnv() {
        jsContext.getGlobalObject().setProperty("__mf_native_result", args -> {
            callResult = String.valueOf(args[0]);
            return null;
        });
        jsContext.getGlobalObject().setProperty("__mf_native_http", args ->
                safeString(() -> executeHttp(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]))));
        jsContext.getGlobalObject().setProperty("__mf_native_hash", args ->
                safeString(() -> hash(String.valueOf(args[0]), String.valueOf(args[1]))));
        jsContext.getGlobalObject().setProperty("__mf_native_b64", args ->
                Base64.encodeToString(String.valueOf(args[0]).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
    }

    private JSONObject readResult(String fallback) throws Exception {
        if (callResult == null) throw new IllegalStateException(fallback);
        JSONObject result = new JSONObject(callResult);
        if (!result.optBoolean("ok")) {
            throw new IllegalStateException(result.optString("error", fallback));
        }
        return result;
    }

    private String executeHttp(String method, String rawUrl, String optionsJson) throws Exception {
        JSONObject options = optionsJson == null || optionsJson.isEmpty() ? new JSONObject() : new JSONObject(optionsJson);
        String url = appendParams(rawUrl, options.optJSONObject("params"));
        Request.Builder builder = new Request.Builder().url(url);

        JSONObject headers = options.optJSONObject("headers");
        if (headers != null) {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = headers.optString(key);
                if (!value.isEmpty()) builder.header(key, value);
            }
        }
        if (headers == null || !headers.has("User-Agent")) builder.header("User-Agent", USER_AGENT);

        String upperMethod = method.toUpperCase(Locale.US);
        RequestBody body = null;
        Object data = options.opt("data");
        if (data == null || data == JSONObject.NULL) data = options.opt("body");
        if (data != null && data != JSONObject.NULL) {
            String contentType = headers == null ? "application/json" : headers.optString("Content-Type", "application/json");
            body = RequestBody.create(String.valueOf(data), MediaType.parse(contentType));
        }
        if ("GET".equals(upperMethod)) builder.get();
        else builder.method(upperMethod, body != null ? body : RequestBody.create(new byte[0]));

        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            String bodyText = response.body() == null ? "" : response.body().string();
            Object dataValue = bodyText;
            try {
                String trimmed = bodyText.trim();
                dataValue = trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed);
            } catch (Exception ignored) {
            }
            JSONObject headersJson = new JSONObject();
            for (String name : response.headers().names()) {
                headersJson.put(name, response.header(name));
            }
            return new JSONObject()
                    .put("status", response.code())
                    .put("statusText", response.message())
                    .put("headers", headersJson)
                    .put("data", dataValue)
                    .toString();
        }
    }

    private String appendParams(String url, JSONObject params) throws Exception {
        if (params == null || params.length() == 0) return url;
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        Iterator<String> keys = params.keys();
        boolean first = true;
        while (keys.hasNext()) {
            String key = keys.next();
            if (!first) builder.append("&");
            first = false;
            builder
                    .append(URLEncoder.encode(key, "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(params.optString(key), "UTF-8"));
        }
        return builder.toString();
    }

    private String hash(String algorithm, String input) throws Exception {
        String normalized = algorithm.replace("-", "");
        MessageDigest digest = MessageDigest.getInstance(normalized);
        byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) builder.append(String.format(Locale.US, "%02x", b));
        return builder.toString();
    }

    private void waitFor(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(8L);
        }
    }

    private String safeString(ThrowingStringSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            Log.w(TAG, "Native call failed", e);
            return "";
        }
    }

    private interface ThrowingStringSupplier {
        String get() throws Exception;
    }

    private String prelude() {
        return ""
                + "var module={exports:{}}; var exports=module.exports; var global=globalThis;\n"
                + "function setTimeout(fn){ if (typeof fn==='function') fn(); return 0; }\n"
                + "function clearTimeout(){}\n"
                + "function __mf_parse(v){ try{return JSON.parse(v)}catch(e){return v} }\n"
                + "function __mf_http(method,url,options){ return __mf_parse(__mf_native_http(method,url,JSON.stringify(options||{}))); }\n"
                + "var axios=function(config){ config=config||{}; return Promise.resolve(__mf_http(config.method||'GET', config.url, config)); };\n"
                + "axios.get=function(url,config){ return Promise.resolve(__mf_http('GET',url,config||{})); };\n"
                + "axios.post=function(url,data,config){ config=config||{}; config.data=data; return Promise.resolve(__mf_http('POST',url,config)); };\n"
                + "var request=function(url,options){ return axios(Object.assign({}, options||{}, {url:url})); };\n"
                + "var CryptoJS={enc:{Utf8:{parse:function(v){return String(v)}},Hex:{},Base64:{stringify:function(v){return __mf_native_b64(String(v))}}},"
                + "MD5:function(v){return {toString:function(){return __mf_native_hash('MD5',String(v))}}},"
                + "SHA1:function(v){return {toString:function(){return __mf_native_hash('SHA-1',String(v))}}},"
                + "SHA256:function(v){return {toString:function(){return __mf_native_hash('SHA-256',String(v))}}}};\n"
                + "function require(name){ if(name==='axios')return axios; if(name==='crypto-js')return CryptoJS; if(name==='@react-native-async-storage/async-storage')return {}; throw new Error('暂不支持插件依赖: '+name); }\n";
    }

    private String bridge() {
        return ""
                + "function __mf_finish(ok,value,error){ __mf_native_result(JSON.stringify({ok:ok,value:value||null,error:error?String(error):''})); }\n"
                + "function __mf_call_search(query,page){ try{ var p=__mf_plugin&&__mf_plugin.search; if(!p) throw new Error('插件不支持搜索'); Promise.resolve(p.call(__mf_plugin,query,page,'music')).then(function(r){__mf_finish(true,r,null)},function(e){__mf_finish(false,null,e&&e.message||e)}); }catch(e){__mf_finish(false,null,e&&e.message||e)} }\n"
                + "function __mf_call_media_source(itemJson,quality){ try{ var item=JSON.parse(itemJson); if(item.url){__mf_finish(true,{url:item.url},null);return;} var p=__mf_plugin&&__mf_plugin.getMediaSource; if(!p) throw new Error('插件不支持播放地址解析'); Promise.resolve(p.call(__mf_plugin,item,quality||'standard')).then(function(r){__mf_finish(true,r,null)},function(e){__mf_finish(false,null,e&&e.message||e)}); }catch(e){__mf_finish(false,null,e&&e.message||e)} }\n";
    }

    @Override
    public void close() {
        if (jsContext != null) {
            jsContext.destroy();
            jsContext = null;
        }
    }
}
