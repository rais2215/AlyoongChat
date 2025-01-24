package com.esaunggul.alyoongchat.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.esaunggul.alyoongchat.utilities.Constants
import com.esaunggul.alyoongchat.utilities.PreferenceManager
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

open class BaseActivity : AppCompatActivity() {

        private var documentReference: DocumentReference? = null

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferenceManager = PreferenceManager(applicationContext)
        val database = FirebaseFirestore.getInstance()

        val userId = preferenceManager.getString(Constants.KEY_USER_ID)
        if (!userId.isNullOrEmpty()) { // Cek null atau kosong
        documentReference = database.collection(Constants.KEY_COLLECTION_USERS)
        .document(userId)
        }
        }

        override fun onPause() {
        super.onPause()
        documentReference?.update(Constants.KEY_AVAILABILITY, 0)
        }

        override fun onResume() {
        super.onResume()
        documentReference?.update(Constants.KEY_AVAILABILITY, 1)
        }
        }
