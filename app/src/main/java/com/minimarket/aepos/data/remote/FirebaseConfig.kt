package com.minimarket.aepos.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage

object FirebaseConfig {

    const val API_KEY = "AIzaSyD0GPWoxJAxMvK6u8ZE1F24CXxJRYvdoxo"
    const val PROJECT_ID = "minimarket-flor-8d7f9"
    const val APPLICATION_ID = "1:519884713211:web:294f2cc23a85f0915cd45e"
    const val STORAGE_BUCKET = "minimarket-flor-8d7f9.firebasestorage.app"

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return

        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(API_KEY)
                    .setApplicationId(APPLICATION_ID)
                    .setProjectId(PROJECT_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .build()

                FirebaseApp.initializeApp(context, options)
            }

            // Habilitar persistencia offline y rendimiento óptimo en Firestore
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            Firebase.firestore.firestoreSettings = settings

            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val firestore: FirebaseFirestore
        get() = Firebase.firestore

    val auth: FirebaseAuth
        get() = Firebase.auth

    val storage: FirebaseStorage
        get() = Firebase.storage
}
