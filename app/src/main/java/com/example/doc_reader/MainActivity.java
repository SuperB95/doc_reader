package com.example.doc_reader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private LinearLayout documentContainer;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        documentContainer = findViewById(R.id.document_container);
        Button addDocumentButton = findViewById(R.id.add_document_button);

        db = FirebaseFirestore.getInstance();

        loadDocuments();

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        String fileName = "imported_" + System.currentTimeMillis() + ".pdf";

                        try (InputStream in = getContentResolver().openInputStream(fileUri);
                             FileOutputStream out = new FileOutputStream(new File(getFilesDir(), fileName))) {

                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = in.read(buffer)) > 0) {
                                out.write(buffer, 0, length);
                            }

                            saveDocumentMetaToFirestore(fileName);
                            Toast.makeText(this, "PDF sikeresen importálva!", Toast.LENGTH_SHORT).show();
                            loadDocuments();

                        } catch (Exception e) {
                            Toast.makeText(this, "Hiba a fájl importálásakor: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        addDocumentButton.setOnClickListener(v -> {
            if (checkPermission()) {
                openPdfPicker();
            } else {
                requestPermission();
            }
        });
    }

    private void loadDocuments() {
        documentContainer.removeAllViews();

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("documents")
                .whereEqualTo("user_id", currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot) {
                        String fileName = doc.getString("file_name");
                        String displayNameRaw = doc.getString("display_name");
                        String displayNameFinal = (displayNameRaw != null) ? displayNameRaw : fileName;
                        String category = doc.getString("category");
                        if (category == null) category = "Nincs kategória";

                        File file = new File(getFilesDir(), fileName);

                        View docView = LayoutInflater.from(this).inflate(R.layout.document_item, documentContainer, false);

                        TextView nameView = docView.findViewById(R.id.document_name);
                        TextView categoryView = docView.findViewById(R.id.document_category);
                        RatingBar ratingBar = docView.findViewById(R.id.document_rating_bar);
                        Button feedbackButton = docView.findViewById(R.id.feedback_button);
                        Button categoryButton = docView.findViewById(R.id.category_button);
                        ImageButton deleteButton = docView.findViewById(R.id.delete_button);
                        ImageButton renameButton = docView.findViewById(R.id.rename_button);

                        nameView.setText("📄 " + displayNameFinal);
                        categoryView.setText("Kategória: " + category);

                        if (doc.contains("rating")) {
                            Long rating = doc.getLong("rating");
                            ratingBar.setRating(rating != null ? rating.intValue() : 0);
                        } else {
                            ratingBar.setRating(0);
                        }

                        // Teljes kártya kattintható a dokumentum megnyitására
                        docView.setOnClickListener(v -> openPdf(file));

                        feedbackButton.setOnClickListener(v -> {
                            Intent intent = new Intent(MainActivity.this, FeedbackActivity.class);
                            intent.putExtra("file_name", fileName);
                            startActivityForResult(intent, 100);
                        });

                        categoryButton.setOnClickListener(v -> showCategoryPopup(fileName));

                        deleteButton.setOnClickListener(v -> deleteDocument(fileName, file));

                        renameButton.setOnClickListener(v -> showRenamePopup(fileName, displayNameFinal));

                        documentContainer.addView(docView);
                    }
                });
    }






    private void showNewCategoryDialog(String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Új kategória hozzáadása");

        final TextView input = new TextView(this);
        input.setPadding(32, 32, 32, 32);
        builder.setView(input);

        builder.setPositiveButton("Mentés", (dialog, which) -> {
            String newCategory = input.getText().toString();
            if (!newCategory.isEmpty()) {
                Map<String, Object> category = new HashMap<>();
                category.put("name", newCategory);
                category.put("created_at", System.currentTimeMillis());

                db.collection("categories").add(category).addOnSuccessListener(docRef -> {
                    Toast.makeText(this, "Kategória hozzáadva.", Toast.LENGTH_SHORT).show();
                    assignCategoryToDocument(fileName, newCategory);
                });
            }
        });

        builder.setNegativeButton("Mégse", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void assignCategoryToDocument(String fileName, String category) {
        db.collection("documents")
                .whereEqualTo("file_name", fileName)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    if (!querySnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshots.getDocuments()) {
                            doc.getReference().update("category", category);
                        }
                    }
                });
    }

    private void openPdf(File pdfFile) {
        Uri contentUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                pdfFile
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(contentUri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Nincs alkalmazás a PDF megnyitásához.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        filePickerLauncher.launch(intent);
    }

    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO},
                    PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void saveDocumentMetaToFirestore(String fileName) {
        Map<String, Object> docMeta = new HashMap<>();
        docMeta.put("file_name", fileName);
        docMeta.put("import_time", System.currentTimeMillis());
        docMeta.put("last_opened", null);
        docMeta.put("category", null);
        docMeta.put("user_id", FirebaseAuth.getInstance().getCurrentUser().getUid());
        db.collection("documents")
                .add(docMeta)
                .addOnSuccessListener(documentReference ->
                        Toast.makeText(this, "Metaadat mentve Firestore-ba!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Firestore mentési hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openPdfPicker();
            } else {
                Toast.makeText(this, "Engedély szükséges a fájlok kiválasztásához.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            loadDocuments();
        }
    }

    private void showCategoryPopup(String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Kategória kiválasztása");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        Spinner categorySpinner = new Spinner(this);
        EditText newCategoryInput = new EditText(this);
        newCategoryInput.setHint("Új kategória neve");

        layout.addView(categorySpinner);
        layout.addView(newCategoryInput);

        builder.setView(layout);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("categories").get().addOnSuccessListener(snapshot -> {
            List<String> categories = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot) {
                categories.add(doc.getString("name"));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
            categorySpinner.setAdapter(adapter);
        });

        builder.setPositiveButton("Mentés", (dialog, which) -> {
            String selectedCategory = (String) categorySpinner.getSelectedItem();
            String newCategory = newCategoryInput.getText().toString().trim();

            String finalCategory = !newCategory.isEmpty() ? newCategory : selectedCategory;

            if (finalCategory == null || finalCategory.isEmpty()) {
                Toast.makeText(this, "Kérlek adj meg vagy válassz kategóriát!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newCategory.isEmpty()) {
                Map<String, Object> catData = new HashMap<>();
                catData.put("name", newCategory);
                db.collection("categories").add(catData);
            }

            updateDocumentCategory(fileName, finalCategory);
        });

        builder.setNegativeButton("Mégse", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateDocumentCategory(String fileName, String category) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("documents")
                .whereEqualTo("file_name", fileName)
                .whereEqualTo("user_id", userId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        String docId = snapshot.getDocuments().get(0).getId();
                        db.collection("documents").document(docId)
                                .update("category", category)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Kategória frissítve!", Toast.LENGTH_SHORT).show();
                                    loadDocuments(); // Frissítjük a listát
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }
    private void showRenamePopup(String fileName, String currentName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Dokumentum átnevezése");

        final EditText input = new EditText(this);
        input.setText(currentName);

        builder.setView(input);

        builder.setPositiveButton("Mentés", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                db.collection("documents")
                        .whereEqualTo("file_name", fileName)
                        .whereEqualTo("user_id", userId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            if (!snapshot.isEmpty()) {
                                String docId = snapshot.getDocuments().get(0).getId();
                                db.collection("documents").document(docId)
                                        .update("display_name", newName)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Név frissítve!", Toast.LENGTH_SHORT).show();
                                            loadDocuments();
                                        });
                            }
                        });
            }
        });

        builder.setNegativeButton("Mégse", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void deleteDocument(String fileName, File file) {
        new AlertDialog.Builder(this)
                .setTitle("Biztosan törlöd?")
                .setMessage("Ez a művelet nem visszavonható!")
                .setPositiveButton("Igen, törlöm", (dialog, which) -> {
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    db.collection("documents")
                            .whereEqualTo("file_name", fileName)
                            .whereEqualTo("user_id", userId)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (!snapshot.isEmpty()) {
                                    String docId = snapshot.getDocuments().get(0).getId();

                                    // Firestore törlés
                                    db.collection("documents").document(docId).delete();

                                    // Lokális fájl törlés
                                    if (file.exists()) {
                                        if (file.delete()) {
                                            Toast.makeText(this, "Dokumentum törölve!", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(this, "Hiba a fájl törlésekor!", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    loadDocuments();
                                }
                            });
                })
                .setNegativeButton("Mégse", (dialog, which) -> dialog.dismiss())
                .show();
    }


}
