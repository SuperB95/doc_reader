package com.example.doc_reader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private LinearLayout documentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        documentContainer = findViewById(R.id.document_container);
        Button addDocumentButton = findViewById(R.id.add_document_button);

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

                            Toast.makeText(this, "PDF sikeresen importálva: " + fileName, Toast.LENGTH_SHORT).show();
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
        File[] files = getFilesDir().listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".pdf")) {
                    TextView docView = new TextView(this);
                    docView.setText(file.getName());
                    docView.setTextSize(18);
                    docView.setPadding(16, 16, 16, 16);
                    docView.setOnClickListener(v -> openPdf(file));
                    documentContainer.addView(docView);
                }
            }
        }
    }

//    private void openPdf(File pdfFile) {
//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        intent.setDataAndType(Uri.fromFile(pdfFile), "application/pdf");
//        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
//        startActivity(intent);
//    }
    private void openPdf(File pdfFile) {
        Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(contentUri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        updateDocumentLastOpened(pdfFile.getName());
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


    private void saveDocumentMetaToFirestore(String fileName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> docMeta = new HashMap<>();
        docMeta.put("file_name", fileName);
        docMeta.put("import_time", System.currentTimeMillis());
        docMeta.put("last_opened", null);

        db.collection("documents")
                .add(docMeta)
                .addOnSuccessListener(documentReference ->
                        Toast.makeText(this, "Metaadat mentve Firestore-ba.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Metaadat mentési hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }


    private void updateDocumentLastOpened(String fileName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("documents")
                .whereEqualTo("file_name", fileName)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            doc.getReference().update("last_opened", System.currentTimeMillis());
                        }
                    }
                });
    }
}
