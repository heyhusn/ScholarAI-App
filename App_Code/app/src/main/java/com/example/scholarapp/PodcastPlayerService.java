package com.example.scholarapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Foreground service that owns the TextToSpeech engine and podcast dialogue
 * state. Survives activity rotation and backgrounding. Publishes a persistent
 * Media-Style notification with Play/Pause, Prev, and Next action buttons.
 *
 * <p>The activity binds to this service via {@link LocalBinder} and registers a
 * {@link PlaybackCallback} to receive real-time UI update events.
 */
public class PodcastPlayerService extends Service {

    // ── Notification constants ─────────────────────────────────────────────
    public static final String CHANNEL_ID        = "podcast_playback_channel";
    public static final int    NOTIFICATION_ID   = 1001;

    public static final String ACTION_PLAY_PAUSE = "com.example.scholarapp.ACTION_PLAY_PAUSE";
    public static final String ACTION_PREV       = "com.example.scholarapp.ACTION_PREV";
    public static final String ACTION_NEXT       = "com.example.scholarapp.ACTION_NEXT";
    public static final String ACTION_STOP       = "com.example.scholarapp.ACTION_STOP";

    // ── Intent extras for starting the service ────────────────────────────
    public static final String EXTRA_SCRIPT       = "script";
    public static final String EXTRA_PAPER_TITLE  = "paperTitle";
    public static final String EXTRA_PAPER_AUTHOR = "paperAuthor";

    // ── Dialogue model ─────────────────────────────────────────────────────
    public static class DialogueLine {
        public String speaker; // "Alice" or "Bob"
        public String text;
    }

    // ── Playback state ─────────────────────────────────────────────────────
    private final List<DialogueLine> dialogueLines = new ArrayList<>();
    private int   currentLineIndex = 0;
    private boolean isPlaying      = false;
    private boolean isPaused       = false;  // true when paused mid-line
    private float   playbackSpeed  = 1.0f;
    private String  paperTitle     = "";
    private String  paperAuthor    = "";

    // ── Smooth-progress tracking ───────────────────────────────────────────
    // We simulate per-second elapsed time within the current line so the
    // progress bar moves smoothly instead of jumping line-by-line.
    private final android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long lineStartTimeMs  = 0;   // System.currentTimeMillis() when current line started
    private int  lineEstimatedMs  = 0;   // estimated duration for the current line (ms)
    private int  totalEstimatedMs = 0;   // total estimated duration for all lines (ms)
    private int  elapsedBeforeCurrent = 0; // sum of durations of lines before currentLineIndex

    // ── TTS ────────────────────────────────────────────────────────────────
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private Voice aliceVoice   = null;
    private Voice bobVoice     = null;

    // ── Binding ────────────────────────────────────────────────────────────
    private final IBinder binder = new LocalBinder();
    private PlaybackCallback callback;

    // ── Notification receiver ─────────────────────────────────────────────
    private NotificationActionReceiver notifReceiver;
    private boolean receiverRegistered = false;

    // ──────────────────────────────────────────────────────────────────────
    //  Callback interface (activity implements this)
    // ──────────────────────────────────────────────────────────────────────
    public interface PlaybackCallback {
        void onLineChanged(int lineIndex, DialogueLine line);
        void onPlayStateChanged(boolean playing);
        void onFinished();
        void onTtsReady();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Binder — returned to the activity
    // ──────────────────────────────────────────────────────────────────────
    public class LocalBinder extends Binder {
        public PodcastPlayerService getService() {
            return PodcastPlayerService.this;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Service lifecycle
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerNotificationReceiver();
        initTextToSpeech();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopPlayback();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }

            // Load data passed via startService intent
            String rawScript = intent.getStringExtra(EXTRA_SCRIPT);
            String title     = intent.getStringExtra(EXTRA_PAPER_TITLE);
            String author    = intent.getStringExtra(EXTRA_PAPER_AUTHOR);
            if (title  != null) paperTitle  = title;
            if (author != null) paperAuthor = author;
            if (rawScript != null && !rawScript.trim().isEmpty()) {
                parseDialogue(rawScript);
                currentLineIndex = 0;
            }
        }

        // Promote to foreground with initial notification
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Keep service alive in background (activity may rebind)
        callback = null;
        return true; // allow rebind
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterNotificationReceiver();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Public API (called by the activity via LocalBinder)
    // ──────────────────────────────────────────────────────────────────────

    public void setUpdateCallback(PlaybackCallback cb) {
        this.callback = cb;
        // Immediately inform the newly-bound activity of current state
        if (cb != null) {
            if (isTtsReady) cb.onTtsReady();
            if (!dialogueLines.isEmpty() && currentLineIndex < dialogueLines.size()) {
                cb.onLineChanged(currentLineIndex, dialogueLines.get(currentLineIndex));
            }
            cb.onPlayStateChanged(isPlaying);
        }
    }

