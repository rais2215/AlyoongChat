package com.esaunggul.alyoongchat.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.esaunggul.alyoongchat.adapters.UsersAdapter
import com.esaunggul.alyoongchat.databinding.ActivityUsersBinding
import com.esaunggul.alyoongchat.listeners.UserListener
import com.esaunggul.alyoongchat.models.User
import com.esaunggul.alyoongchat.utilities.Constants
import com.esaunggul.alyoongchat.utilities.PreferenceManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot

class UsersActivity : BaseActivity(), UserListener {

    private lateinit var binding: ActivityUsersBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(applicationContext)

        setListeners()
        getUsers()
    }

    private fun setListeners() {
        binding.imageBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun getUsers() {
        loading(true)
        val database = FirebaseFirestore.getInstance()

        database.collection(Constants.KEY_COLLECTION_USERS)
            .get()
            .addOnCompleteListener { task ->
                loading(false)
                val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID)
                if (task.isSuccessful && task.result != null) {
                    val users = mutableListOf<User>()
                    for (document: QueryDocumentSnapshot in task.result!!) {
                        if (currentUserId == document.id) {
                            continue
                        }
                        val user = User().apply {
                            name = document.getString(Constants.KEY_NAME) ?: ""
                            email = document.getString(Constants.KEY_EMAIL) ?: ""
                            image = document.getString(Constants.KEY_IMAGE) ?: ""
                            token = document.getString(Constants.KEY_FCM_TOKEN) ?: ""
                            id = document.id
                        }
                        users.add(user)
                    }
                    if (users.isNotEmpty()) {
                        val userAdapter = UsersAdapter(users, this)
                        binding.userRecyclerView.adapter = userAdapter
                        binding.userRecyclerView.visibility = View.VISIBLE
                    } else {
                        showErrorMessage()
                    }
                } else {
                    showErrorMessage()
                }
            }
    }

    private fun showErrorMessage() {
        binding.textErrorMessage.text = "No user available"
        binding.textErrorMessage.visibility = View.VISIBLE
    }

    private fun loading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
    }

    override fun onUserClicked(user: User?) {
        val intent = Intent(applicationContext, ChatActivity::class.java).apply {
            putExtra(Constants.KEY_USER, user)
        }
        startActivity(intent)
        finish()
    }
}
