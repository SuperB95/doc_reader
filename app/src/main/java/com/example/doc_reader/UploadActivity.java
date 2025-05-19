package com.example.doc_reader;

import com.example.doc_reader.model.Document;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class UploadActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        Button uploadButton = findViewById(R.id.upload_button);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();

                        FirebaseStorage storage = FirebaseStorage.getInstance();
                        StorageReference storageRef = storage.getReference().child("documents/" + System.currentTimeMillis());

                        storageRef.putFile(fileUri)
                                .addOnSuccessListener(taskSnapshot ->
                                        taskSnapshot.getStorage().getDownloadUrl()
                                                .addOnSuccessListener(uri ->
                                                        saveDocumentToFirestore(uri.toString())
                                                )
                                                .addOnFailureListener(e ->
                                                        Toast.makeText(this, "Hiba a letöltési URL lekérésekor: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                                )
                                )
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Hiba a feltöltéskor: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                );

                    }
                }
        );

        // Feltöltés gomb eseménykezelése
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

//    private void requestPermission() {
//        ActivityCompat.requestPermissions(this,
//                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
//                PERMISSION_REQUEST_CODE);
//    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+ (API 33)
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO
                    },
                    PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
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

    private void saveDocumentToFirestore(String downloadUrl) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Nem vagy bejelentkezve!", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("documents")
                .add(new Document("Feltöltött dokumentum", "Feltöltött leírás", downloadUrl, System.currentTimeMillis(), FirebaseAuth.getInstance().getUid()))
                .addOnSuccessListener(documentReference ->
                        Toast.makeText(this, "Dokumentum sikeresen mentve!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Mentési hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
