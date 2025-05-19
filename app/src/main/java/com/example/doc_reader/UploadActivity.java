package com.example.doc_reader;

import com.example.doc_reader.model.Document;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class UploadActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final int PICK_FILE_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        Button uploadButton = findViewById(R.id.upload_button);

        uploadButton.setOnClickListener(v -> {
            if (checkPermission()) {
                openFilePicker();
            } else {
                requestPermission();
            }
        });
    }

    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this, "Engedély szükséges a fájlok kiválasztásához.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri fileUri = data.getData();

            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReference().child("documents/" + System.currentTimeMillis());

            storageRef.putFile(fileUri)
                    .addOnSuccessListener(taskSnapshot ->
                            storageRef.getDownloadUrl().addOnSuccessListener(uri ->
                                    saveDocumentToFirestore(uri.toString())
                            )
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Hiba a feltöltéskor: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        }
    }

    private void saveDocumentToFirestore(String downloadUrl) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Egyszerűsített példaként csak a linket mentjük
        db.collection("documents")
                .add(new Document("Feltöltött dokumentum", "Feltöltött leírás", downloadUrl, System.currentTimeMillis(), FirebaseAuth.getInstance().getUid()))
                .addOnSuccessListener(documentReference ->
                        Toast.makeText(this, "Dokumentum sikeresen mentve!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Mentési hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}