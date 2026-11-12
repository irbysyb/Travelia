package com.travelia

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.travelia.databinding.ActivityRegisterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityRegisterBinding.inflate(layoutInflater)
            setContentView(binding.root)

            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            binding.btnRegister.setOnClickListener {
                if (validateInputs()) {
                    registerToFirebase()
                }
            }

            binding.tvLoginLink.setOnClickListener {
                navigateToLogin()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memuat halaman: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerToFirebase() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        binding.btnRegister.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    val userData = hashMapOf(
                        "email" to email,
                        "role" to "user"
                    )

                    db.collection("users").document(uid)
                        .set(userData)
                        .addOnSuccessListener {
                            val generatedOtp = (1000..9999).random().toString()
                            sendOtpAndNavigate(email, generatedOtp)
                        }
                        .addOnFailureListener { e ->
                            binding.btnRegister.isEnabled = true
                            Toast.makeText(this, "Gagal menyimpan role user: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    binding.btnRegister.isEnabled = true
                    val errorMessage = task.exception?.message ?: "Registrasi gagal."
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun sendOtpAndNavigate(email: String, otpCode: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                binding.btnRegister.isEnabled = true
                Toast.makeText(this@RegisterActivity, "Kode OTP berhasil dikirim!", Toast.LENGTH_SHORT).show()
                navigateToOtp(email, otpCode)
            }
        }
    }

    private fun validateInputs(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Email tidak valid"
            return false
        } else {
            binding.tilEmail.isErrorEnabled = false
        }

        if (password.length < 6) {
            binding.tilPassword.error = "Password minimal 6 karakter"
            return false
        } else {
            binding.tilPassword.isErrorEnabled = false
        }

        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Password tidak cocok"
            return false
        } else {
            binding.tilConfirmPassword.isErrorEnabled = false
        }

        return true
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToOtp(email: String, otpCode: String) {
        val intent = Intent(this, OtpActivity::class.java).apply {
            putExtra("EXTRA_EMAIL", email)
            putExtra("EXTRA_OTP", otpCode)
        }
        startActivity(intent)
        finish()
    }
}