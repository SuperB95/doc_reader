package com.example.doc_reader;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FeedbackActivity extends AppCompatActivity {

    private TextView documentTitle;
    private EditText commentInput;
    private RatingBar ratingBar;
    private Button saveButton;

    private String fileName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        documentTitle = findViewById(R.id.document_title);
        commentInput = findViewById(R.id.comment_input);
        ratingBar = findViewById(R.id.rating_bar);
        saveButton = findViewById(R.id.save_feedback_button);

        fileName = getIntent().getStringExtra("file_name");
        documentTitle.setText("Dokumentum: " + fileName);

        loadExistingFeedback();

        saveButton.setOnClickListener(v -> saveFeedback());
    }

    private void loadExistingFeedback() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("documents")
                .whereEqualTo("file_name", fileName)
                .whereEqualTo("user_id", userId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        Map<String, Object> data = snapshot.getDocuments().get(0).getData();
                        if (data != null) {
                            if (data.containsKey("comment")) {
                                commentInput.setText((String) data.get("comment"));
                            }
                            if (data.containsKey("rating")) {
                                Long rating = (Long) data.get("rating");
                                ratingBar.setRating(rating != null ? rating.intValue() : 0);
                            }
                        }
                    }
                });
    }

    private void saveFeedback() {
        String comment = commentInput.getText().toString().trim();
        int rating = (int) ratingBar.getRating();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("documents")
                .whereEqualTo("file_name", fileName)
                .whereEqualTo("user_id", userId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        String docId = snapshot.getDocuments().get(0).getId();

                        Map<String, Object> updateData = new HashMap<>();
                        updateData.put("comment", comment);
                        updateData.put("rating", rating);
                        updateData.put("last_updated", System.currentTimeMillis());

                        db.collection("documents").document(docId)
                                .update(updateData)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Visszajelzés mentve!", Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_OK);
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Mentési hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }
}
