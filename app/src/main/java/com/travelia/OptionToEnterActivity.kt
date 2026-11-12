package com.travelia

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.travelia.databinding.ActivityOptionToEnterBinding

class OptionToEnterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOptionToEnterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // 1. Inisialisasi ViewBinding
            binding = ActivityOptionToEnterBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // 2. Klik Tombol Login -> Pindah ke LoginActivity
            binding.btnLogin.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }

            // 3. Klik Tombol Register -> Pindah ke RegisterActivity
            binding.btnRegister.setOnClickListener {
                try {
                    val intent = Intent(this, RegisterActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membuka halaman Register: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}