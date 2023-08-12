package com.github.catvod.utils;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class Github {

    public static final int TIMEOUT = 5 * 1000;
    public static final String A = "https://raw.githubusercontent.com/";
    //public static final String B = "https://fongmi.cachefly.net/";
    public static final String C = "https://ghproxy.com/";
    public static final String M = "hzcoolwind/TV";

    private final OkHttpClient client;
    private String proxy;

    private static class Loader {
        static volatile Github INSTANCE = new Github();
    }

    private static Github get() {
        return Loader.INSTANCE;
    }

    private Github() {
        client = OkHttp.client(TIMEOUT);
        check(A);
        //check(B);
        check(C);
    }

    private void check(String url) {
        try {
            if (getProxy().length() > 0) return;
            Response response = OkHttp.newCall(client, url).execute();
            if (response.code() == 200) setProxy(url);
        } catch (IOException ignored) {
        }
    }

    private void setProxy(String url) {
        this.proxy = url.equals(C) ? url + A + M : url + M;
    }

    private String getProxy() {
        return TextUtils.isEmpty(proxy) ? "" : proxy;
    }

    private static String getUrl(String path, String name) {
        return get().getProxy() + "/" + path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        return getUrl((dev ? "dev" : "dev") + "/myrelease", name + ".json");
    }

    public static String getApk(boolean dev, String name) {
        return getUrl((dev ? "dev" : "dev") + "/myrelease", name + ".apk");
    }

    public static String getSo(String name) {
        try {
            File file = Path.so(name);
            if (file.length() < 300) Path.write(file, OkHttp.newCall(getUrl("so", file.getName())).execute().body().bytes());
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }
}
