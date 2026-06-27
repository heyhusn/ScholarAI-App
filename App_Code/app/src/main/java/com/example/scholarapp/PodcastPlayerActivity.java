package com.example.scholarapp;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

/**
 * UI controller for the podcast player screen. All TTS and dialogue state now
 * lives in {@link PodcastPlayerService}. This activity binds to the service,
 * registers a {@link PodcastPlayerService.PlaybackCallback} for real-time
 * updates, and delegates every button press to the service's public API.
 *
 * <p>When the user leaves (onStop), the activity unbinds but does NOT stop the
 * service — playback continues in the background with the Media-Style
 * notification. When the user returns, onStart re-binds and the activity
 * restores its UI from the service's current state.
 */
public class PodcastPlayerActivity extends AppCompatActivity
        implements PodcastPlayerService.PlaybackCallback {

    // ── Views ──────────────────────────────────────────────────────────────
    private FrameLayout btnBack;
    private View progressTrack;
    private View progressFill;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView tvPaperTitle;
    private TextView tvPaperAuthor;
    private TextView tvTranscriptText;
    private TextView tvSpeakerLabel;
    private TextView tvPlayIcon;
    private FrameLayout btnPlayPause;
    private FrameLayout btnRewind;
    private FrameLayout btnFF;
    private FrameLayout btnPrev;
    private FrameLayout btnNext;
    private View micRingOuter;
    private View micRingInner;

    private TextView btnSpeed075;
    private TextView btnSpeed100;
    private TextView btnSpeed125;
    private TextView btnSpeed150;
    private TextView btnSpeed200;

    // ── Service binding ────────────────────────────────────────────────────
    private PodcastPlayerService playerService;
    private boolean serviceBound = false;

    // ── Intent data (kept for starting/restarting the service) ─────────────
    private String paperTitle;
    private String paperAuthor;
    private String rawScript;

    // ── Ring-pulse animator ────────────────────────────────────────────────
    private android.animation.ValueAnimator ringPulseAnimator;

    // ──────────────────────────────────────────────────────────────────────
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PodcastPlayerService.LocalBinder binder =
                    (PodcastPlayerService.LocalBinder) service;
            playerService = binder.getService();
            serviceBound  = true;

            // Push the script if the service hasn't received it yet (first launch)
            if (rawScript != null && playerService.getTotalLines() == 0) {
                playerService.setScript(rawScript, paperTitle, paperAuthor);
            }

            // Register for live callbacks — will immediately sync UI state
            playerService.setUpdateCallback(PodcastPlayerActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound  = false;
            playerService = null;
        }
    };

    // ──────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_player);

        bindViews();
        readIntentData();
        setupClickListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Start the service (idempotent) then bind to it
        Intent serviceIntent = new Intent(this, PodcastPlayerService.class);
        serviceIntent.putExtra(PodcastPlayerService.EXTRA_SCRIPT,       rawScript);
        serviceIntent.putExtra(PodcastPlayerService.EXTRA_PAPER_TITLE,  paperTitle);
        serviceIntent.putExtra(PodcastPlayerService.EXTRA_PAPER_AUTHOR, paperAuthor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unregister callback so we don't leak the activity, but keep the
        // service running for background playback.
        if (serviceBound && playerService != null) {
            playerService.setUpdateCallback(null);
        }
        unbindService(serviceConnection);
        serviceBound  = false;
        playerService = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingPulse();

        // Only fully stop the service when the activity is truly finishing
        // (back pressed / finish() called), NOT on rotation/config changes.
        if (isFinishing()) {
            Intent stopIntent = new Intent(this, PodcastPlayerService.class);
            stopIntent.setAction(PodcastPlayerService.ACTION_STOP);
            startService(stopIntent);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  PlaybackCallback — called from PodcastPlayerService on its thread
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public void onTtsReady() {
        // TTS is ready — nothing extra needed; UI is driven by other callbacks
    }

    @Override
    public void onLineChanged(int lineIndex, PodcastPlayerService.DialogueLine line) {
        runOnUiThread(() -> {
            updateTranscriptDisplay(line);
            updatePlaybackProgress(lineIndex);
        });
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        runOnUiThread(() -> {
            if (playing) {
                tvPlayIcon.setText("||");
                tvPlayIcon.setPadding(0, 0, 0, 0);
                startRingPulse();
            } else {
                tvPlayIcon.setText("▶");
                tvPlayIcon.setPadding(6, 0, 0, 0);
                stopRingPulse();
            }
        });
    }

    @Override
    public void onFinished() {
        runOnUiThread(() -> {
            tvPlayIcon.setText("▶");
            tvPlayIcon.setPadding(6, 0, 0, 0);
            stopRingPulse();
            updatePlaybackProgress(0);
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    //  View binding
    // ──────────────────────────────────────────────────────────────────────
    private void bindViews() {
        btnBack          = findViewById(R.id.btnBack);
        progressTrack    = findViewById(R.id.progressTrack);
        progressFill     = findViewById(R.id.progressFill);
        tvCurrentTime    = findViewById(R.id.tvCurrentTime);
        tvTotalTime      = findViewById(R.id.tvTotalTime);
        tvPaperTitle     = findViewById(R.id.tvPaperTitle);
        tvPaperAuthor    = findViewById(R.id.tvPaperAuthor);
        tvTranscriptText = findViewById(R.id.tvTranscriptText);
        tvSpeakerLabel   = findViewById(R.id.tvSpeakerLabel);
        tvPlayIcon       = findViewById(R.id.tvPlayIcon);
        btnPlayPause     = findViewById(R.id.btnPlayPause);
        btnRewind        = findViewById(R.id.btnRewind);
        btnFF            = findViewById(R.id.btnFF);
        btnPrev          = findViewById(R.id.btnPrev);
        btnNext          = findViewById(R.id.btnNext);
        micRingOuter     = findViewById(R.id.micRingOuter);
        micRingInner     = findViewById(R.id.micRingInner);

        btnSpeed075 = findViewById(R.id.btnSpeed075);
        btnSpeed100 = findViewById(R.id.btnSpeed100);
        btnSpeed125 = findViewById(R.id.btnSpeed125);
        btnSpeed150 = findViewById(R.id.btnSpeed150);
        btnSpeed200 = findViewById(R.id.btnSpeed200);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Read intent extras
    // ──────────────────────────────────────────────────────────────────────
    private void readIntentData() {
        paperTitle  = getIntent().getStringExtra("paperTitle");
        paperAuthor = getIntent().getStringExtra("paperAuthor");
        rawScript   = getIntent().getStringExtra("script");

        if (rawScript == null || rawScript.trim().isEmpty()) {
            rawScript = "Alice: Welcome to Scholar Mind!\nBob: Let us explore this fascinating paper together.";
        }

        if (paperTitle  != null) tvPaperTitle.setText(paperTitle);
        if (paperAuthor != null) tvPaperAuthor.setText(paperAuthor);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Click listeners — all delegate to the service
    // ──────────────────────────────────────────────────────────────────────
    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnPlayPause.setOnClickListener(v -> {
            if (playerService == null) {
                Toast.makeText(this, "Player is still starting up, please wait.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!playerService.isTtsReady()) {
                Toast.makeText(this, "TTS is still initialising, please wait.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            playerService.togglePlayPause();
        });

        btnRewind.setOnClickListener(v -> {
            if (playerService != null) playerService.rewind();
        });

        btnFF.setOnClickListener(v -> {
            if (playerService != null) playerService.fastForward();
        });

        btnPrev.setOnClickListener(v -> {
            if (playerService != null) playerService.prevLine();
        });

        btnNext.setOnClickListener(v -> {
            if (playerService != null) playerService.nextLine();
        });

        btnSpeed075.setOnClickListener(v -> changeSpeed(0.75f, btnSpeed075));
        btnSpeed100.setOnClickListener(v -> changeSpeed(1.0f,  btnSpeed100));
        btnSpeed125.setOnClickListener(v -> changeSpeed(1.25f, btnSpeed125));
        btnSpeed150.setOnClickListener(v -> changeSpeed(1.5f,  btnSpeed150));
        btnSpeed200.setOnClickListener(v -> changeSpeed(2.0f,  btnSpeed200));

        // ── Seek by tapping the progress track ────────────────────────────
        if (progressTrack != null) {
            progressTrack.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP
                        || event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    if (playerService == null || !playerService.isTtsReady()) return true;
                    int totalLines = playerService.getTotalLines();
                    if (totalLines == 0) return true;
                    float fraction = event.getX() / v.getWidth();
                    if (fraction < 0f) fraction = 0f;
                    if (fraction > 1f) fraction = 1f;
                    int targetLine = (int) (fraction * totalLines);
                    playerService.seekToLine(targetLine);
                }
                return true;
            });
        }

        // Tactile press feedback
        com.example.scholarapp.utils.TouchFeedbackUtils.applyScaleFeedback(btnPlayPause);
        com.example.scholarapp.utils.TouchFeedbackUtils.applyScaleFeedback(btnRewind);
        com.example.scholarapp.utils.TouchFeedbackUtils.applyScaleFeedback(btnFF);
        com.example.scholarapp.utils.TouchFeedbackUtils.applyScaleFeedback(btnPrev);
        com.example.scholarapp.utils.TouchFeedbackUtils.applyScaleFeedback(btnNext);
    }

    private void changeSpeed(float speed, TextView activeBtn) {
        if (playerService != null) playerService.setSpeed(speed);

        int unselectedColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary);
        btnSpeed075.setBackgroundResource(R.drawable.player_speed_unselected); btnSpeed075.setTextColor(unselectedColor);
        btnSpeed100.setBackgroundResource(R.drawable.player_speed_unselected); btnSpeed100.setTextColor(unselectedColor);
        btnSpeed125.setBackgroundResource(R.drawable.player_speed_unselected); btnSpeed125.setTextColor(unselectedColor);
        btnSpeed150.setBackgroundResource(R.drawable.player_speed_unselected); btnSpeed150.setTextColor(unselectedColor);
        btnSpeed200.setBackgroundResource(R.drawable.player_speed_unselected); btnSpeed200.setTextColor(unselectedColor);

        activeBtn.setBackgroundResource(R.drawable.player_speed_selected);
        activeBtn.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
    }

    // ──────────────────────────────────────────────────────────────────────
    //  UI update helpers
    // ──────────────────────────────────────────────────────────────────────
    /**
     * Smooth progress: uses elapsed milliseconds (with intra-line interpolation)
     * when data is available, falls back to discrete line-count otherwise.
     */
    private void updatePlaybackProgress(int lineIndex) {
        if (playerService == null) return;
        int totalLines = playerService.getTotalLines();
        if (totalLines == 0) return;

        int totalMs   = playerService.getTotalEstimatedMs();
        int elapsedMs = playerService.getElapsedMs();

        float ratio;
        int elapsedSec;
        int totalSec;

        if (totalMs > 0) {
            ratio      = Math.min(1f, (float) elapsedMs / totalMs);
            elapsedSec = elapsedMs / 1000;
            totalSec   = totalMs  / 1000;
        } else {
            // Fallback — plain line ratio (~4 s/line)
            ratio      = (float) lineIndex / totalLines;
            elapsedSec = lineIndex  * 4;
            totalSec   = totalLines * 4;
        }

        tvCurrentTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                elapsedSec / 60, elapsedSec % 60));
        tvTotalTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                totalSec / 60, totalSec % 60));

        if (progressFill != null) {
            final float finalRatio = ratio;
            ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
            progressFill.post(() -> {
                View parent = (View) progressFill.getParent();
                if (parent != null) {
                    lp.width = (int) (parent.getWidth() * finalRatio);
                    progressFill.setLayoutParams(lp);
                }
            });
        }
    }

    /**
     * Fades out the old transcript text, switches content & speaker label,
     * then fades back in. Unchanged from original activity.
     */
    private void updateTranscriptDisplay(PodcastPlayerService.DialogueLine line) {
        boolean isAlice = "Alice".equals(line.speaker);

        int labelColor = isAlice ? 0xFF7C3AED : 0xFF0D9488;
        String badge   = isAlice ? "🎙 Alice" : "🎙 Bob";

        if (tvSpeakerLabel != null) {
            tvSpeakerLabel.setText(badge);
            tvSpeakerLabel.setTextColor(labelColor);
        }

        if (tvTranscriptText != null) {
            final String newText = line.text;
            if (newText.equals(tvTranscriptText.getText().toString())) return;

            tvTranscriptText.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .withEndAction(() -> {
                        tvTranscriptText.setText(newText);
                        tvTranscriptText.animate()
                                .alpha(1f)
                                .setDuration(220)
                                .start();
                    })
                    .start();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Ring-pulse animation — unchanged from original
    // ──────────────────────────────────────────────────────────────────────
    private void startRingPulse() {
        if (ringPulseAnimator == null) {
            ringPulseAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            ringPulseAnimator.setDuration(3000);
            ringPulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            ringPulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            ringPulseAnimator.addUpdateListener(anim -> {
                float p = (float) anim.getAnimatedValue();
                if (micRingOuter != null) {
                    micRingOuter.setAlpha(0.1f + p * 0.25f);
                    micRingOuter.setScaleX(1f + p * 0.08f);
                    micRingOuter.setScaleY(1f + p * 0.08f);
                }
                if (micRingInner != null) {
                    micRingInner.setAlpha(0.2f + (1f - p) * 0.3f);
                    micRingInner.setScaleX(1f + (1f - p) * 0.05f);
                    micRingInner.setScaleY(1f + (1f - p) * 0.05f);
                }
            });
        }
        if (!ringPulseAnimator.isRunning()) ringPulseAnimator.start();
    }

    private void stopRingPulse() {
        if (ringPulseAnimator != null) ringPulseAnimator.cancel();
        if (micRingOuter != null) micRingOuter.setAlpha(0f);
        if (micRingInner != null) micRingInner.setAlpha(0f);
    }
}
