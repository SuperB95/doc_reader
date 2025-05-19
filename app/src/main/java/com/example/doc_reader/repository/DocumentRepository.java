package com.example.doc_reader.repository;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.example.doc_reader.model.Document;
import java.util.ArrayList;
import java.util.List;

public class DocumentRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference documentsRef = db.collection("documents");

    public void addDocument(Document doc) {
        documentsRef.add(doc)
                .addOnSuccessListener(documentReference -> {
                    Log.d("Firestore", "Dokumentum mentve: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Hiba történt a mentés során", e);
                });
    }

    public void getUserDocuments(OnDocumentsLoadedListener listener) {
        documentsRef
                .whereEqualTo("ownerId", FirebaseAuth.getInstance().getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Document> documentList = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Document doc = snapshot.toObject(Document.class);
                        documentList.add(doc);
                    }
                    listener.onDocumentsLoaded(documentList);
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Lekérdezési hiba", e);
                });
    }

    public interface OnDocumentsLoadedListener {
        void onDocumentsLoaded(List<Document> documents);
    }
}