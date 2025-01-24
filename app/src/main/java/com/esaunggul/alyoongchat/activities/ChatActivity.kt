package com.esaunggul.alyoongchat.activities

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.esaunggul.alyoongchat.adapters.ChatAdapter
import com.esaunggul.alyoongchat.databinding.ActivityChatBinding
import com.esaunggul.alyoongchat.models.ChatMessage
import com.esaunggul.alyoongchat.models.User
import com.esaunggul.alyoongchat.utilities.Constants
import com.esaunggul.alyoongchat.utilities.PreferenceManager
import com.google.firebase.firestore.*
import com.google.firebase.firestore.EventListener
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : BaseActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var receiverUser: User
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var database: FirebaseFirestore
    private var conversionId: String? = null
    private var isReceiverAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadReceiverDetails()
        init()
        setListeners()
        listenMessages()
        listenAvailabilityOfReceiver()
    }

    private fun init() {
        preferenceManager = PreferenceManager(applicationContext)
        chatAdapter = ChatAdapter(
            chatMessages,
            getBitmapFromEncodedString(receiverUser.image),
            preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
        )
        binding.chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@ChatActivity)
        }
        database = FirebaseFirestore.getInstance()
    }

    private fun sendMessage() {
        val message = hashMapOf<String, Any>(
            Constants.KEY_SENDER_ID to (preferenceManager.getString(Constants.KEY_USER_ID) ?: ""),
            Constants.KEY_RECEIVER_ID to (receiverUser.id ?: ""),
            Constants.KEY_MESSAGE to binding.inputMessage.text.toString(),
            Constants.KEY_TIMESTAMP to FieldValue.serverTimestamp() // Gunakan FieldValue.serverTimestamp()
        )

        database.collection(Constants.KEY_COLLECTION_CHAT)
            .add(message)
            .addOnSuccessListener {
                if (conversionId != null) {
                    updateConversion(binding.inputMessage.text.toString())
                } else {
                    val conversion = hashMapOf<String, Any>(
                        Constants.KEY_SENDER_ID to (preferenceManager.getString(Constants.KEY_USER_ID) ?: ""),
                        Constants.KEY_SENDER_NAME to (preferenceManager.getString(Constants.KEY_NAME) ?: ""),
                        Constants.KEY_SENDER_IMAGE to (preferenceManager.getString(Constants.KEY_IMAGE) ?: ""),
                        Constants.KEY_RECEIVER_ID to (receiverUser.id ?: ""),
                        Constants.KEY_RECEIVER_NAME to (receiverUser.name ?: ""),
                        Constants.KEY_RECEIVER_IMAGE to (receiverUser.image ?: ""),
                        Constants.KEY_LAST_MESSAGE to binding.inputMessage.text.toString(),
                        Constants.KEY_TIMESTAMP to FieldValue.serverTimestamp()
                    )
                    addConversion(conversion)
                }
            }
    }

    private fun listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS)
            .document(receiverUser.id ?: "")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (value != null && value.exists()) {
                    val availability = value.getLong(Constants.KEY_AVAILABILITY)?.toInt() ?: 0
                    isReceiverAvailable = availability == 1
                }
            }
        if(isReceiverAvailable) {
            binding.textAvailability.visibility = View.VISIBLE
        } else {
            binding.textAvailability.visibility = View.GONE
        }
    }

    private fun listenMessages() {
        val eventListener = EventListener<QuerySnapshot> { value, error ->
            if (error != null) return@EventListener
            value?.let {
                val count = chatMessages.size
                for (documentChange in it.documentChanges) {
                    if (documentChange.type == DocumentChange.Type.ADDED) {
                        val chatMessage = ChatMessage().apply {
                            senderId = documentChange.document.getString(Constants.KEY_SENDER_ID)
                            receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID)
                            message = documentChange.document.getString(Constants.KEY_MESSAGE)
                            dateTime = getReadableDateTime(documentChange.document.getDate(Constants.KEY_TIMESTAMP)!!)
                            dateObject = documentChange.document.getDate(Constants.KEY_TIMESTAMP)
                        }
                        chatMessages.add(chatMessage)
                    }
                }
                chatMessages.sortBy { it.dateObject }
                if (count == 0) {
                    chatAdapter.notifyDataSetChanged()
                } else {
                    chatAdapter.notifyItemRangeInserted(count, chatMessages.size - count)
                    binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }
                binding.chatRecyclerView.visibility = View.VISIBLE
            }
            binding.progressBar.visibility = View.GONE
            if (conversionId == null) {
                checkForConversion()
            }
        }

        val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
        database.collection(Constants.KEY_COLLECTION_CHAT)
            .whereEqualTo(Constants.KEY_SENDER_ID, currentUserId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
            .addSnapshotListener(eventListener)

        database.collection(Constants.KEY_COLLECTION_CHAT)
            .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
            .addSnapshotListener(eventListener)
    }

    private fun getBitmapFromEncodedString(encodedImage: String?): Bitmap? {
        return try {
            val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadReceiverDetails() {
        receiverUser = intent.getSerializableExtra(Constants.KEY_USER) as User
        binding.textName.text = receiverUser.name
    }

    private fun setListeners() {
        binding.imageBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.layoutSend.setOnClickListener { sendMessage() }
    }

    private fun getReadableDateTime(date: Date): String {
        return SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date)
    }

    private fun addConversion(conversion: HashMap<String, Any>) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .add(conversion)
            .addOnSuccessListener { documentReference ->
                conversionId = documentReference.id
            }
    }

    private fun updateConversion(message: String) {
        conversionId?.let {
            val documentReference = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(it)
            documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, Date()
            )
        }
    }

    private fun checkForConversion() {
        if (conversionId == null) {
            checkForConversionRemotely(
                preferenceManager.getString(Constants.KEY_USER_ID) ?: "",
                receiverUser.id ?: ""
            )
            checkForConversionRemotely(
                receiverUser.id ?: "",
                preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
            )
        }
    }

    private fun checkForConversionRemotely(senderId: String, receiverId: String) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null && task.result.documents.isNotEmpty()) {
                    val documentSnapshot = task.result.documents[0]
                    conversionId = documentSnapshot.id
                }
            }
    }

    override fun onResume() {
        super.onResume()
        listenAvailabilityOfReceiver()
    }
}
