package com.travelia

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.travelia.databinding.ActivityOtpBinding

class OtpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpBinding
    private var countDownTimer: CountDownTimer? = null

    private var emailUser: String? = null
    private var validOtpCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // MENGHALANGI GESTUR SWIPE KANAN KE KIRI ATAU TOMBOL BACK SUPAYA TIDAK KEMBALI
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@OtpActivity,
                    "Silakan selesaikan verifikasi OTP terlebih dahulu.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // Ambil data email & OTP dari intent Register
        emailUser = intent.getStringExtra("EXTRA_EMAIL")
        validOtpCode = intent.getStringExtra("EXTRA_OTP")

        // Update teks sub-header dengan email pengguna
        if (!emailUser.isNullOrEmpty()) {
            binding.tvOtpSubtitle.text = "Check your inbox or spam for $emailUser"
        }

        // Tampilkan Toast sementara untuk testing (Mengetahui kode OTP yang dihasilkan)
        Toast.makeText(this, "Kode OTP Kamu: $validOtpCode", Toast.LENGTH_LONG).show()

        setupOtpInputs()
        startResendTimer()

        binding.tvResendBtn.setOnClickListener {
            // Generate OTP baru saat klik Resend
            validOtpCode = (1000..9999).random().toString()
            Toast.makeText(this, "Kode OTP Baru: $validOtpCode", Toast.LENGTH_LONG).show()
            startResendTimer()
        }

        // Tombol Verifikasi/Selanjutnya
        binding.btnNext.setOnClickListener {
            val inputOtp = getOtpCode()

            if (inputOtp.length < 4) {
                Toast.makeText(this, "Masukkan 4 digit kode OTP", Toast.LENGTH_SHORT).show()
            } else if (inputOtp == validOtpCode) {
                // OTP COCOK -> Verifikasi Sukses
                Toast.makeText(this, "Verifikasi Berhasil!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, CreateUsernameActivity::class.java)
                startActivity(intent)
                finishAffinity()
            } else {
                // OTP SALAH
                Toast.makeText(this, "Kode OTP salah, silakan coba lagi!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOtpCode(): String {
        return binding.etOtp1.text.toString() +
                binding.etOtp2.text.toString() +
                binding.etOtp3.text.toString() +
                binding.etOtp4.text.toString()
    }

    private fun setupOtpInputs() {
        val et1 = binding.etOtp1
        val et2 = binding.etOtp2
        val et3 = binding.etOtp3
        val et4 = binding.etOtp4

        fun moveNext(current: EditText, next: EditText) {
            current.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1) {
                        next.requestFocus()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        fun handleBackspace(current: EditText, previous: EditText) {
            current.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (current.text.isEmpty()) {
                        previous.requestFocus()
                        previous.setText("")
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        moveNext(et1, et2)
        moveNext(et2, et3)
        moveNext(et3, et4)

        handleBackspace(et2, et1)
        handleBackspace(et3, et2)
        handleBackspace(et4, et3)
    }

    private fun startResendTimer() {
        binding.tvResendBtn.isEnabled = false
        binding.tvResendBtn.alpha = 0.5f

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                binding.tvTimer.text = "in ${secondsRemaining}s"
            }

            override fun onFinish() {
                binding.tvTimer.text = ""
                binding.tvResendBtn.isEnabled = true
                binding.tvResendBtn.alpha = 1.0f
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}