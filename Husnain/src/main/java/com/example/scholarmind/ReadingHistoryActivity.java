package com.example.scholarmind;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.scholarmind.FirebaseManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ReadingHistoryActivity
 * ───────────────────────
 * Fetches all papers from Firestore and lists them.
 * Tapping a paper opens ModeSelectActivity.
 *
 * Layout IDs expected in activity_reading_history.xml:
 *   btnBack        – FrameLayout
 *   llPaperList    – LinearLayout  (papers added here dynamically)
 *   tvEmpty        – TextView      (shown when no papers exist)
 *   tvLoading      – TextView      (shown while fetching)
 */
public class ReadingHistoryActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────
    private FrameLayout  btnBack;
    private LinearLayout llPaperList;
    private TextView     tvEmpty;
    private TextView     tvLoading;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    // ── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_history);

        bindViews();
        loadHistory();
    }

    // ── Binding ────────────────────────────────────────────────────────────
    private void bindViews() {
        btnBack     = findViewById(R.id.btnBack);
        llPaperList = findViewById(R.id.llPaperList);
        tvEmpty     = findViewById(R.id.tvEmpty);
        tvLoading   = findViewById(R.id.tvLoading);

        btnBack.setOnClickListener(v -> finish());
        tvEmpty.setVisibility(View.GONE);
        tvLoading.setVisibility(View.VISIBLE);
    }

    // ── Firestore READ ─────────────────────────────────────────────────────
    private void loadHistory() {
        FirebaseManager.getInstance().getAllPapers(
                papers -> {
                    tvLoading.setVisibility(View.GONE);
                    if (papers.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        for (Map<String, Object> paper : papers) {
                            addPaperRow(paper);
                        }
                    }
                },
                e -> {
                    tvLoading.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Could not load history.\n" + e.getMessage());
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
        );
    }

    // ── Paper row factory ──────────────────────────────────────────────────
    private void addPaperRow(Map<String, Object> paper) {
        String id       = (String) paper.get("id");
        String title    = (String) paper.get("title");
        String author   = (String) paper.get("author");
        String year     = (String) paper.get("year");
        String category = (String) paper.get("category");
        String status   = (String) paper.get("status");
        Long   created  = (Long)   paper.get("createdAt");

        // Card wrapper
        CardView card = new CardView(this);
        card.setRadius(16f);
        card.setCardElevation(4f);
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, 0, 0, 20);
        card.setLayoutParams(cardLp);

        // Inner layout
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(40, 32, 40, 32);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title != null ? title : "Untitled");
        tvTitle.setTextSize(15f);
        tvTitle.setTextColor(0xFF1A1A2E);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setMaxLines(2);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        inner.addView(tvTitle);

        // Author + Year
        TextView tvAuthor = new TextView(this);
        String authorYear = (author != null ? author : "") + (year != null ? " · " + year : "");
        tvAuthor.setText(authorYear);
        tvAuthor.setTextSize(13f);
        tvAuthor.setTextColor(0xFF888888);
        tvAuthor.setPadding(0, 6, 0, 0);
        inner.addView(tvAuthor);

        // Row: category chip + status chip + date
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setPadding(0, 10, 0, 0);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        if (category != null) {
            TextView tvCategory = makeChip(category, 0xFF4A90D9);
            metaRow.addView(tvCategory);
        }

        if (status != null) {
            int chipColor = status.equals("done") ? 0xFF27AE60 :
                    status.equals("error") ? 0xFFE74C3C : 0xFFF39C12;
            TextView tvStatus = makeChip(status, chipColor);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginStart(12);
            tvStatus.setLayoutParams(lp);
            metaRow.addView(tvStatus);
        }

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        metaRow.addView(spacer);

        // Date
        if (created != null) {
            TextView tvDate = new TextView(this);
            tvDate.setText(DATE_FORMAT.format(new Date(created)));
            tvDate.setTextSize(12f);
            tvDate.setTextColor(0xFFAAAAAA);
            metaRow.addView(tvDate);
        }

        inner.addView(metaRow);
        card.addView(inner);
        llPaperList.addView(card);

        // Click → ModeSelectActivity
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, ModeSelectActivity.class);
            intent.putExtra("paperId",     id);
            intent.putExtra("paperTitle",  title);
            intent.putExtra("paperAuthor", author != null ? author + " · " + year : "");
            startActivity(intent);
        });
    }

    // ── Chip helper ────────────────────────────────────────────────────────
    private TextView makeChip(String label, int color) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11f);
        chip.setTextColor(0xFFFFFFFF);
        chip.setPadding(20, 8, 20, 8);
        chip.setBackgroundColor(color);
        // Rounded via setBackground would need a drawable; colour is sufficient here
        return chip;
    }
}