package com.example.SlipStream.repository;

import com.example.SlipStream.model.User;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ExecutionException;

@Repository // Mark this as a Spring bean
public class FirebaseUserRepository implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseUserRepository.class);
    private static final String COLLECTION_NAME = "Users";

    @Override
    public User getUserByEmail(String email) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        // Assuming email is used as the document ID for simplicity
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(email);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            logger.debug("User found in Firestore: {}", email);
            return document.toObject(User.class);
        } else {
            logger.debug("User not found in Firestore: {}", email);
            return null;
        }
    }

    @Override
    public void saveUser(User user) throws ExecutionException, InterruptedException {
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            logger.error("Cannot save user with null or empty email.");
            throw new IllegalArgumentException("User email cannot be null or empty.");
        }
        Firestore db = FirestoreClient.getFirestore();
        // Use email as the document ID
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(user.getEmail());
        ApiFuture<WriteResult> future = docRef.set(user); // Overwrites if exists, creates if not
        future.get(); // Wait for write to complete
        logger.info("User saved/updated successfully in Firestore: {}", user.getEmail());
    }
}
