package com.travelia

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.travelia.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inisialisasi ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Inisialisasi Firebase Auth & Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 3. KLIK TEKS "Register" DI FOOTER -> Pindah ke RegisterActivity
        binding.tvRegisterLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 4. KLIK TOMBOL "Login" -> Proses Login ke Firebase
        binding.btnLogin.setOnClickListener {
            if (validateInputs()) {
                loginToFirebase()
            }
        }
    }

    private fun loginToFirebase() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Nonaktifkan form input dan tombol agar user tidak bisa mengetik ulang saat loading
        setFormEnabled(false)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    val uid = firebaseUser?.uid ?: ""
                    val userEmail = firebaseUser?.email ?: email

                    val userDocRef = db.collection("users").document(uid)

                    // Cek apakah data user sudah ada di Firestore
                    userDocRef.get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                // Jika data sudah ada, baca rolenya
                                val role = document.getString("role")

                                if (role == "admin") {
                                    Toast.makeText(this, "Selamat Datang, Admin!", Toast.LENGTH_SHORT).show()
                                    // Admin diarahkan ke LoadingActivity terlebih dahulu
                                    val intent = Intent(this, LoadingActivity::class.java).apply {
                                        putExtra(LoadingActivity.EXTRA_TYPE, LoadingActivity.TYPE_LOGIN)
                                    }
                                    startActivity(intent)
                                    finishAffinity()
                                } else {
                                    Toast.makeText(this, "Login Berhasil!", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this, LoadingActivity::class.java).apply {
                                        putExtra(LoadingActivity.EXTRA_TYPE, LoadingActivity.TYPE_LOGIN)
                                    }
                                    startActivity(intent)
                                    finishAffinity()
                                }
                            } else {
                                // === PENANGANAN USER LAMA YANG BELUM ADA DI FIRESTORE ===
                                val defaultUserData = hashMapOf(
                                    "email" to userEmail,
                                    "role" to "user" // Otomatis dianggap sebagai user biasa
                                )

                                userDocRef.set(defaultUserData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Login Berhasil!", Toast.LENGTH_SHORT).show()
                                        val intent = Intent(this, LoadingActivity::class.java).apply {
                                            putExtra(LoadingActivity.EXTRA_TYPE, LoadingActivity.TYPE_LOGIN)
                                        }
                                        startActivity(intent)
                                        finishAffinity()
                                    }
                                    .addOnFailureListener { e ->
                                        setFormEnabled(true)
                                        Toast.makeText(this, "Gagal menginisialisasi data user: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            setFormEnabled(true)
                            Toast.makeText(this, "Gagal mengambil data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    setFormEnabled(true)
                    val errorMessage = task.exception?.message ?: "Login gagal, periksa email dan password."
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    // Fungsi untuk mengunci atau membuka kembali inputan form saat proses loading
    private fun setFormEnabled(enabled: Boolean) {
        binding.etEmail.isEnabled = enabled
        binding.etPassword.isEnabled = enabled
        binding.btnLogin.isEnabled = enabled
    }

    private fun validateInputs(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email tidak boleh kosong"
            return false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Format email tidak valid"
            return false
        } else {
            binding.tilEmail.isErrorEnabled = false
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password tidak boleh kosong"
            return false
        } else {
            binding.tilPassword.isErrorEnabled = false
        }

        return true
    }
}