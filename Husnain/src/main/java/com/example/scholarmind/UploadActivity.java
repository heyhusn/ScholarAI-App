package com.example.scholarmind;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.scholarmind.utils.FirebaseManager;

public class UploadActivity extends AppCompatActivity {
    private FrameLayout btnBack;
    private TextView btnBrowse;
    private TextView btnUploadAnalyze;
    private boolean isFileSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        btnBack = findViewById(R.id.btnBack);
        btnBrowse = findViewById(R.id.btnBrowse);
        btnUploadAnalyze = findViewById(R.id.btnUploadAnalyze);

        btnBack.setOnClickListener(v -> finish());

        btnBrowse.setOnClickListener(v -> {
            isFileSelected = true;
            Toast.makeText(this, "Selected: transformer_architecture.pdf (3.2 MB)", Toast.LENGTH_SHORT).show();
        });

        btnUploadAnalyze.setOnClickListener(v -> {
            if (!isFileSelected) {
                Toast.makeText(this, "Please select/browse a PDF document first", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create Paper record in Firestore
            btnUploadAnalyze.setEnabled(false);
            FirebaseManager.getInstance().addPaper(
                    "transformer_architecture.pdf",
                    "Vaswani et al.",
                    "2017",
                    "3.2 MB",
                    "Technical",
                    documentReference -> {
                        // Success
                        Intent intent = new Intent(UploadActivity.this, ProcessingActivity.class);
                        intent.putExtra("paperId", documentReference.getId());
                        intent.putExtra("paperTitle", "transformer_architecture.pdf");
                        intent.putExtra("paperAuthor", "Vaswani et al. · 2017");
                        startActivity(intent);
                        finish();
                    },
                    e -> {
                        // Fail
                        btnUploadAnalyze.setEnabled(true);
                        Toast.makeText(UploadActivity.this, "Firestore Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
            );
        });
    }
}
