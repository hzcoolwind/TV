package com.fongmi.android.tv.player.ijk;

import android.content.res.Resources;
import android.text.TextUtils;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.ui.R;

import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import com.fongmi.android.tv.App;

import java.util.Locale;

public class IjkTrackNameProvider {

    private final Resources resources;

    public IjkTrackNameProvider() {
        this.resources = App.get().getResources();
    }

    public String getTrackName(ITrackInfo trackInfo) {
        String trackName;
        int trackType = trackInfo.getTrackType();
        if (trackType == C.TRACK_TYPE_VIDEO) {
            trackName = joinWithSeparator(buildResolutionString(trackInfo.getWidth(), trackInfo.getHeight()), buildBitrateString(trackInfo.getBitrate()));
        } else if (trackType == C.TRACK_TYPE_AUDIO) {
            trackName = joinWithSeparator(buildLanguageString(trackInfo.getLanguage()), buildAudioChannelString(trackInfo.getChannelCount()), buildBitrateString(trackInfo.getBitrate()));
        } else {
            trackName = buildLanguageString(trackInfo.getLanguage());
        }
        return TextUtils.isEmpty(trackName) ? resources.getString(R.string.exo_track_unknown) : trackName;
    }

    private String buildResolutionString(int width, int height) {
        return width == Format.NO_VALUE || height == Format.NO_VALUE ? "" : resources.getString(R.string.exo_track_resolution, width, height);
    }
    private String buildBitrateString(int bitrate) {
        return bitrate <= 0 ? "" : resources.getString(R.string.exo_track_bitrate, bitrate / 1000000f);
    }

    private String buildAudioChannelString(int channelCount) {
        if (channelCount < 1) return "";
        switch (channelCount) {
            case 1:
                return resources.getString(R.string.exo_track_mono);
            case 2:
                return resources.getString(R.string.exo_track_stereo);
            case 6:
            case 7:
                return resources.getString(R.string.exo_track_surround_5_point_1);
            case 8:
                return resources.getString(R.string.exo_track_surround_7_point_1);
            default:
                return resources.getString(R.string.exo_track_surround);
        }
    }

    private String buildLanguageString(String language) {
        if (TextUtils.isEmpty(language) || C.LANGUAGE_UNDETERMINED.equals(language)) return "";
        Locale languageLocale = Util.SDK_INT >= 21 ? Locale.forLanguageTag(language) : new Locale(language);
        Locale displayLocale = Util.getDefaultDisplayLocale();
        String languageName = languageLocale.getDisplayName(displayLocale);
        if (TextUtils.isEmpty(languageName)) return "";
        try {
            int firstCodePointLength = languageName.offsetByCodePoints(0, 1);
            return languageName.substring(0, firstCodePointLength).toUpperCase(displayLocale) + languageName.substring(firstCodePointLength);
        } catch (IndexOutOfBoundsException e) {
            return languageName;
        }
    }

    private String joinWithSeparator(String... items) {
        String itemList = "";
        for (String item : items) {
            if (!item.isEmpty()) {
                if (TextUtils.isEmpty(itemList)) {
                    itemList = item;
                } else {
                    itemList = resources.getString(R.string.exo_item_list, itemList, item);
                }
            }
        }
        return itemList;
    }
}
