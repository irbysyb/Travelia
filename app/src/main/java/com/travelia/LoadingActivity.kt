package com.travelia

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.travelia.databinding.ActivityLoadingBinding

class LoadingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoadingBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val handler = Handler(Looper.getMainLooper())
    private var dotCount = 1
    private lateinit var runnable: Runnable

    companion object {
        const val EXTRA_TYPE = "EXTRA_TYPE"
        const val TYPE_REGISTER = "REGISTER"
        const val TYPE_LOGIN = "LOGIN"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 1. Memuat Gambar GIF Menggunakan Glide
        Glide.with(this)
            .asGif()
            .load(R.drawable.img_loading_illustration)
            .into(binding.imgLoadingIllustration)

        val loadingType = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_LOGIN
        setupUI(loadingType)

        // 2. Jalankan animasi teks titik-titik bergerak
        startLoadingTextAnimation()

        // 3. Simulasi delay loading 3 detik lalu cek role dan arahkan halaman
        handler.postDelayed({
            checkRoleAndNavigate()
        }, 3000)
    }

    private fun checkRoleAndNavigate() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    val role = document.getString("role")
                    if (document.exists() && role == "admin") {
                        // Jika Admin -> Masuk ke AdminActivity
                        val intent = Intent(this, AdminActivity::class.java)
                        startActivity(intent)
                        finishAffinity()
                    } else {
                        // Jika User Biasa -> Masuk ke MainActivity
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finishAffinity()
                    }
                }
                .addOnFailureListener {
                    // Fallback jika gagal ambil dari Firestore, arahkan ke MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finishAffinity()
                }
        } else {
            // Jika sesi user tidak ditemukan, kembali ke LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finishAffinity()
        }
    }

    private fun startLoadingTextAnimation() {
        runnable = object : Runnable {
            override fun run() {
                val dots = " .".repeat(dotCount)
                binding.tvLoadingDots.text = dots

                dotCount = if (dotCount < 3) dotCount + 1 else 1
                handler.postDelayed(this, 400)
            }
        }
        handler.post(runnable)
    }

    private fun setupUI(type: String) {
        if (type == TYPE_REGISTER) {
            binding.tvRegisterStatus.visibility = View.VISIBLE
            binding.tvGreeting.visibility = View.GONE
        } else {
            binding.tvRegisterStatus.visibility = View.GONE
            binding.tvGreeting.visibility = View.VISIBLE

            val uid = auth.currentUser?.uid
            if (uid != null) {
                // Ambil data langsung dari Firestore untuk memastikan apakah Admin atau User
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { document ->
                        val role = document.getString("role")
                        if (document.exists() && role == "admin") {
                            binding.tvGreeting.text = "Halo, Admin."
                        } else {
                            val username = document.getString("username")
                                ?: document.getString("name")
                                ?: auth.currentUser?.displayName
                                ?: "User"
                            binding.tvGreeting.text = "Halo, $username."
                        }
                    }
                    .addOnFailureListener {
                        binding.tvGreeting.text = "Halo, User."
                    }
            } else {
                binding.tvGreeting.text = "Halo, User."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}