package com.fongmi.android.tv.player;

import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.ijk.IjkUtil;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Players implements Player.Listener, IMediaPlayer.Listener, ParseCallback {
    public static final int SYS = 0;
    public static final int IJK = 1;
    public static final int EXO = 2;

    private static final String TAG = Players.class.getSimpleName();

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private final StringBuilder builder;
    private final Formatter formatter;
    private final Runnable runnable;

    private Map<String, String> headers;
    private MediaSessionCompat session;
    private ExoPlayer exoPlayer = null;
    private IjkVideoView ijkPlayer = null;
    private ParseJob parseJob;
    private PlayerView view;
    private List<Sub> subs;
    private String format;
    private String url;
    private Drm drm;
    private Sub sub;

    private int decode;
    private int retry;

    private int playerId;

    public static Players create(Activity activity) {
        Players player = new Players(activity);
        Server.get().setPlayer(player);
        return player;
    }

    private Players(Activity activity) {
        playerId = Setting.getPlayer();
        decode = Setting.getDecode();
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        formatter = new Formatter(builder, Locale.getDefault());
        createSession(activity);
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 0, new Intent(App.get(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void init(PlayerView view) {
        releasePlayer();
        setPlayer(view);
        setMediaItem();
    }
    public void init(PlayerView view, IjkVideoView ijkView) {
        releasePlayer();
        releaseIjkPlayer();
        setPlayer(view);
        setupIjkPlayer(ijkView);
        setMediaItem();
    }

    public int getPlayerId() {
        return playerId;
    }
    public void setPlayerId(int playerId) {
        if (this.playerId != playerId) stop();
        this.playerId = playerId;
    }
    private void setPlayer(PlayerView view) {
        exoPlayer = new ExoPlayer.Builder(App.get()).setLoadControl(ExoUtil.buildLoadControl()).setTrackSelector(ExoUtil.buildTrackSelector()).setRenderersFactory(ExoUtil.buildRenderersFactory(isHard() ? EXTENSION_RENDERER_MODE_ON : EXTENSION_RENDERER_MODE_PREFER)).setMediaSourceFactory(ExoUtil.buildMediaSourceFactory()).build();
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true);
        exoPlayer.addAnalyticsListener(new EventLogger());
        exoPlayer.setHandleAudioBecomingNoisy(true);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
        this.view = view;
    }

    private void setupIjkPlayer(IjkVideoView view) {
        ijkPlayer = view.render(Setting.getRender());
        ijkPlayer.addListener(this);
        ijkPlayer.setPlayer(playerId);
    }

    public boolean isExo() {
        return playerId == EXO;
    }

    public boolean isIjkOrSys() {
        return playerId == SYS || playerId == IJK;
    }

    public void togglePlayer() {
        stop();
        setPlayerId(isExo() ? SYS : ++playerId);
    }

    public void nextPlayer() {
        stop();
        setPlayerId(isExo() ? IJK : EXO);
    }

    public ExoPlayer get() {
        return exoPlayer;
    }
    public IjkVideoView getIjkPlayer() { return ijkPlayer; }

    public MediaSessionCompat getSession() {
        return session;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setSub(Sub sub) {
        this.sub = sub;
        if (isIjkOrSys()) return;
        setMediaItem();
    }

    public void setFormat(String format) {
        this.format = format;
        setMediaItem();
    }

    public void reset() {
        removeTimeoutCheck();
        retry = 0;
    }

    public void clear() {
        headers = null;
        format = null;
        subs = null;
        drm = null;
        url = null;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public int getVideoWidth() {
        return isExo() ? exoPlayer.getVideoSize().width : ijkPlayer.getVideoWidth();
    }

    public int getVideoHeight() {
        return isExo() ? exoPlayer.getVideoSize().height : ijkPlayer.getVideoHeight();
    }

    public float getSpeed() {
        return isExo() ? exoPlayer.getPlaybackParameters().speed : ijkPlayer.getSpeed();
    }

    public long getPosition() {
        return isExo() ? exoPlayer.getCurrentPosition() : ijkPlayer.getCurrentPosition();
    }

    public long getDuration() {
        return isExo() ? exoPlayer.getDuration() : ijkPlayer.getDuration();
    }

    public long getBuffered() {
        return isExo() ? exoPlayer.getBufferedPosition() : ijkPlayer.getBufferedPosition();
    }

    public boolean retried() {
        return ++retry > 2;
    }

    public boolean canAdjustSpeed() {
        return !Setting.isTunnel();
    }

    public boolean haveTrack(int type) {
        return isExo() ? ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), type): ijkPlayer.haveTrack(type);
    }

    public boolean isPlaying() {
        return isExo() ? exoPlayer != null && exoPlayer.isPlaying() : ijkPlayer != null && ijkPlayer.isPlaying();
    }

    public boolean isEnded() {
        return isExo() ? exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_ENDED :
                         ijkPlayer != null && ijkPlayer.getPlaybackState() == Player.STATE_ENDED;
    }

    public boolean isIdle() {
        return isExo() ? exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_IDLE :
                         ijkPlayer != null && ijkPlayer.getPlaybackState() == Player.STATE_IDLE;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        return getDuration() < 3 * 60 * 1000 || exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isVod() {
        return getDuration() > 3 * 60 * 1000 && !exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isHard() {
        return decode == HARD;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        return getVideoWidth() == 0 && getVideoHeight() == 0 ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        if (exoPlayer != null && !Setting.isTunnel()) exoPlayer.setPlaybackSpeed(speed);
        if (ijkPlayer != null && !Setting.isTunnel()) ijkPlayer.setSpeed(speed);
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.25f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? Setting.getSpeed() : 1;
        return setSpeed(speed);
    }

    public void toggleDecode() {
        decode = isHard() ? SOFT : HARD;
        Setting.putDecode(decode);
        init(view);
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        if (isExo() && exoPlayer != null) exoPlayer.seekTo(time);
        else if (isIjkOrSys() && ijkPlayer != null) ijkPlayer.seekTo(getPosition() + time);
    }

    public void prepare() {
        if (exoPlayer != null) exoPlayer.prepare();
    }

    public void play() {
        if (isExo()) if (exoPlayer != null) exoPlayer.play();
        else if (isIjkOrSys()) if (ijkPlayer != null) ijkPlayer.start();
    }

    public void pause() {
        if (isExo()) if (exoPlayer != null) exoPlayer.pause();
        else if (isIjkOrSys()) if (ijkPlayer != null) ijkPlayer.pause();
    }

    public void stop() {
        if (isExo()) if (exoPlayer != null) exoPlayer.stop();
        if (isIjkOrSys()) if (ijkPlayer != null) ijkPlayer.stop();
        stopParse();
    }

    public void release() {
        stopParse();
        releasePlayer();
        releaseIjkPlayer();
        session.release();
        removeTimeoutCheck();
        Server.get().setPlayer(null);
        App.execute(() -> Source.get().stop());
    }

    private void releasePlayer() {
        if (exoPlayer != null) exoPlayer.release();
        if (view != null) view.setPlayer(null);
        exoPlayer = null;
    }

    private void releaseIjkPlayer() {
        if (ijkPlayer == null) return;
        ijkPlayer.release();
        ijkPlayer = null;
    }

    public void start(Channel channel, int timeout) {
        if (channel.hasMsg()) {
            ErrorEvent.extract(channel.getMsg());
        } else if (channel.getParse() == 1) {
            startParse(channel.result(), false);
        } else if (isIllegal(channel.getUrl())) {
            ErrorEvent.url();
        } else if (channel.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(channel.getDrm().getUUID())) {
            ErrorEvent.drm();
        } else {
            setMediaItem(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        if (result.hasMsg()) {
            ErrorEvent.extract(result.getMsg());
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (isIllegal(result.getRealUrl())) {
            ErrorEvent.url();
        } else if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            ErrorEvent.drm();
        } else {
            setMediaItem(result, timeout);
        }
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public static Map<String, String> checkUa(Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(header.getKey())) return headers;
        headers.put(HttpHeaders.USER_AGENT, Setting.getUa().isEmpty() ? ExoUtil.getUa() : Setting.getUa());
        return headers;
    }

    private List<Sub> checkSub(List<Sub> subs) {
        if (sub == null || subs.contains(sub)) return subs;
        subs.add(0, sub);
        return subs;
    }

    private void setMediaItem() {
        if (url != null) setMediaItem(headers, url, format, drm, subs, Constant.TIMEOUT_PLAY);
    }

    public void setMediaItem(String url) {
        setMediaItem(new HashMap<>(), url);
    }

    private void setMediaItem(Map<String, String> headers, String url) {
        setMediaItem(headers, url, null, null, new ArrayList<>(), Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(Channel channel, int timeout) {
        setMediaItem(channel.getHeaders(), channel.getUrl(), channel.getFormat(), channel.getDrm(), new ArrayList<>(), timeout);
    }

    private void setMediaItem(Result result, int timeout) {
        setMediaItem(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), timeout);
    }

    private void setMediaItem(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout) {
        if (isExo()) if (exoPlayer != null) exoPlayer.setMediaItem(ExoUtil.getMediaItem(this.headers = checkUa(headers), UrlUtil.uri(this.url = url), this.format = format, this.drm = drm, checkSub(this.subs = subs), decode));
        if (isIjkOrSys()) if (ijkPlayer != null) ijkPlayer.setMediaSource(IjkUtil.getSource(this.headers = checkUa(headers), UrlUtil.uri(this.url = url), checkSub(this.subs = subs)));

        App.post(runnable, timeout);
        session.setActive(true);
        PlayerEvent.prepare();
        Logger.t(TAG).d(url);
        prepare();
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    public void resetTrack() {
        if (isExo()) if (exoPlayer != null) ExoUtil.resetTrack(exoPlayer);
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    private void setTrack(Track item) {
        if (isExo()) setTrackExo(item);
        if (isIjkOrSys()) setTrackIjk(item);
    }
    private void setTrackExo(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setTrackIjk(Track item) {
        if (item.isSelected()) {
            ijkPlayer.selectTrack(item.getType(), item.getTrack());
            ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ijkPlayer.deselectTrack(item.getType(), item.getTrack());
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setPlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, getPosition(), getSpeed()).build());
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        if ("data".equals(scheme)) return false;
        return scheme.isEmpty() || "file".equals(scheme) ? !Path.exists(url) : host.isEmpty();
    }

    private MediaMetadataCompat.Builder putBitmap(MediaMetadataCompat.Builder builder, Drawable drawable) {
        try {
            return builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, ((BitmapDrawable) drawable).getBitmap());
        } catch (Exception ignored) {
            return builder;
        }
    }

    public void setMetadata(String title, String artist, String artUri, Drawable drawable) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        session.setMetadata(putBitmap(builder, drawable).build());
        ActionEvent.update();
    }

    public void share(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Bundle bundle = new Bundle();
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) bundle.putString(entry.getKey(), entry.getValue());
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, getUrl());
            intent.putExtra("extra_headers", bundle);
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void choose(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            List<String> list = new ArrayList<>();
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) list.addAll(Arrays.asList(entry.getKey(), entry.getValue()));
            Uri data = getUrl().startsWith("file://") || getUrl().startsWith("/") ? FileUtil.getShareUri(getUrl()) : Uri.parse(getUrl());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(data, "video/*");
            intent.putExtra("title", title);
            intent.putExtra("return_result", isVod());
            intent.putExtra("headers", list.toArray(new String[0]));
            if (isVod()) intent.putExtra("position", (int) getPosition());
            activity.startActivityForResult(Util.getChooser(intent), 1001);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkData(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) seekTo(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        setMediaItem(headers, url);
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (!events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_PLAYER_ERROR)) return;
        switch (player.getPlaybackState()) {
            case Player.STATE_IDLE:
                setPlaybackState(events.contains(Player.EVENT_PLAYER_ERROR) ? PlaybackStateCompat.STATE_ERROR : PlaybackStateCompat.STATE_NONE);
                break;
            case Player.STATE_READY:
                setPlaybackState(player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                break;
            case Player.STATE_BUFFERING:
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                break;
            case Player.STATE_ENDED:
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
        }
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        PlayerEvent.state(state);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        PlayerEvent.size();
    }

    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
        if (isHard() && ExoUtil.shouldSoftDecode(tracks)) {
            ErrorEvent.url(PlaybackException.ERROR_CODE_DECODING_FAILED);
        } else if (!tracks.isEmpty()) {
            PlayerEvent.track();
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Logger.t(TAG).e(error.errorCode + "," + url);
        ErrorEvent.url(error.errorCode);
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                PlayerEvent.state(Player.STATE_BUFFERING);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
            case IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START:
                PlayerEvent.state(Player.STATE_READY);
                break;
        }
    }
    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        setPlaybackState(PlaybackStateCompat.STATE_ERROR);
        ErrorEvent.url(1);
        return true;
    }
    @Override
    public void onPrepared(IMediaPlayer mp) {
        PlayerEvent.state(Player.STATE_READY);
    }
    @Override
    public void onCompletion(IMediaPlayer mp) {
        PlayerEvent.state(Player.STATE_ENDED);
    }
}
