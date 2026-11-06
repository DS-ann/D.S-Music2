package com.dsann.music.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.view.Gravity;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.AppWidgetTarget;
import com.dsann.music.R;
import com.dsann.music.model.SongItem;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base Widget Provider for D.S Music.
 * Handles common playback control intents and updates all 3 widget sizes.
 */
public class DsmusicWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "DsmusicWidget";
    // Intent Actions from AudioPlaybackService (must match exactly)
    private static final String ACTION_PLAY = "com.dsann.music.ACTION_PLAY";
    private static final String ACTION_PAUSE = "com.dsann.music.ACTION_PAUSE";
    private static final String ACTION_PREVIOUS = "com.dsann.music.ACTION_PREVIOUS";
    private static final String ACTION_NEXT = "com.dsann.music.ACTION_NEXT";
    private static final String ACTION_TOGGLE_LYRICS = "com.dsann.music.ACTION_TOGGLE_LYRICS";
    
    private static final String PREFS_NAME = "dsmusic_widget_prefs";
    private static final String PREF_LYRICS_MODE = "lyrics_mode_";

    /**
     * Carries widget lyrics-page choices to the current preference file.
     * The previous file is located by its own widget setting keys.
     */
    private static void migrateWidgetPreferences(Context context) {
        SharedPreferences target = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (target.getBoolean("_dsmusic_storage_migrated", false)) return;

        File directory = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String filename = file.getName();
                if (!filename.endsWith(".xml") || filename.equals(PREFS_NAME + ".xml")) continue;

                String sourceName = filename.substring(0, filename.length() - 4);
                SharedPreferences source = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
                SharedPreferences.Editor destination = target.edit();
                SharedPreferences.Editor cleanup = source.edit();
                boolean copied = false;

                for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (!key.startsWith(PREF_LYRICS_MODE) || !(value instanceof Boolean)) continue;
                    if (!target.contains(key)) destination.putBoolean(key, (Boolean) value);
                    cleanup.remove(key);
                    copied = true;
                }

                if (copied && destination.commit()) {
                    cleanup.commit();
                    if (source.getAll().isEmpty()) file.delete();
                }
            }
        }
        target.edit().putBoolean("_dsmusic_storage_migrated", true).apply();
    }

    // Keep RemoteViews/AppWidgetManager Binder work away from the activity's main looper.
    // A single worker also preserves update order across rapid playback/lyrics callbacks.
    private static final ExecutorService PARTIAL_WIDGET_UPDATE_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread worker = new Thread(runnable, "DsmusicWidgetUpdates");
                worker.setPriority(Thread.MIN_PRIORITY);
                return worker;
            });
    // Progress ticks arrive once per second. Keep only the newest pending snapshot so a
    // slow AppWidgetManager/Binder call cannot build an unbounded queue of stale positions.
    private static final AtomicReference<WidgetProgressUpdate> PENDING_PARTIAL_WIDGET_UPDATE =
            new AtomicReference<>();
    private static final AtomicBoolean PARTIAL_WIDGET_UPDATE_SCHEDULED = new AtomicBoolean(false);

    private static final class WidgetProgressUpdate {
        final Context context;
        final int position;
        final int duration;
        final CharSequence currentLyric;

        WidgetProgressUpdate(Context context, int position, int duration, CharSequence currentLyric) {
            this.context = context;
            this.position = position;
            this.duration = duration;
            this.currentLyric = currentLyric;
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        migrateWidgetPreferences(context);
        int layoutId = R.layout.widget_medium;
        Class<?> providerClass = WidgetMediumProvider.class;
        if (this instanceof WidgetSmallProvider) {
            layoutId = R.layout.widget_small;
            providerClass = WidgetSmallProvider.class;
        } else if (this instanceof WidgetLargeProvider) {
            layoutId = R.layout.widget_large;
            providerClass = WidgetLargeProvider.class;
        }
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null, false, 0, 0, null, R.drawable.ic_repeat, layoutId, providerClass);
        }

        // onUpdate has no access to the current playback item itself. Ask the running playback
        // service to replace these initial placeholders with the real track/position immediately;
        // otherwise widgets added while a song is already playing can remain apparently blank.
        try {
            Intent refreshIntent = new Intent(context, com.dsann.music.service.AudioPlaybackService.class);
            refreshIntent.setAction("com.dsann.music.ACTION_WIDGET_REFRESH_STATE");
            context.startService(refreshIntent);
        } catch (Exception e) {
            Log.w(TAG, "Could not refresh widget state after adding a widget", e);
        }
    }

    /** Returns whether any D.S Music widget is installed on the current launcher. */
    public static boolean hasAnyWidgets(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
            return manager.getAppWidgetIds(
                    new ComponentName(context, WidgetSmallProvider.class)).length > 0
                    || manager.getAppWidgetIds(
                    new ComponentName(context, WidgetMediumProvider.class)).length > 0
                    || manager.getAppWidgetIds(
                    new ComponentName(context, WidgetLargeProvider.class)).length > 0;
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not query installed widgets", error);
            // Preserve the playback service if launcher state cannot be queried safely.
            return true;
        }
    }

    public static void updateAllWidgets(Context context, SongItem currentSong, boolean isPlaying, int position,
            int duration, CharSequence currentLyric) {
        updateAllWidgets(context, currentSong, isPlaying, position, duration, currentLyric, R.drawable.ic_repeat);
    }

    public static void updateAllWidgets(Context context, SongItem currentSong, boolean isPlaying, int position,
            int duration, CharSequence currentLyric, int repeatIconResId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        new Handler(Looper.getMainLooper()).post(() -> {
            // Update Small Widgets
            int[] smallIds = manager.getAppWidgetIds(new ComponentName(context, WidgetSmallProvider.class));
            for (int id : smallIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, repeatIconResId, R.layout.widget_small, WidgetSmallProvider.class);
            }

            // Update Medium Widgets
            int[] mediumIds = manager.getAppWidgetIds(new ComponentName(context, WidgetMediumProvider.class));
            for (int id : mediumIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, repeatIconResId, R.layout.widget_medium, WidgetMediumProvider.class);
            }

            // Update Large Widgets
            int[] largeIds = manager.getAppWidgetIds(new ComponentName(context, WidgetLargeProvider.class));
            for (int id : largeIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, repeatIconResId, R.layout.widget_large, WidgetLargeProvider.class);
            }
        });
    }

    /** True only while at least one installed medium widget is displaying its lyrics page. */
    public static boolean hasLyricsModeWidget(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
            int[] ids = manager.getAppWidgetIds(
                    new ComponentName(context, WidgetMediumProvider.class));
            SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            for (int id : ids) {
                if (preferences.getBoolean(PREF_LYRICS_MODE + id, false)) return true;
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not inspect lyrics widget state", error);
        }
        return false;
    }

    private static void applyLyricsPresentation(Context context, RemoteViews views, CharSequence lyrics) {
        views.setTextViewText(R.id.widget_lyrics_text, lyrics != null ? lyrics : "...");
        SharedPreferences appearance = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        String size = appearance.getString("lyrics_text_size", "Standard");
        float textSize = "Small".equals(size) ? 16.5f : "Large".equals(size) ? 21.5f : 19f;
        views.setTextViewTextSize(
                R.id.widget_lyrics_text, TypedValue.COMPLEX_UNIT_SP, textSize);
        String alignment = appearance.getString("lyrics_alignment", "Original");
        int horizontalGravity = "Center".equals(alignment) ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        views.setInt(
                R.id.widget_lyrics_text, "setGravity",
                horizontalGravity | Gravity.CENTER_VERTICAL);
    }

    public static void updateAllWidgetsPartial(Context context, int position, int duration, CharSequence currentLyric) {
        final Context appContext = context.getApplicationContext();
        PENDING_PARTIAL_WIDGET_UPDATE.set(
                new WidgetProgressUpdate(appContext, position, duration, currentLyric));
        schedulePartialWidgetUpdateDrain();
    }

    /**
     * Re-apply the transport controls after the device wakes. Some launchers defer a
     * RemoteViews update while keyguard is closing; this small partial update restores
     * current play/pause PendingIntents without reloading or blurring artwork.
     */
    public static void refreshAllWidgetControls(Context context, boolean isPlaying) {
        final Context appContext = context.getApplicationContext();
        try {
            PARTIAL_WIDGET_UPDATE_EXECUTOR.execute(() -> {
                AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
                refreshWidgetControls(
                        appContext, manager, WidgetSmallProvider.class,
                        R.layout.widget_small, isPlaying);
                refreshWidgetControls(
                        appContext, manager, WidgetMediumProvider.class,
                        R.layout.widget_medium, isPlaying);
                refreshWidgetControls(
                        appContext, manager, WidgetLargeProvider.class,
                        R.layout.widget_large, isPlaying);
            });
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not schedule widget control refresh", error);
        }
    }

    private static void refreshWidgetControls(
            Context context,
            AppWidgetManager manager,
            Class<?> providerClass,
            int layoutId,
            boolean isPlaying
    ) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, providerClass));
        for (int id : ids) {
            try {
                RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
                applyWidgetActionIcons(
                        views,
                        isPlaying,
                        layoutId,
                        0
                );
                setupPendingIntents(context, views, isPlaying, layoutId, providerClass);
                manager.partiallyUpdateAppWidget(id, views);
            } catch (Exception error) {
                Log.w(TAG, "Could not refresh widget controls for " + id, error);
            }
        }
    }

    private static void schedulePartialWidgetUpdateDrain() {
        if (!PARTIAL_WIDGET_UPDATE_SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        try {
            PARTIAL_WIDGET_UPDATE_EXECUTOR.execute(() -> {
                while (true) {
                    WidgetProgressUpdate update = PENDING_PARTIAL_WIDGET_UPDATE.getAndSet(null);
                    if (update != null) {
                        applyPartialWidgetProgress(update);
                    }

                    PARTIAL_WIDGET_UPDATE_SCHEDULED.set(false);
                    // If another tick arrived during the Binder update, process only its newest
                    // snapshot. If a producer already scheduled a worker, let that worker run.
                    if (PENDING_PARTIAL_WIDGET_UPDATE.get() == null ||
                            !PARTIAL_WIDGET_UPDATE_SCHEDULED.compareAndSet(false, true)) {
                        return;
                    }
                }
            });
        } catch (RuntimeException error) {
            PARTIAL_WIDGET_UPDATE_SCHEDULED.set(false);
            Log.w(TAG, "Could not schedule widget progress update", error);
        }
    }

    private static void applyPartialWidgetProgress(WidgetProgressUpdate update) {
        Context appContext = update.context;
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        try {
            int[] smallIds = manager.getAppWidgetIds(
                    new ComponentName(appContext, WidgetSmallProvider.class));
            int[] mediumIds = manager.getAppWidgetIds(
                    new ComponentName(appContext, WidgetMediumProvider.class));
            int[] largeIds = manager.getAppWidgetIds(
                    new ComponentName(appContext, WidgetLargeProvider.class));
            if (smallIds.length == 0 && mediumIds.length == 0 && largeIds.length == 0) {
                return;
            }

            for (int id : smallIds) {
                updateWidgetProgress(
                        appContext, manager, id, update.position, update.duration,
                        null, R.layout.widget_small);
            }
            for (int id : mediumIds) {
                updateWidgetProgress(
                        appContext, manager, id, update.position, update.duration,
                        update.currentLyric, R.layout.widget_medium);
            }
            for (int id : largeIds) {
                updateWidgetProgress(
                        appContext, manager, id, update.position, update.duration,
                        null, R.layout.widget_large);
            }
        } catch (Exception error) {
            Log.w(TAG, "Widget progress update failed", error);
        }
    }

    private static void updateWidgetProgress(
            Context context,
            AppWidgetManager manager,
            int id,
            int position,
            int duration,
            CharSequence currentLyric,
            int layoutId
    ) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
            int progress = duration > 0
                    ? (int) (((float) Math.max(0, position) / duration) * 100f)
                    : 0;
            views.setProgressBar(R.id.widget_progress, 100, Math.max(0, Math.min(100, progress)), false);
            if (layoutId == R.layout.widget_medium) {
                applyLyricsPresentation(context, views, currentLyric);
            }
            manager.partiallyUpdateAppWidget(id, views);
        } catch (Exception e) {
            Log.e(TAG, "Error in partial widget update: " + e.getMessage());
        }
    }

    private static void setWidgetIcon(RemoteViews views, int viewId, int drawableId) {
        views.setImageViewResource(viewId, drawableId);
        views.setInt(viewId, "setColorFilter", Color.WHITE);
    }

    private static void applyWidgetActionIcons(
            RemoteViews views,
            boolean isPlaying,
            int layoutId,
            int repeatIconResId
    ) {
        // Set drawable resources explicitly instead of relying on the launcher's
        // interpretation of XML ImageButton defaults or inherited theme tints.
        setWidgetIcon(views, R.id.widget_play_pause,
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

        if (layoutId == R.layout.widget_small ||
                layoutId == R.layout.widget_medium ||
                layoutId == R.layout.widget_large) {
            setWidgetIcon(views, R.id.widget_prev, R.drawable.ic_skip_previous);
            setWidgetIcon(views, R.id.widget_next, R.drawable.ic_skip_next);
        }

        if (layoutId == R.layout.widget_medium) {
            setWidgetIcon(views, R.id.widget_toggle_lyrics, R.drawable.ic_lyrics);
        } else if (layoutId == R.layout.widget_large) {
            setWidgetIcon(views, R.id.widget_favorite, R.drawable.ic_favorite_border);
            if (repeatIconResId != 0) {
                setWidgetIcon(views, R.id.widget_repeat, repeatIconResId);
            }
        }
    }

    private static void updateWidget(
            Context context,
            AppWidgetManager manager,
            int id,
            SongItem song,
            boolean isPlaying,
            int position,
            int duration,
            CharSequence currentLyric,
            int repeatIconResId,
            int layoutId,
            Class<?> providerClass
    ) {
        try {
            RemoteViews views = hideSkeleton(context, layoutId);

            if (layoutId == R.layout.widget_small ||
                    layoutId == R.layout.widget_medium ||
                    layoutId == R.layout.widget_large) {
                views.setTextViewText(
                        R.id.widget_song_title,
                        song != null && song.title != null ? song.title : "D.S Music"
                );
                views.setTextViewText(
                        R.id.widget_artist_name,
                        song != null && song.artist != null ? song.artist : "Select a song"
                );
            }

            int progress = duration > 0
                    ? (int) (((float) Math.max(0, position) / duration) * 100f)
                    : 0;
            views.setProgressBar(R.id.widget_progress, 100, Math.max(0, Math.min(100, progress)), false);

            if (layoutId == R.layout.widget_medium) {
                applyLyricsPresentation(context, views, currentLyric);
                boolean isLyricsMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_LYRICS_MODE + id, false);
                views.setDisplayedChild(R.id.widget_flipper, isLyricsMode ? 1 : 0);
            }

            // Apply icon resources, tint, and click actions on every full refresh,
            // including the initial empty-state refresh before a song is selected.
            applyWidgetActionIcons(views, isPlaying, layoutId, repeatIconResId);
            setupPendingIntents(context, views, isPlaying, layoutId, providerClass);
            manager.updateAppWidget(id, views);

            if (song != null) {
                Uri artworkUri = null;
                try {
                    artworkUri = song.getAlbumArtUri();
                } catch (Exception artworkError) {
                    Log.w(TAG, "Could not resolve widget artwork for " + song.title, artworkError);
                }
                if (artworkUri != null) {
                    try {
                        loadAlbumArt(context, views, artworkUri, id);
                    } catch (Exception artworkError) {
                        Log.w(TAG, "Could not load widget album art for " + song.title, artworkError);
                    }
                    try {
                        loadBlurredAura(context, views, artworkUri, id);
                    } catch (Exception artworkError) {
                        Log.w(TAG, "Could not load widget aura for " + song.title, artworkError);
                    }
                }
            }
        } catch (Exception error) {
            Log.e(TAG, "Failed to update widget " + id, error);
            try {
                RemoteViews fallback = new RemoteViews(context.getPackageName(), layoutId);
                if (layoutId == R.layout.widget_small ||
                        layoutId == R.layout.widget_medium ||
                        layoutId == R.layout.widget_large) {
                    fallback.setTextViewText(
                            R.id.widget_song_title,
                            song != null && song.title != null ? song.title : "D.S Music"
                    );
                    fallback.setTextViewText(
                            R.id.widget_artist_name,
                            song != null && song.artist != null ? song.artist : "Select a song"
                    );
                }
                int progress = duration > 0
                        ? (int) (((float) Math.max(0, position) / duration) * 100f)
                        : 0;
                fallback.setProgressBar(R.id.widget_progress, 100, Math.max(0, Math.min(100, progress)), false);
                if (layoutId == R.layout.widget_medium) {
                    applyLyricsPresentation(context, fallback, currentLyric);
                    boolean isLyricsMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .getBoolean(PREF_LYRICS_MODE + id, false);
                    fallback.setDisplayedChild(R.id.widget_flipper, isLyricsMode ? 1 : 0);
                }
                applyWidgetActionIcons(fallback, isPlaying, layoutId, repeatIconResId);
                setupPendingIntents(context, fallback, isPlaying, layoutId, providerClass);
                manager.updateAppWidget(id, fallback);
            } catch (Exception fallbackError) {
                Log.e(TAG, "Widget fallback rendering failed for " + id, fallbackError);
            }
        }
    }

    public static String findCurrentLyricLine(java.util.List<com.dsann.music.data.lyrics.LyricLine> lyrics,
            int position) {
        if (lyrics == null || lyrics.isEmpty()) return null;

        // Providers do not all return timestamps in ascending order. The full-screen
        // player sorts its display list; use the same order-independent lookup here.
        com.dsann.music.data.lyrics.LyricLine current = null;
        com.dsann.music.data.lyrics.LyricLine first = null;
        long currentStartMs = Long.MIN_VALUE;
        long firstStartMs = Long.MAX_VALUE;
        for (com.dsann.music.data.lyrics.LyricLine line : lyrics) {
            if (line == null || line.isInterlude || line.content == null || line.content.trim().isEmpty()) {
                continue;
            }
            long lineStartMs = line.startTimeMs;
            if (line.words != null && !line.words.isEmpty()) {
                for (com.dsann.music.data.lyrics.LyricWord word : line.words) {
                    lineStartMs = Math.min(lineStartMs, word.getAbsoluteStartTimeMs());
                }
            }
            if (lineStartMs < firstStartMs) {
                first = line;
                firstStartMs = lineStartMs;
            }
            if (lineStartMs <= position && lineStartMs > currentStartMs) {
                current = line;
                currentStartMs = lineStartMs;
            }
        }
        if (current != null) return current.content;
        return first != null ? first.content : null;
    }

    private static RemoteViews hideSkeleton(Context context, int layoutId) {
        return new RemoteViews(context.getPackageName(), layoutId);
    }

    private static void loadAlbumArt(Context context, RemoteViews views, Uri artUri, int widgetId) {
        AppWidgetTarget target = new AppWidgetTarget(context, R.id.widget_album_art, views, widgetId);

        Glide.with(context.getApplicationContext())
                .asBitmap()
                .load(artUri)
                .placeholder(R.drawable.default_album_art)
                .error(R.drawable.default_album_art)
                .centerCrop()
                .into(target);
    }

    private static void loadBlurredAura(Context context, RemoteViews views, Uri artUri, int widgetId) {
        AppWidgetTarget auraTarget = new AppWidgetTarget(context, R.id.widget_aura, views, widgetId);

        Glide.with(context.getApplicationContext())
                .asBitmap()
                .load(artUri)
                .centerCrop()
                .override(150, 150) // Scale down for performance
                .transform(new com.bumptech.glide.load.resource.bitmap.BitmapTransformation() {
                    @Override
                    protected Bitmap transform(@NonNull com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool pool,
                            @NonNull Bitmap toTransform, int outWidth, int outHeight) {
                        // Cache a vivid artwork-derived accent for the widget's UI-Matching
                        // lyrics mode before blurring the image for the background.
                        int lyricAccent = sampleWidgetAccent(toTransform);
                        context.getApplicationContext()
                                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit().putInt("widget_lyrics_dynamic_accent", lyricAccent).apply();

                        // 1. Zoom in (2x)
                        int zoomWidth = toTransform.getWidth() / 2;
                        int zoomHeight = toTransform.getHeight() / 2;
                        Bitmap zoomed = Bitmap.createBitmap(toTransform,
                                zoomWidth / 2, zoomHeight / 2, zoomWidth, zoomHeight);

                        // 2. Multi-pass deep blur
                        return blur(zoomed);
                    }

                    @Override
                    public void updateDiskCacheKey(@NonNull java.security.MessageDigest messageDigest) {
                    }
                })
                .into(auraTarget);
    }

    private static int sampleWidgetAccent(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return Color.rgb(124, 156, 255);
        }
        long red = 0L, green = 0L, blue = 0L, count = 0L;
        int stepX = Math.max(1, bitmap.getWidth() / 24);
        int stepY = Math.max(1, bitmap.getHeight() / 24);
        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                red += Color.red(pixel);
                green += Color.green(pixel);
                blue += Color.blue(pixel);
                count++;
            }
        }
        if (count == 0L) return Color.rgb(124, 156, 255);
        int average = Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
        float[] hsv = new float[3];
        Color.colorToHSV(average, hsv);
        hsv[1] = Math.max(0.46f, Math.min(0.86f, hsv[1]));
        hsv[2] = Math.max(0.78f, Math.min(0.94f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    private static Bitmap blur(Bitmap image) {
        if (null == image)
            return null;

        Bitmap outputBitmap = Bitmap.createBitmap(image);
        // Simple fast box blur for widgets (RenderScript is deprecated and sometimes
        // unstable in background)
        return fastblur(outputBitmap);
    }

    private static Bitmap fastblur(Bitmap sentBitmap) {
        int width = Math.round(sentBitmap.getWidth() * (float) 1.0);
        int height = Math.round(sentBitmap.getHeight() * (float) 1.0);
        sentBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false);

        Bitmap bitmap = sentBitmap.copy(Objects.requireNonNull(sentBitmap.getConfig()), true);

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = 25 + 25 + 1;

        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] temp = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            temp[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = 25 + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -25; i <= 25; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + 25];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = 25;

            for (x = 0; x < w; x++) {

                r[yi] = temp[rsum];
                g[yi] = temp[gsum];
                b[yi] = temp[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - 25 + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + 25 + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -25 * w;
            for (i = -25; i <= 25; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + 25];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = 25;
            for (y = 0; y < h; y++) {
                // Preserve alpha or set fully opaque? Background aura should probably be opaque
                // but alpha is handled by layout
                pix[yi] = (0xff000000 & pix[yi]) | (temp[rsum] << 16) | (temp[gsum] << 8) | temp[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - 25 + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + 25 + 1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);

        return (bitmap);
    }

    private static void setupPendingIntents(Context context, RemoteViews views, boolean isPlaying, int layoutId, Class<?> providerClass) {
        // App Launch Intent (On Art)
        Intent launchIntent = new Intent(context, com.dsann.music.MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piLaunch = PendingIntent.getActivity(context, 0, launchIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        views.setOnClickPendingIntent(R.id.widget_album_art, piLaunch);
        
        // Lyrics Toggle (Medium Widget only)
        if (layoutId == R.layout.widget_medium) {
            Intent toggleIntent = new Intent(context, providerClass);
            toggleIntent.setAction(ACTION_TOGGLE_LYRICS);
            PendingIntent piToggle = PendingIntent.getBroadcast(context, 20, toggleIntent, 
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.widget_toggle_lyrics, piToggle);
            views.setOnClickPendingIntent(R.id.widget_lyrics_text, piToggle);
        }
        
        // Playback Controls
        PendingIntent piPlayPause = getServicePendingIntent(context, isPlaying ? ACTION_PAUSE : ACTION_PLAY, 10);
        views.setOnClickPendingIntent(R.id.widget_play_pause, piPlayPause);
        
        if (layoutId == R.layout.widget_small ||
                layoutId == R.layout.widget_medium ||
                layoutId == R.layout.widget_large) {
            PendingIntent piPrev = getServicePendingIntent(context, ACTION_PREVIOUS, 11);
            views.setOnClickPendingIntent(R.id.widget_prev, piPrev);

            PendingIntent piNext = getServicePendingIntent(context, ACTION_NEXT, 12);
            views.setOnClickPendingIntent(R.id.widget_next, piNext);
        }

        if (layoutId == R.layout.widget_large) {
            // Favorite Button Intent
            PendingIntent piFav = getServicePendingIntent(context, "com.dsann.music.ACTION_TOGGLE_FAV", 13);
            views.setOnClickPendingIntent(R.id.widget_favorite, piFav);

            // Repeat/Loop Button Intent
            PendingIntent piLoop = getServicePendingIntent(context, "com.dsann.music.ACTION_TOGGLE_LOOP", 14);
            views.setOnClickPendingIntent(R.id.widget_repeat, piLoop);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        migrateWidgetPreferences(context);
        super.onReceive(context, intent);
        if (ACTION_TOGGLE_LYRICS.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, WidgetMediumProvider.class));
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            for (int id : ids) {
                // Toggle the saved state
                boolean currentMode = prefs.getBoolean(PREF_LYRICS_MODE + id, false);
                prefs.edit().putBoolean(PREF_LYRICS_MODE + id, !currentMode).apply();
                
                // Toggle the saved page without replacing the current RemoteViews layout.
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_medium);
                views.setDisplayedChild(R.id.widget_flipper, !currentMode ? 1 : 0);
                manager.partiallyUpdateAppWidget(id, views);
            }

            // The old implementation only switched the ViewFlipper page. It never fetched
            // lyrics, so a newly opened lyrics page could remain "..." forever.
            try {
                Intent lyricsIntent = new Intent(context, com.dsann.music.service.AudioPlaybackService.class);
                lyricsIntent.setAction("com.dsann.music.ACTION_WIDGET_FETCH_LYRICS");
                context.startService(lyricsIntent);
            } catch (Exception e) {
                Log.w(TAG, "Could not request lyrics for widget", e);
            }
        }
    }

    private static PendingIntent getServicePendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, com.dsann.music.service.AudioPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
