package com.fongmi.android.tv.player;

import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.ui.CaptionStyleCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Prefers;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Utils;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.Call;

public class ExoUtil {

    private static HttpDataSource.Factory httpDataSourceFactory;
    private static DataSource.Factory dataSourceFactory;
    private static ExtractorsFactory extractorsFactory;
    private static DatabaseProvider database;
    private static Cache cache;

    public static LoadControl buildLoadControl() {
        return new DefaultLoadControl();
    }

    public static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("zh").setTunnelingEnabled(Prefers.isTunnel()));
        return trackSelector;
    }

    public static RenderersFactory buildRenderersFactory() {
        return new DefaultRenderersFactory(App.get()).setExtensionRendererMode(Math.abs(Prefers.getDecode() - 2));
    }

    public static CaptionStyleCompat getCaptionStyle() {
        return new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    public static boolean haveTrack(Tracks tracks, int type) {
        int count = 0;
        for (Tracks.Group trackGroup : tracks.getGroups()) if (trackGroup.getType() == type) count += trackGroup.length;
        return count > 0;
    }

    public static void selectTrack(ExoPlayer player, int group, int track) {
        List<Integer> trackIndices = new ArrayList<>();
        selectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static void deselectTrack(ExoPlayer player, int group, int track) {
        List<Integer> trackIndices = new ArrayList<>();
        deselectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static MediaSource getSource(Result result, int errorCode) {
        return getSource(result.getHeaders(), result.getRealUrl(), result.getSubs(), errorCode);
    }

    public static MediaSource getSource(Map<String, String> headers, String url, int errorCode) {
        return getSource(headers, url, Collections.emptyList(), errorCode);
    }

    private static MediaSource getSource(Map<String, String> headers, String url, List<Sub> subs, int errorCode) {
        Uri uri = Uri.parse(url.trim().replace("\\", ""));
        if (uri.getUserInfo() != null) headers.put(HttpHeaders.AUTHORIZATION, "Basic " + Base64.encodeToString(uri.getUserInfo().getBytes(), Base64.NO_WRAP));
        return new DefaultMediaSourceFactory(getDataSourceFactory(headers), getExtractorsFactory()).createMediaSource(getMediaItem(uri, subs, errorCode));
    }

    private static MediaItem getMediaItem(Uri uri, List<Sub> subs, int errorCode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) builder.setMimeType(MimeTypes.BASE_TYPE_APPLICATION+"/octet-stream");
        if (subs.size() > 0) builder.setSubtitleConfigurations(getSubtitles(subs));
        //builder.setAllowChunklessPreparation(Players.isHard());
        //builder.setAds(Sniffer.getAdsRegex(uri));
        return builder.build();
    }

    private static List<MediaItem.SubtitleConfiguration> getSubtitles(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> items = new ArrayList<>();
        for (Sub sub : subs) items.add(sub.getExo());
        return items;
    }

    private static void selectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i == track || trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void deselectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i != track && trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void setTrackParameters(ExoPlayer player, int group, List<Integer> trackIndices) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(player.getCurrentTracks().getGroups().get(group).getMediaTrackGroup(), trackIndices)).build());
    }

    private static synchronized ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3);
        return extractorsFactory;
    }

    private static synchronized HttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = Prefers.getHttp() == 0 ? new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true) : new OkHttpDataSource.Factory((Call.Factory) OkHttp.client());
        return httpDataSourceFactory;
    }

    private static synchronized DataSource.Factory getDataSourceFactory(Map<String, String> headers) {
        if (dataSourceFactory == null) dataSourceFactory = buildReadOnlyCacheDataSource(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory()), getCache());
        httpDataSourceFactory.setDefaultRequestProperties(Utils.checkHeaders(headers));
        return dataSourceFactory;
    }

    private static CacheDataSource.Factory buildReadOnlyCacheDataSource(DataSource.Factory upstreamFactory, Cache cache) {
        return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstreamFactory).setCacheWriteDataSinkFactory(null).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private static synchronized DatabaseProvider getDatabase() {
        if (database == null) database = new StandaloneDatabaseProvider(App.get());
        return database;
    }

    private static synchronized Cache getCache() {
        if (cache == null) cache = new SimpleCache(FileUtil.getCacheDir("player"), new NoOpCacheEvictor(), getDatabase());
        return cache;
    }

    public static void reset() {
        if (cache != null) cache.release();
        httpDataSourceFactory = null;
        dataSourceFactory = null;
        extractorsFactory = null;
        database = null;
        cache = null;
    }
}
