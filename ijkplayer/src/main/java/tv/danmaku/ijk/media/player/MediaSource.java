package tv.danmaku.ijk.media.player;

import android.net.Uri;

import com.avery.subtitle.model.Sub;

import java.util.List;
import java.util.Map;

public class MediaSource {

    private final Map<String, String> headers;
    private final Uri uri;
    private List<Sub> subs;

    public MediaSource(Map<String, String> headers, Uri uri) {
        this.headers = headers;
        this.uri = uri;
    }

    public MediaSource(Map<String, String> headers, Uri uri, List<Sub> subs) {
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

    public List<Sub> getSubs() { return subs;}
}
