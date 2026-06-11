package com.example.scholarmind;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.example.scholarmind.utils.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;

public class HomeActivity extends AppCompatActivity {
    private TextView tvGreeting;
    private LinearLayout cardBeginner, cardTechnical, cardPodcast, cardFlashcards;
    private LinearLayout llPaperVaswani, llPaperBert;
    private ConstraintLayout bannerUpload;
    private LinearLayout btnNavHome, btnNavHistory, btnNavCards, btnNavProfile;
    private FrameLayout btnNavUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);

        // Bind Views
        tvGreeting = findViewById(R.id.greeting);
        cardBeginner = findViewById(R.id.cardBeginner);
        cardTechnical = findViewById(R.id.cardTechnical);
        cardPodcast = findViewById(R.id.cardPodcast);
        cardFlashcards = findViewById(R.id.cardFlashcards);
        
        llPaperVaswani = findViewById(R.id.llPaperVaswani);
        llPaperBert = findViewById(R.id.llPaperBert);
        bannerUpload = findViewById(R.id.bannerUpload);

        btnNavHome = findViewById(R.id.btnNavHome);
        btnNavHistory = findViewById(R.id.btnNavHistory);
        btnNavUpload = findViewById(R.id.btnNavUpload);
        btnNavCards = findViewById(R.id.btnNavCards);
        btnNavProfile = findViewById(R.id.btnNavProfile);

        // Retrieve User Profile Name dynamically from Firestore
        String currentUserId = FirebaseManager.getInstance().getCurrentUserId();
        if (currentUserId != null) {
            FirebaseManager.getInstance().getUserProfile(currentUserId, documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String fullName = documentSnapshot.getString("fullName");
                    if (fullName != null && !fullName.trim().isEmpty()) {
                        tvGreeting.setText("Good morning, " + fullName.trim() + " 👋");
                    }
                }
            }, e -> {
                // Fallback to default name in layout XML
            });
        }

        // Setup Click Listeners for Navigation
        cardBeginner.setOnClickListener(v -> launchModeActivity(BeginnerModeActivity.class));
        cardTechnical.setOnClickListener(v -> launchModeActivity(TechnicalModeActivity.class));
        cardPodcast.setOnClickListener(v -> launchModeActivity(ChatModeActivity.class)); // ChatMode is used for AI Podcast narrative
        cardFlashcards.setOnClickListener(v -> launchActivity(FlashcardsActivity.class));

        // Recent Papers Click (opens mode selector screen)
        if (llPaperVaswani != null) {
            llPaperVaswani.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, ModeSelectActivity.class);
                intent.putExtra("paperTitle", "Attention Is All You Need");
                intent.putExtra("paperAuthor", "Vaswani et al. · 2017");
                startActivity(intent);
            });
        }

        if (llPaperBert != null) {
            llPaperBert.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, ModeSelectActivity.class);
                intent.putExtra("paperTitle", "BERT: Pre-training of Deep Bidirectional Transformers");
                intent.putExtra("paperAuthor", "Devlin et al. · 2019");
                startActivity(intent);
            });
        }

        bannerUpload.setOnClickListener(v -> launchActivity(UploadActivity.class));

        // Custom Bottom Navigation click handling
        btnNavHistory.setOnClickListener(v -> launchActivity(ReadingHistoryActivity.class));
        btnNavUpload.setOnClickListener(v -> launchActivity(UploadActivity.class));
        btnNavCards.setOnClickListener(v -> launchActivity(FlashcardsActivity.class));
        btnNavProfile.setOnClickListener(v -> launchActivity(ProfileActivity.class));
    }

    private void launchActivity(Class<?> activityClass) {
        startActivity(new Intent(HomeActivity.this, activityClass));
    }

    private void launchModeActivity(Class<?> activityClass) {
        Intent intent = new Intent(HomeActivity.this, activityClass);
        intent.putExtra("paperTitle", "Attention Is All You Need");
        intent.putExtra("paperAuthor", "Vaswani et al. · 2017");
        startActivity(intent);
    }
}