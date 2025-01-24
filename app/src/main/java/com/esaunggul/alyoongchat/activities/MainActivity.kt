package com.esaunggul.alyoongchat.activities

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.esaunggul.alyoongchat.adapters.RecentConversationsAdapter
import com.esaunggul.alyoongchat.databinding.ActivityMainBinding
import com.esaunggul.alyoongchat.listeners.ConversionListener
import com.esaunggul.alyoongchat.models.ChatMessage
import com.esaunggul.alyoongchat.models.User
import com.esaunggul.alyoongchat.utilities.Constants
import com.esaunggul.alyoongchat.utilities.PreferenceManager
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : BaseActivity(), ConversionListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager
    private val chatMessages: MutableList<ChatMessage> = mutableListOf()
    private lateinit var adapter: RecentConversationsAdapter
    private lateinit var database: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            preferenceManager = PreferenceManager(applicationContext)

            init()
            loadUserDetails()
            getToken()
            setListeners()
            listenConversations()
        } catch (e: Exception) {
            Log.e("MainActivity", "Initialization error", e)
            showToast("An error occurred while starting the app")
            finish()
        }
    }

    private fun init() {
        adapter = RecentConversationsAdapter(chatMessages, this)
        binding.conversationRecyclerView.apply {
            adapter = this@MainActivity.adapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        database = FirebaseFirestore.getInstance()
    }

    private fun setListeners() {
        binding.imageSignOut.setOnClickListener { signOut() }
        binding.fabNewChat.setOnClickListener {
            startActivity(Intent(applicationContext, UsersActivity::class.java))
        }
    }

    private fun loadUserDetails() {
        binding.textName.text = preferenceManager.getString(Constants.KEY_NAME) ?: "User"
        val imageString = preferenceManager.getString(Constants.KEY_IMAGE)
        if (!imageString.isNullOrEmpty()) {
            try {
                val bytes = Base64.decode(imageString, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                binding.imageProfile.setImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading profile image", e)
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun listenConversations() {
        val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID)
        if (currentUserId.isNullOrEmpty()) {
            showToast("User ID not found")
            return
        }

        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_SENDER_ID, currentUserId)
            .addSnapshotListener(eventListener)

        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
            .addSnapshotListener(eventListener)
    }

    private val eventListener = EventListener<QuerySnapshot> { value, error ->
        if (error != null) {
            Log.e("MainActivity", "Firestore listener error", error)
            showToast("Error loading conversations")
            return@EventListener
        }

        if (value != null) {
            try {
                handleConversationChanges(value)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing conversations", e)
                showToast("Error processing conversations")
            }
        }
    }

    private fun handleConversationChanges(value: QuerySnapshot) {
        value.documentChanges.forEach { documentChange ->
            val senderId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: return@forEach
            val receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: return@forEach
            val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID) ?: return@forEach

            when (documentChange.type) {
                DocumentChange.Type.ADDED -> {
                    val chatMessage = createChatMessage(documentChange, currentUserId)
                    chatMessages.add(chatMessage)
                }
                DocumentChange.Type.MODIFIED -> {
                    updateExistingMessage(documentChange, senderId, receiverId)
                }
                else -> {}
            }
        }

        chatMessages.sortByDescending { it.dateTime }
        updateUI()
    }

    private fun createChatMessage(documentChange: DocumentChange, currentUserId: String): ChatMessage {
        return ChatMessage().apply {
            senderId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
            receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""

            if (currentUserId == senderId) {
                conversionImage = documentChange.document.getString(Constants.KEY_RECEIVER_IMAGE) ?: ""
                conversionName = documentChange.document.getString(Constants.KEY_RECEIVER_NAME) ?: ""
                conversionId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""
            } else {
                conversionImage = documentChange.document.getString(Constants.KEY_SENDER_IMAGE) ?: ""
                conversionName = documentChange.document.getString(Constants.KEY_SENDER_NAME) ?: ""
                conversionId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
            }

            message = documentChange.document.getString(Constants.KEY_LAST_MESSAGE) ?: ""
            dateTime = documentChange.document.getTimestamp(Constants.KEY_TIMESTAMP)?.toDate()?.toString() ?: ""
        }
    }

    private fun updateExistingMessage(documentChange: DocumentChange, senderId: String, receiverId: String) {
        val index = chatMessages.indexOfFirst {
            it.senderId == senderId && it.receiverId == receiverId
        }
        if (index != -1) {
            chatMessages[index].apply {
                message = documentChange.document.getString(Constants.KEY_LAST_MESSAGE) ?: message
                dateTime = documentChange.document.getTimestamp(Constants.KEY_TIMESTAMP)?.toDate()?.toString() ?: dateTime
            }
        }
    }

    private fun updateUI() {
        adapter.notifyDataSetChanged()
        binding.conversationRecyclerView.apply {
            smoothScrollToPosition(0)
            visibility = View.VISIBLE
        }
        binding.progressBar.visibility = View.GONE
    }

    private fun getToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> updateToken(token) }
            .addOnFailureListener {
                Log.e("MainActivity", "Token fetch failed")
                showToast("Unable to fetch FCM token")
            }
    }

    private fun updateToken(token: String) {
        val userId = preferenceManager.getString(Constants.KEY_USER_ID)
        if (userId.isNullOrEmpty()) {
            showToast("User ID not found")
            return
        }

        database.collection(Constants.KEY_COLLECTION_USERS)
            .document(userId)
            .update(Constants.KEY_FCM_TOKEN, token)
            .addOnFailureListener {
                Log.e("MainActivity", "Token update failed")
                showToast("Unable to update token")
            }
    }

    private fun signOut() {
        val userId = preferenceManager.getString(Constants.KEY_USER_ID)
        if (userId.isNullOrEmpty()) return

        showToast("Signing out...")

        database.collection(Constants.KEY_COLLECTION_USERS)
            .document(userId)
            .update(Constants.KEY_FCM_TOKEN, null)
            .addOnSuccessListener {
                preferenceManager.clear()
                startActivity(Intent(applicationContext, SignInActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Log.e("MainActivity", "Sign out failed")
                showToast("Unable to sign out")
            }
    }

    override fun onConversionClicked(user: User) {
        val intent = Intent(applicationContext, ChatActivity::class.java)
        intent.putExtra(Constants.KEY_USER, user)
        startActivity(intent)
    }
}