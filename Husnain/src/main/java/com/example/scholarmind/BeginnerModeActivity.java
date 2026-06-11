package com.example.scholarmind;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

/**
 * BeginnerModeActivity
 * ─────────────────────
 * Shows a set of simple pre-built questions the user can tap to get
 * easy-to-understand answers about the uploaded paper.
 *
 * Layout IDs expected in activity_beginner_mode.xml:
 *   btnBack           – FrameLayout  (top-left back arrow)
 *   tvPaperTitle      – TextView     (paper name in header)
 *   tvPaperAuthor     – TextView     (author · year in header)
 *   scrollQuestions   – ScrollView   (wraps llQuestions)
 *   llQuestions       – LinearLayout (question chips added here)
 *   cardAnswer        – CardView     (answer card, starts GONE)
 *   tvAnswerQuestion  – TextView     (repeats the tapped question)
 *   tvAnswerBody      – TextView     (the answer text)
 *   tvLoading         – TextView     (visible while fetching)
 */
public class BeginnerModeActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────
    private FrameLayout  btnBack;
    private TextView     tvPaperTitle;
    private TextView     tvPaperAuthor;
    private LinearLayout llQuestions;
    private CardView     cardAnswer;
    private TextView     tvAnswerQuestion;
    private TextView     tvAnswerBody;
    private TextView     tvLoading;

    // ── State ──────────────────────────────────────────────────────────────
    private String paperId;
    private String paperTitle;
    private String paperAuthor;

    // Pre-built beginner questions shown as tappable chips
    private static final String[] BEGINNER_QUESTIONS = {
            "What is this paper about?",
            "Who wrote this paper and when?",
            "What problem does this paper solve?",
            "What are the main findings?",
            "What methods were used?",
            "Why is this paper important?",
            "What are the key terms I should know?",
            "What are the limitations of this study?"
    };

    // ── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beginner_mode);

        // Read extras passed from the previous screen
        paperId     = getIntent().getStringExtra("paperId");
        paperTitle  = getIntent().getStringExtra("paperTitle");
        paperAuthor = getIntent().getStringExtra("paperAuthor");

        bindViews();
        populateHeader();
        buildQuestionChips();
        loadPaperFromFirestore();
    }

    // ── View binding ───────────────────────────────────────────────────────
    private void bindViews() {
        btnBack          = findViewById(R.id.btnBack);
        tvPaperTitle     = findViewById(R.id.tvPaperTitle);
        tvPaperAuthor    = findViewById(R.id.tvPaperAuthor);
        llQuestions      = findViewById(R.id.llQuestions);
        cardAnswer       = findViewById(R.id.cardAnswer);
        tvAnswerQuestion = findViewById(R.id.tvAnswerQuestion);
        tvAnswerBody     = findViewById(R.id.tvAnswerBody);
        tvLoading        = findViewById(R.id.tvLoading);

        btnBack.setOnClickListener(v -> finish());
        cardAnswer.setVisibility(View.GONE);
        tvLoading.setVisibility(View.GONE);
    }

    // ── Header ─────────────────────────────────────────────────────────────
    private void populateHeader() {
        if (paperTitle  != null) tvPaperTitle.setText(paperTitle);
        if (paperAuthor != null) tvPaperAuthor.setText(paperAuthor);
    }

    // ── Firestore READ ─────────────────────────────────────────────────────
    private void loadPaperFromFirestore() {
        if (paperId == null) return;

        FirebaseManager.getInstance().getPaper(
                paperId,
                data -> {
                    // Refresh header with Firestore data (in case extras were missing)
                    String title  = (String) data.get("title");
                    String author = (String) data.get("author");
                    String year   = (String) data.get("year");
                    if (title  != null) tvPaperTitle.setText(title);
                    if (author != null && year != null)
                        tvPaperAuthor.setText(author + " · " + year);
                },
                e -> Toast.makeText(this,
                        "Could not load paper: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show()
        );
    }

    // ── Question chips ─────────────────────────────────────────────────────
    private void buildQuestionChips() {
        for (String question : BEGINNER_QUESTIONS) {
            TextView chip = new TextView(this);
            chip.setText(question);
            chip.setPadding(40, 28, 40, 28);
            chip.setTextSize(15f);
            chip.setTextColor(0xFF1A1A2E);
            chip.setBackground(getDrawable(R.drawable.bg_question_chip)); // rounded rect drawable
            chip.setClickable(true);
            chip.setFocusable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 20);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> onQuestionTapped(question));
            llQuestions.addView(chip);
        }
    }

    // ── Answer display ─────────────────────────────────────────────────────
    private void onQuestionTapped(String question) {
        tvAnswerQuestion.setText(question);
        tvAnswerBody.setText("");
        cardAnswer.setVisibility(View.VISIBLE);
        tvLoading.setVisibility(View.VISIBLE);

        // Simulate fetching a beginner-friendly answer
        // Replace this block with a real API / Firestore call as needed
        cardAnswer.postDelayed(() -> {
            tvLoading.setVisibility(View.GONE);
            tvAnswerBody.setText(getSimpleAnswer(question));
        }, 1000);
    }

    /**
     * Returns a plain-language answer for each pre-built question.
     * Swap this method body for a real Claude API / Firestore lookup
     * when your backend is ready.
     */
    private String getSimpleAnswer(String question) {
        switch (question) {
            case "What is this paper about?":
                return "This paper introduces the Transformer — a new kind of model that understands language "
                        + "by learning which words are most important to each other, without reading them one by one.";
            case "Who wrote this paper and when?":
                return "It was written by Vaswani and a team of researchers at Google in 2017.";
            case "What problem does this paper solve?":
                return "Before this paper, language models were slow because they processed words one at a time. "
                        + "The Transformer processes all words at once, making it much faster and more accurate.";
            case "What are the main findings?":
                return "The Transformer outperformed all previous models on language translation tasks and trained "
                        + "significantly faster using standard computer hardware.";
            case "What methods were used?":
                return "The authors used a technique called 'self-attention' which lets every word in a sentence "
                        + "look at every other word and decide which ones matter most.";
            case "Why is this paper important?":
                return "This paper is the foundation of modern AI language models like ChatGPT, Google Bard, "
                        + "and many others. It changed how the entire field works.";
            case "What are the key terms I should know?":
                return "• Attention – how the model focuses on important words\n"
                        + "• Encoder – reads and understands the input\n"
                        + "• Decoder – produces the output (e.g. a translation)\n"
                        + "• Token – a word or word-piece the model processes";
            case "What are the limitations of this study?":
                return "The model needs a lot of data and computing power to train. "
                        + "It can also struggle with very long documents because attention scales quadratically.";
            default:
                return "Answer not available for this question yet.";
        }
    }
}