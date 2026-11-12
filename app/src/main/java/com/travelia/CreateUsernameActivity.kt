package com.travelia

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.travelia.databinding.ActivityCreateUsernameBinding

class CreateUsernameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateUsernameBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreateUsernameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@CreateUsernameActivity,
                    "Silakan tentukan username atau tekan 'Lewati' terlebih dahulu.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        binding.tvSkip.setOnClickListener {
            handleSkipWithRandomUsername()
        }

        binding.btnNext.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()

            if (validateUsername(username)) {
                saveUsernameToFirebase(username)
            }
        }
    }

    private fun handleSkipWithRandomUsername() {
        val user = auth.currentUser
        val email = user?.email

        val randomUsername = if (!email.isNullOrEmpty()) {
            val nameFromEmail = email.substringBefore("@").replace("[^a-zA-Z0-9]".toRegex(), "")
            val randomNumber = (100..999).random()
            "$nameFromEmail$randomNumber"
        } else {
            "user${(1000..9999).random()}"
        }

        saveUsernameToFirebase(randomUsername)
    }

    private fun validateUsername(username: String): Boolean {
        // Regex untuk mendeteksi apakah mengandung karakter selain huruf dan angka (termasuk simbol & spasi)
        val isValidCharacters = username.matches("^[a-zA-Z0-9]+$".toRegex())

        if (username.isEmpty()) {
            binding.tilUsername.error = "Username tidak boleh kosong"
            return false
        } else if (username.length < 3) {
            binding.tilUsername.error = "Username minimal 3 karakter"
            return false
        } else if (username.length > 8) {
            binding.tilUsername.error = "Username maksimal 8 karakter"
            return false
        } else if (username.contains(" ")) {
            binding.tilUsername.error = "Username tidak boleh mengandung spasi"
            return false
        } else if (!isValidCharacters) {
            binding.tilUsername.error = "Username hanya boleh berisi huruf dan angka (tanpa simbol)"
            return false
        } else {
            binding.tilUsername.isErrorEnabled = false
        }
        return true
    }

    private fun saveUsernameToFirebase(username: String) {
        val user = auth.currentUser

        if (user != null) {
            binding.btnNext.isEnabled = false
            binding.tvSkip.isEnabled = false

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()

            user.updateProfile(profileUpdates).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // SIMPAN JUGA USERNAME KE FIRESTORE AGAR TERBACA DI PROFIL
                    val usernameMap = mapOf("username" to username)
                    db.collection("users").document(user.uid)
                        .set(usernameMap, SetOptions.merge())
                        .addOnSuccessListener {
                            binding.btnNext.isEnabled = true
                            binding.tvSkip.isEnabled = true
                            navigateToUserData()
                        }
                        .addOnFailureListener {
                            binding.btnNext.isEnabled = true
                            binding.tvSkip.isEnabled = true
                            navigateToUserData()
                        }
                } else {
                    binding.btnNext.isEnabled = true
                    binding.tvSkip.isEnabled = true
                    navigateToUserData()
                }
            }
        } else {
            navigateToUserData()
        }
    }

    private fun navigateToUserData() {
        val intent = Intent(this, UserDataActivity::class.java)
        startActivity(intent)
        finish()
    }
}