package com.fongmi.android.tv.player;

import android.net.Uri;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Utils;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.player.MediaSource;

public class IjkUtil {

    public static MediaSource getSource(Result result) {
        return getSource(result.getHeaders(), result.getRealUrl(), result.getSubs());
    }

    public static MediaSource getSource(Map<String, String> headers, String url, List<Sub> subs) {
        Uri uri = Uri.parse(url.trim().replace("\\", ""));
        if (Sniffer.isAds(uri)) uri = Uri.parse(Server.get().getAddress().concat("/m3u8?url=").concat(URLEncoder.encode(url)));
        List<com.avery.subtitle.model.Sub> ijksubs = new ArrayList<com.avery.subtitle.model.Sub>();
        for (Sub sub:subs) {
            ijksubs.add(new com.avery.subtitle.model.Sub(sub.getUrl(), sub.getName(), sub.getLang(), sub.getFormat()));
        }
        return new MediaSource(Utils.checkHeaders(headers), uri, ijksubs);
    }
}
