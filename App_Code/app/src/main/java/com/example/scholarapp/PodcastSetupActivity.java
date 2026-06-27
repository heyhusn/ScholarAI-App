package com.example.scholarapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PodcastSetupActivity extends AppCompatActivity {

    private FrameLayout btnBack;
    private LinearLayout cardBasic;
    private LinearLayout cardDeep;
    private TextView tvBasicTitle;
    private TextView tvBasicDesc;
    private TextView tvDeepTitle;
    private TextView tvDeepDesc;
    private EditText etInstructions;
    private android.widget.Button btnGenerate;

    private boolean isBasicSelected = true;

    // Paper info
    private String paperId;
    private String paperTitle;
    private String paperAuthor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_setup);

        paperId = getIntent().getStringExtra("paperId");
        paperTitle = getIntent().getStringExtra("paperTitle");
        paperAuthor = getIntent().getStringExtra("paperAuthor");

        bindViews();
        setupClickListeners();
        updateSelectionUI();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        cardBasic = findViewById(R.id.cardBasic);
        cardDeep = findViewById(R.id.cardDeep);
        tvBasicTitle = findViewById(R.id.tvBasicTitle);
        tvBasicDesc = findViewById(R.id.tvBasicDesc);
        tvDeepTitle = findViewById(R.id.tvDeepTitle);
        tvDeepDesc = findViewById(R.id.tvDeepDesc);
        etInstructions = findViewById(R.id.etInstructions);
        btnGenerate = findViewById(R.id.btnGenerate);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        cardBasic.setOnClickListener(v -> {
            isBasicSelected = true;
            updateSelectionUI();
        });

        cardDeep.setOnClickListener(v -> {
            isBasicSelected = false;
            updateSelectionUI();
        });

        btnGenerate.setOnClickListener(v -> {
            Intent intent = new Intent(PodcastSetupActivity.this, PodcastProgressActivity.class);
            intent.putExtra("paperId", paperId);
            intent.putExtra("paperTitle", paperTitle);
            intent.putExtra("paperAuthor", paperAuthor);
            intent.putExtra("isBasic", isBasicSelected);
            intent.putExtra("instructions", etInstructions.getText().toString().trim());
            startActivity(intent);
        });
    }

    private void updateSelectionUI() {
        if (isBasicSelected) {
            cardBasic.setBackgroundResource(R.drawable.podcast_card_selected);
            cardDeep.setBackgroundResource(R.drawable.podcast_card_unselected);

            tvBasicTitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
            tvBasicDesc.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary));

            tvDeepTitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
            tvDeepDesc.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_muted));
        } else {
            cardBasic.setBackgroundResource(R.drawable.podcast_card_unselected);
            cardDeep.setBackgroundResource(R.drawable.podcast_card_selected);

            tvBasicTitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
            tvBasicDesc.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_muted));

            tvDeepTitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
            tvDeepDesc.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary));
        }
    }
}
