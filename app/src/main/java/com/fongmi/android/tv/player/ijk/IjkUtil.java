package com.fongmi.android.tv.player.ijk;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.Dimension;
import androidx.media3.common.MediaItem;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.player.Players;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.player.MediaSource;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class IjkUtil {
    public static CaptionStyleCompat getCaptionStyle() {
        return Setting.isCaption() ? CaptionStyleCompat.createFromCaptionStyle(((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle()) : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }
    public static void setSubtitleView(IjkVideoView ijk) {
        ijk.getSubtitleView().setStyle(getCaptionStyle());
        ijk.getSubtitleView().setApplyEmbeddedFontSizes(false);
        ijk.getSubtitleView().setApplyEmbeddedStyles(!Setting.isCaption());
        if (Setting.getSubtitlePosition() != 0) ijk.getSubtitleView().setTranslationY(Setting.getSubtitlePosition());
        if (Setting.getSubtitleTextSize() != 0) ijk.getSubtitleView().setFixedTextSize(Dimension.SP, Setting.getSubtitleTextSize());
    }
    private static List<MediaItem.SubtitleConfiguration> getSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        for (Sub sub : subs) configs.add(sub.getConfig());
        return configs;
    }

    public static MediaSource getSource(Map<String, String> headers, Uri url) {
        return new MediaSource(Players.checkUa(headers), url);
    }
    public static MediaSource getSource(Map<String, String> headers, Uri url, List<Sub> subs) {
        return new MediaSource(Players.checkUa(headers), url, getSubtitleConfigs(subs));
    }
}