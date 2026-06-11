package com.example.scholarmind;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.scholarmind.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;

/**
 * ProfileActivity
 * ────────────────
 * Displays the signed-in user's profile loaded from Firestore,
 * and provides a Sign Out button.
 *
 * Layout IDs expected in activity_profile.xml:
 *   btnBack         – FrameLayout
 *   tvFullName      – TextView   (user's full name)
 *   tvEmail         – TextView   (user's email)
 *   tvUserId        – TextView   (shortened UID)
 *   tvPapersCount   – TextView   (number of papers uploaded)
 *   tvLoading       – TextView   (loading indicator)
 *   btnSignOut      – TextView   (sign out button)
 */
public class ProfileActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────
    private FrameLayout btnBack;
    private TextView    tvFullName;
    private TextView    tvEmail;
    private TextView    tvUserId;
    private TextView    tvPapersCount;
    private TextView    tvLoading;
    private TextView    btnSignOut;

    // ── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        bindViews();
        loadProfile();
        loadPaperCount();
    }

    // ── Binding ────────────────────────────────────────────────────────────
    private void bindViews() {
        btnBack       = findViewById(R.id.btnBack);
        tvFullName    = findViewById(R.id.tvFullName);
        tvEmail       = findViewById(R.id.tvEmail);
        tvUserId      = findViewById(R.id.tvUserId);
        tvPapersCount = findViewById(R.id.tvPapersCount);
        tvLoading     = findViewById(R.id.tvLoading);
        btnSignOut    = findViewById(R.id.btnSignOut);

        btnBack.setOnClickListener(v -> finish());

        btnSignOut.setOnClickListener(v -> signOut());
    }

    // ── Load profile from Firestore ────────────────────────────────────────
    private void loadProfile() {
        String userId = FirebaseManager.getInstance().getCurrentUserId();
        if (userId == null) {
            tvFullName.setText("Guest User");
            tvEmail.setText("Not signed in");
            tvUserId.setText("");
            tvLoading.setVisibility(View.GONE);
            return;
        }

        // Show short UID
        tvUserId.setText("ID: " + userId.substring(0, Math.min(8, userId.length())) + "...");

        // Show email from FirebaseAuth directly
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            tvEmail.setText(email != null ? email : "No email");
        }

        FirebaseManager.getInstance().getUserProfile(
                userId,
                documentSnapshot -> {
                    tvLoading.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        tvFullName.setText(fullName != null && !fullName.isEmpty()
                                ? fullName : "Scholar");
                    } else {
                        tvFullName.setText("Scholar");
                    }
                },
                e -> {
                    tvLoading.setVisibility(View.GONE);
                    tvFullName.setText("Scholar");
                    Toast.makeText(this, "Could not load profile", Toast.LENGTH_SHORT).show();
                }
        );
    }

    // ── Load paper count ───────────────────────────────────────────────────
    private void loadPaperCount() {
        FirebaseManager.getInstance().getAllPapers(
                papers -> tvPapersCount.setText(papers.size() + " papers uploaded"),
                e     -> tvPapersCount.setText("— papers uploaded")
        );
    }

    // ── Sign out ───────────────────────────────────────────────────────────
    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
        // Navigate back to login / splash
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}