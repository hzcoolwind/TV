package tv.danmaku.ijk.media.player;

import android.net.Uri;

import androidx.media3.common.MediaItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MediaSource {
    private final Map<String, String> headers;
    private final Uri uri;
    private List<MediaItem.SubtitleConfiguration> subs=null;

    public MediaSource(Map<String, String> headers, Uri uri) {
        this.headers = headers;
        this.uri = uri;
        this.subs = new ArrayList<>();
    }

    public MediaSource(Map<String, String> headers, Uri uri, List<MediaItem.SubtitleConfiguration> subs) {
        this.headers = headers;
        this.uri = uri;
        this.subs = subs;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Uri getUri() {
        return uri;
    }

    public List<MediaItem.SubtitleConfiguration> getSubs() { return subs; }
}