    public void setScript(String rawScript, String title, String author) {
        if (title  != null) paperTitle  = title;
        if (author != null) paperAuthor = author;
        parseDialogue(rawScript);
        currentLineIndex = 0;
        if (callback != null && !dialogueLines.isEmpty()) {
            callback.onLineChanged(0, dialogueLines.get(0));
        }
        refreshNotification();
    }

    public void play() {
        if (!isTtsReady || dialogueLines.isEmpty()) return;
        isPlaying = true;
        isPaused  = false;
        speakCurrentLine();
        if (callback != null) callback.onPlayStateChanged(true);
        refreshNotification();
    }

    public void pause() {
        isPlaying = false;
        isPaused  = true;
        stopProgressTicker();
        if (tts != null) tts.stop();
        if (callback != null) callback.onPlayStateChanged(false);
        refreshNotification();
    }

    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            // If paused mid-way, resume from the same line (not from scratch)
            play();
        }
    }

    /** Seek to a specific line index (called when user taps the progress bar). */
    public void seekToLine(int lineIndex) {
        if (dialogueLines.isEmpty()) return;
        currentLineIndex = Math.max(0, Math.min(lineIndex, dialogueLines.size() - 1));
        isPaused = false;
        recalcElapsedBefore();
        notifyLineChanged();
        if (isPlaying) speakCurrentLine();
        refreshNotification();
    }

    public void prevLine() {
        currentLineIndex = Math.max(0, currentLineIndex - 2);
        if (isPlaying) speakCurrentLine();
        notifyLineChanged();
        refreshNotification();
    }

    public void nextLine() {
        if (dialogueLines.isEmpty()) return;
        currentLineIndex = Math.min(dialogueLines.size() - 1, currentLineIndex + 2);
        if (isPlaying) speakCurrentLine();
        notifyLineChanged();
        refreshNotification();
    }

    public void rewind() {
        prevLine();
    }

    public void fastForward() {
        nextLine();
    }

    public void setSpeed(float speed) {
        playbackSpeed = speed;
        if (tts != null) tts.setSpeechRate(playbackSpeed);
        if (isPlaying) speakCurrentLine();
    }

    // ── Getters for activity state restoration ────────────────────────────
    public boolean isPlaying()            { return isPlaying; }
    public boolean isTtsReady()           { return isTtsReady; }
    public int     getCurrentLineIndex()  { return currentLineIndex; }
    public float   getPlaybackSpeed()     { return playbackSpeed; }
    public int     getTotalLines()        { return dialogueLines.size(); }
    public int     getTotalEstimatedMs()  { return totalEstimatedMs; }

    /** Returns elapsed ms across the whole podcast (for smooth progress). */
    public int getElapsedMs() {
        if (!isPlaying || lineStartTimeMs == 0) {
            return elapsedBeforeCurrent;
        }
        long sinceLineStart = System.currentTimeMillis() - lineStartTimeMs;
        return (int) (elapsedBeforeCurrent + Math.min(sinceLineStart, lineEstimatedMs));
    }

    public DialogueLine getCurrentLine() {
        if (dialogueLines.isEmpty() || currentLineIndex >= dialogueLines.size()) return null;
        return dialogueLines.get(currentLineIndex);
    }

    public List<DialogueLine> getDialogueLines() {
        return dialogueLines;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  TTS initialisation  (migrated verbatim from PodcastPlayerActivity)
    // ──────────────────────────────────────────────────────────────────────
    private void initTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this,
                            "English TTS not supported on this device.", Toast.LENGTH_LONG).show();
                } else {
                    tts.setSpeechRate(playbackSpeed);
                    pickVoices();
                    setupUtteranceListener();
                    isTtsReady = true;
                    if (callback != null) callback.onTtsReady();
                }
            } else {
                Toast.makeText(this,
                        "Text-to-Speech initialisation failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Picks two distinct English voices for Alice (female preference) and
     * Bob (male preference). Falls back gracefully if only one voice exists.
     * Migrated verbatim from PodcastPlayerActivity.
     */
    private void pickVoices() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null || voices.isEmpty()) return;

            List<Voice> englishVoices = new ArrayList<>();
            for (Voice v : voices) {
                if (v.getLocale() != null
                        && v.getLocale().getLanguage().equals("en")
                        && !v.isNetworkConnectionRequired()) {
                    englishVoices.add(v);
                }
            }
            if (englishVoices.isEmpty()) {
                for (Voice v : voices) {
                    if (v.getLocale() != null && v.getLocale().getLanguage().equals("en")) {
                        englishVoices.add(v);
                    }
                }
            }
            if (englishVoices.isEmpty()) return;

            for (Voice v : englishVoices) {
                String name = v.getName().toLowerCase(Locale.US);
                if (name.contains("female") || name.contains("f-") || name.contains("-f_")
                        || name.contains("woman") || name.contains("girl")) {
                    aliceVoice = v;
                    break;
                }
            }
            for (Voice v : englishVoices) {
                String name = v.getName().toLowerCase(Locale.US);
                if ((name.contains("male") && !name.contains("female"))
                        || name.contains("m-") || name.contains("-m_")
                        || name.contains("man") || name.contains("boy")) {
                    if (aliceVoice == null || !v.getName().equals(aliceVoice.getName())) {
                        bobVoice = v;
                        break;
                    }
                }
            }
            if (aliceVoice == null && englishVoices.size() >= 1) aliceVoice = englishVoices.get(0);
            if (bobVoice   == null && englishVoices.size() >= 2) bobVoice   = englishVoices.get(1);
        } catch (Exception ignored) {
            // Voice enumeration failed — default voice with pitch changes will be used
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Utterance listener
    // ──────────────────────────────────────────────────────────────────────
    private void setupUtteranceListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                if (callback != null && currentLineIndex < dialogueLines.size()) {
                    callback.onLineChanged(currentLineIndex, dialogueLines.get(currentLineIndex));
                }
                refreshNotification();
            }

            @Override
            public void onDone(String utteranceId) {
                if (!isPlaying) return;
                currentLineIndex++;
                if (currentLineIndex < dialogueLines.size()) {
                    speakCurrentLine();
                    notifyLineChanged();
                } else {
                    // Podcast finished
                    isPlaying = false;
                    currentLineIndex = 0;
                    if (callback != null) {
                        callback.onPlayStateChanged(false);
                        callback.onFinished();
                    }
                    refreshNotification();
                }
            }

            @Override
            public void onError(String utteranceId) {
                Toast.makeText(PodcastPlayerService.this,
                        "Audio playback error.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Core speech
    // ──────────────────────────────────────────────────────────────────────
    private void speakCurrentLine() {
        if (tts == null || !isTtsReady || dialogueLines.isEmpty()) return;
        if (currentLineIndex >= dialogueLines.size()) return;

        DialogueLine line = dialogueLines.get(currentLineIndex);

        // Estimate duration: ~13 chars/sec at normal speed (rough approximation)
        int charCount = line.text.length();
        lineEstimatedMs = (int) ((charCount / (13f * playbackSpeed)) * 1000);
        if (lineEstimatedMs < 800) lineEstimatedMs = 800;
        lineStartTimeMs = System.currentTimeMillis();
        recalcElapsedBefore();

        if ("Alice".equals(line.speaker)) {
            if (aliceVoice != null) tts.setVoice(aliceVoice);
            tts.setPitch(1.15f);
        } else {
            if (bobVoice != null) tts.setVoice(bobVoice);
            tts.setPitch(0.85f);
        }
        tts.setSpeechRate(playbackSpeed);

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "line_" + currentLineIndex);
        tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, params, "line_" + currentLineIndex);

        // Start the smooth progress ticker
        startProgressTicker();
    }

    private void stopPlayback() {
        isPlaying = false;
        isPaused  = false;
        stopProgressTicker();
        if (tts != null) tts.stop();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Smooth-progress ticker
    // ──────────────────────────────────────────────────────────────────────
    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) return;
            if (callback != null) {
                // Re-use onLineChanged with the current index so the activity
                // can call getElapsedMs() / getTotalEstimatedMs() for the bar.
                if (currentLineIndex < dialogueLines.size()) {
                    callback.onLineChanged(currentLineIndex,
                            dialogueLines.get(currentLineIndex));
                }
            }
            progressHandler.postDelayed(this, 500);
        }
    };

    private void startProgressTicker() {
        progressHandler.removeCallbacks(progressTick);
        progressHandler.postDelayed(progressTick, 500);
    }

    private void stopProgressTicker() {
        progressHandler.removeCallbacks(progressTick);
    }

    /** Recalculate the estimated total duration and elapsed-before-current. */
    private void recalcElapsedBefore() {
        totalEstimatedMs   = 0;
        elapsedBeforeCurrent = 0;
        for (int i = 0; i < dialogueLines.size(); i++) {
            int charCount = dialogueLines.get(i).text.length();
            int dur = (int) ((charCount / (13f * playbackSpeed)) * 1000);
            if (dur < 800) dur = 800;
            if (i < currentLineIndex) elapsedBeforeCurrent += dur;
            totalEstimatedMs += dur;
        }
        // Update line estimate too
        if (currentLineIndex < dialogueLines.size()) {
            int charCount = dialogueLines.get(currentLineIndex).text.length();
            lineEstimatedMs = (int) ((charCount / (13f * playbackSpeed)) * 1000);
            if (lineEstimatedMs < 800) lineEstimatedMs = 800;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Dialogue parser  (migrated verbatim from PodcastPlayerActivity)
    // ──────────────────────────────────────────────────────────────────────
    private void parseDialogue(String rawScript) {
        dialogueLines.clear();

        String clean = rawScript
                .replaceAll("\\*\\*", "")
                .replaceAll("\\[.*?\\]", "")
                .trim();

        String[] lines = clean.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String lowerLine = trimmed.toLowerCase(Locale.US);
            DialogueLine dl  = new DialogueLine();

            if (lowerLine.startsWith("alice:")) {
                dl.speaker = "Alice";
                dl.text    = trimmed.substring(6).trim();
            } else if (lowerLine.startsWith("bob:")) {
                dl.speaker = "Bob";
                dl.text    = trimmed.substring(4).trim();
            } else {
                dl.speaker = dialogueLines.isEmpty() ? "Alice"
                        : dialogueLines.get(dialogueLines.size() - 1).speaker.equals("Alice")
                        ? "Bob" : "Alice";
                dl.text = trimmed;
            }

            if (!dl.text.isEmpty()) dialogueLines.add(dl);
        }

        if (dialogueLines.isEmpty()) {
            DialogueLine dl = new DialogueLine();
            dl.speaker = "Alice";
            dl.text    = rawScript.trim();
            dialogueLines.add(dl);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Notification helpers
    // ──────────────────────────────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Podcast Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controls for Scholar Mind podcast player");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        // ── Tap action: open PodcastPlayerActivity ─────────────────────────
        Intent openIntent = new Intent(this, PodcastPlayerActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // ── Action PendingIntents ──────────────────────────────────────────
        PendingIntent prevPi  = buildActionIntent(ACTION_PREV,       1);
        PendingIntent playPi  = buildActionIntent(ACTION_PLAY_PAUSE, 2);
        PendingIntent nextPi  = buildActionIntent(ACTION_NEXT,       3);

        // ── Content text ───────────────────────────────────────────────────
        DialogueLine current = getCurrentLine();
        String speakerBadge  = "Scholar Mind Podcast";
        String contentText   = paperTitle.isEmpty() ? "Playing..." : paperTitle;
        if (current != null) {
            speakerBadge = "🎙 " + current.speaker;
            String snippet = current.text.length() > 60
                    ? current.text.substring(0, 57) + "…"
                    : current.text;
            contentText = snippet;
        }

        int playPauseIcon = isPlaying
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;
        String playPauseLabel = isPlaying ? "Pause" : "Play";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(speakerBadge)
                .setContentText(contentText)
                .setSubText(paperTitle)
                .setContentIntent(openPi)
                .setOngoing(isPlaying)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // ── Action buttons ─────────────────────────────────────────
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPi)
                .addAction(playPauseIcon,                         playPauseLabel, playPi)
                .addAction(android.R.drawable.ic_media_next,     "Next",     nextPi)
                // ── Media style ────────────────────────────────────────────
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private PendingIntent buildActionIntent(String action, int requestCode) {
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        return PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Pushes a fresh notification without changing the foreground state. */
    private void refreshNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    // ──────────────────────────────────────────────────────────────────────
    //  BroadcastReceiver for notification button taps
    // ──────────────────────────────────────────────────────────────────────
    public class NotificationActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            switch (String.valueOf(intent.getAction())) {
                case ACTION_PLAY_PAUSE: togglePlayPause(); break;
                case ACTION_PREV:       prevLine();        break;
                case ACTION_NEXT:       nextLine();        break;
                case ACTION_STOP:
                    stopPlayback();
                    stopForeground(true);
                    stopSelf();
                    break;
            }
        }
    }

    private void registerNotificationReceiver() {
        notifReceiver = new NotificationActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY_PAUSE);
        filter.addAction(ACTION_PREV);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notifReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notifReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterNotificationReceiver() {
        if (receiverRegistered && notifReceiver != null) {
            unregisterReceiver(notifReceiver);
            receiverRegistered = false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────────────────────────────
    private void notifyLineChanged() {
        if (callback != null && currentLineIndex < dialogueLines.size()) {
            callback.onLineChanged(currentLineIndex, dialogueLines.get(currentLineIndex));
        }
    }
}
