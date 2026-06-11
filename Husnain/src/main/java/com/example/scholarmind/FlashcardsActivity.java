package com.example.scholarmind;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.scholarmind.FirebaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FlashcardsActivity
 * ───────────────────
 * Displays study flashcards (term on front, definition on back).
 * Tap a card to flip it. Navigate with Previous / Next buttons.
 *
 * Layout IDs expected in activity_flashcards.xml:
 *   btnBack         – FrameLayout
 *   tvCardCounter   – TextView   ("Card 1 of 8")
 *   cardFlip        – CardView   (the flashcard — tap to flip)
 *   tvCardLabel     – TextView   ("TERM" or "DEFINITION")
 *   tvCardContent   – TextView   (the actual text)
 *   btnPrev         – TextView   (Previous button)
 *   btnNext         – TextView   (Next button)
 *   tvProgress      – TextView   (e.g. "3 / 8 studied")
 */
public class FlashcardsActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────
    private FrameLayout btnBack;
    private TextView    tvCardCounter;
    private CardView    cardFlip;
    private TextView    tvCardLabel;
    private TextView    tvCardContent;
    private TextView    btnPrev;
    private TextView    btnNext;
    private TextView    tvProgress;

    // ── State ──────────────────────────────────────────────────────────────
    private int     currentIndex = 0;
    private boolean isShowingFront = true;

    // Each flashcard: index 0 = term, index 1 = definition
    private final List<String[]> flashcards = new ArrayList<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcards);

        bindViews();
        loadDefaultCards();
        displayCard();
    }

    // ── Binding ────────────────────────────────────────────────────────────
    private void bindViews() {
        btnBack       = findViewById(R.id.btnBack);
        tvCardCounter = findViewById(R.id.tvCardCounter);
        cardFlip      = findViewById(R.id.cardFlip);
        tvCardLabel   = findViewById(R.id.tvCardLabel);
        tvCardContent = findViewById(R.id.tvCardContent);
        btnPrev       = findViewById(R.id.btnPrev);
        btnNext       = findViewById(R.id.btnNext);
        tvProgress    = findViewById(R.id.tvProgress);

        btnBack.setOnClickListener(v -> finish());

        cardFlip.setOnClickListener(v -> flipCard());

        btnPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                isShowingFront = true;
                displayCard();
            } else {
                Toast.makeText(this, "This is the first card", Toast.LENGTH_SHORT).show();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentIndex < flashcards.size() - 1) {
                currentIndex++;
                isShowingFront = true;
                displayCard();
            } else {
                Toast.makeText(this, "You've reached the last card! 🎉", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Default flashcard data ─────────────────────────────────────────────
    private void loadDefaultCards() {
        flashcards.add(new String[]{
                "Transformer",
                "A neural network architecture based entirely on self-attention mechanisms, introduced by Vaswani et al. (2017). It replaced RNNs for sequence tasks."
        });
        flashcards.add(new String[]{
                "Self-Attention",
                "A mechanism that allows each token in a sequence to compute a weighted relationship with every other token. Formula: Attention(Q,K,V) = softmax(QKᵀ/√d_k)·V"
        });
        flashcards.add(new String[]{
                "Multi-Head Attention",
                "Running self-attention h times in parallel with different learned projections, then concatenating results. Allows the model to attend to information from different positions."
        });
        flashcards.add(new String[]{
                "Encoder",
                "The part of the Transformer that reads and encodes the input sequence into continuous representations. Consists of N=6 identical layers."
        });
        flashcards.add(new String[]{
                "Decoder",
                "The part of the Transformer that generates the output sequence one token at a time, attending to both its own output and the encoder output."
        });
        flashcards.add(new String[]{
                "Positional Encoding",
                "Sine and cosine functions added to input embeddings to give the model information about the order of tokens, since attention has no inherent notion of sequence order."
        });
        flashcards.add(new String[]{
                "BLEU Score",
                "Bilingual Evaluation Understudy — a metric for evaluating machine translation quality by comparing output to reference translations. Higher is better (max 100)."
        });
        flashcards.add(new String[]{
                "Feed-Forward Network",
                "A position-wise fully connected layer applied identically to each token after attention. In the Transformer: two linear layers with ReLU, d_ff = 2048."
        });
    }

    // ── Display ────────────────────────────────────────────────────────────
    private void displayCard() {
        if (flashcards.isEmpty()) return;

        String[] card = flashcards.get(currentIndex);
        tvCardCounter.setText("Card " + (currentIndex + 1) + " of " + flashcards.size());
        tvProgress.setText((currentIndex + 1) + " / " + flashcards.size() + " studied");

        if (isShowingFront) {
            tvCardLabel.setText("TERM");
            tvCardContent.setText(card[0]);
            cardFlip.setCardBackgroundColor(0xFF1A1A2E);
            tvCardLabel.setTextColor(0xFF4A90D9);
            tvCardContent.setTextColor(0xFFFFFFFF);
        } else {
            tvCardLabel.setText("DEFINITION");
            tvCardContent.setText(card[1]);
            cardFlip.setCardBackgroundColor(0xFFFFFFFF);
            tvCardLabel.setTextColor(0xFF4A90D9);
            tvCardContent.setTextColor(0xFF1A1A2E);
        }

        btnPrev.setAlpha(currentIndex == 0 ? 0.4f : 1f);
        btnNext.setAlpha(currentIndex == flashcards.size() - 1 ? 0.4f : 1f);
    }

    // ── Flip ───────────────────────────────────────────────────────────────
    private void flipCard() {
        cardFlip.animate()
                .rotationY(90f)
                .setDuration(150)
                .withEndAction(() -> {
                    isShowingFront = !isShowingFront;
                    displayCard();
                    cardFlip.setRotationY(-90f);
                    cardFlip.animate()
                            .rotationY(0f)
                            .setDuration(150)
                            .start();
                })
                .start();
    }
}