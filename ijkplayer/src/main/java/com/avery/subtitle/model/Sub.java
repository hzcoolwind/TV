package com.avery.subtitle.model;

import android.text.TextUtils;

public class Sub {

    private String url;

    private String name;

    private String lang;

    private String format;

    public Sub(String url, String name, String lang, String format){
        this.url = url;
        this.name = name;
        this.lang = lang;
        this.format = format;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getLang() {
        return TextUtils.isEmpty(lang) ? "zh" : lang;
    }

    public String getFormat() {
        return TextUtils.isEmpty(format) ? "" : format;
    }

}
