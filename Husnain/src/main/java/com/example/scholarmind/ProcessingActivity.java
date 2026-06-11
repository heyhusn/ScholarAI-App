package com.example.scholarmind;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ProcessingActivity extends AppCompatActivity {
    private TextView tvFilename;
    private TextView tvProgress;
    private TextView btnContinue;
    private String paperId, paperTitle, paperAuthor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        tvFilename = findViewById(R.id.tvFilename);
        tvProgress = findViewById(R.id.tvProgress);
        btnContinue = findViewById(R.id.btnContinue);

        paperId = getIntent().getStringExtra("paperId");
        paperTitle = getIntent().getStringExtra("paperTitle");
        paperAuthor = getIntent().getStringExtra("paperAuthor");

        if (paperTitle != null) {
            tvFilename.setText(paperTitle);
        }

        // Mock Progress update and routing delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            tvProgress.setText("100% complete");
            btnContinue.setEnabled(true);
            
            // Auto redirect to ModeSelectActivity after 1.5 seconds of completion
            new Handler(Looper.getMainLooper()).postDelayed(this::goToModeSelect, 1500);
        }, 2500);

        btnContinue.setOnClickListener(v -> goToModeSelect());
    }

    private void goToModeSelect() {
        Intent intent = new Intent(ProcessingActivity.this, ModeSelectActivity.class);
        intent.putExtra("paperId", paperId);
        intent.putExtra("paperTitle", paperTitle);
        intent.putExtra("paperAuthor", paperAuthor);
        startActivity(intent);
        finish();
    }
}
