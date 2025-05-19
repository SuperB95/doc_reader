package com.example.doc_reader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
        db.collection("documents").whereEqualTo("user_id", currentUserId).get().addOnSuccessListener(querySnapshot -> {
            Map<String, List<String>> groupedDocs = new HashMap<>();

            for (DocumentSnapshot doc : querySnapshot) {
                String fileName = doc.getString("file_name");
                String category = doc.getString("category");
                if (category == null) category = "Nincs kategória";

                if (!groupedDocs.containsKey(category)) {
                    groupedDocs.put(category, new ArrayList<>());
                }
                groupedDocs.get(category).add(fileName);
            }

            // Dokumentumok megjelenítése kategóriák szerint
            for (Map.Entry<String, List<String>> entry : groupedDocs.entrySet()) {
                String category = entry.getKey();

                // Kategória címsor
                TextView categoryView = new TextView(this);
                categoryView.setText("📂 " + category);
                categoryView.setTextSize(20);
                categoryView.setPadding(16, 32, 16, 16);
                categoryView.setTextColor(Color.BLACK);
                categoryView.setTypeface(null, Typeface.BOLD);
                documentContainer.addView(categoryView);

                for (String fileName : entry.getValue()) {
                    File file = new File(getFilesDir(), fileName);
                    if (file.exists()) {
                        TextView docView = new TextView(this);
                        docView.setText("📄 " + fileName);
                        docView.setTextSize(18);
                        docView.setPadding(32, 16, 16, 16);
                        docView.setTextColor(Color.DKGRAY);
                        docView.setBackgroundResource(R.drawable.document_item_bg);
                        docView.setOnClickListener(v -> openPdf(file));
                        docView.setOnLongClickListener(v -> {
                            showCategoryPopup(file.getName());
                            return true;
                        });
                        documentContainer.addView(docView);
                    }
                }
            }
        });
    }

    private void showCategoryPopup(String fileName) {
        db.collection("categories").get().addOnSuccessListener(querySnapshot -> {
            String[] categories = new String[querySnapshot.size()];
            int i = 0;
            for (DocumentSnapshot doc : querySnapshot) {
                categories[i++] = doc.getString("name");
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Válassz kategóriát")
                    .setItems(categories, (dialog, which) -> assignCategoryToDocument(fileName, categories[which]))
                    .setPositiveButton("Új kategória", (dialog, which) -> showNewCategoryDialog(fileName))
                    .show();
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
}
