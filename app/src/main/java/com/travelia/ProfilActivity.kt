package com.travelia

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.travelia.databinding.ActivityProfilBinding

class ProfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var isEditMode = false

    private val daftarKotaTempatTinggal = arrayOf(
        "Banda Aceh", "Indrapuri / Seulimeum", "Jantho", "Sigli", "Beureunun",
        "Lueng Putu", "Meureudu", "Samalanga", "Bireun", "Lhokseumawe",
        "Lhoksukon", "Panton Labu", "Idi Rayeuk", "Peureulak", "Langsa",
        "Kuala Simpang", "Pangkalan Brandan", "Stabat", "Binjai", "Medan"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityProfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setFormEditable(false)
        loadUserData()
        setupDropdowns()
        setupListeners()

        // PENTING: Menangani gestur usap/swipe "Back" sistem Android
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToMain()
            }
        })

        setupBottomNav(this, binding.bottomNavigation.bottomNavigation, R.id.nav_profil)
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return

        binding.etEmail.setText(user.email ?: "-")

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                var nameForAvatar = "User"

                if (document != null && document.exists()) {
                    val username = document.getString("username").orEmpty()
                    val fullName = document.getString("full_name").orEmpty()
                    val phone = document.getString("phone").orEmpty()
                    val city = document.getString("city").orEmpty()
                    val gender = document.getString("gender").orEmpty()

                    if (username.isNotEmpty()) {
                        binding.etUsername.setText(username)
                        binding.tvProfileUsername.text = "@$username"
                    }
                    if (fullName.isNotEmpty()) {
                        binding.etFullName.setText(fullName)
                        binding.tvProfileName.text = fullName
                        nameForAvatar = fullName
                    }
                    if (phone.isNotEmpty()) binding.etPhone.setText(phone)
                    if (city.isNotEmpty()) binding.etCity.setText(city)
                    if (gender.isNotEmpty()) binding.etGender.setText(gender)
                }

                val avatarUrl = "https://api.dicebear.com/7.x/initials/svg?seed=$nameForAvatar"
                Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(binding.imgProfile)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat profil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupDropdowns() {
        // Adapter Kota
        val cityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, daftarKotaTempatTinggal)
        (binding.etCity as? AutoCompleteTextView)?.let { autoCompleteCity ->
            autoCompleteCity.setAdapter(cityAdapter)

            val showAllCities = {
                if (isEditMode) {
                    // Reset filter agar seluruh daftar kota muncul, bukan cuma kota yang sedang terpilih
                    cityAdapter.filter.filter(null)
                    autoCompleteCity.showDropDown()
                }
            }

            autoCompleteCity.setOnClickListener { showAllCities() }
            autoCompleteCity.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showAllCities()
            }
        }

        // Adapter Jenis Kelamin
        val genders = arrayOf("Laki-laki", "Perempuan")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        (binding.etGender as? AutoCompleteTextView)?.let { autoCompleteGender ->
            autoCompleteGender.setAdapter(genderAdapter)

            val showAllGenders = {
                if (isEditMode) {
                    // Reset filter agar Laki-laki dan Perempuan dua-duanya muncul
                    genderAdapter.filter.filter(null)
                    autoCompleteGender.showDropDown()
                }
            }

            autoCompleteGender.setOnClickListener { showAllGenders() }
            autoCompleteGender.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showAllGenders()
            }
        }
    }

    private fun setupListeners() {
        // Tombol Back di Header Bar
        binding.btnBack.setOnClickListener {
            navigateToMain()
        }

        // Tombol Edit / Simpan
        binding.btnEditSave.setOnClickListener {
            if (!isEditMode) {
                setFormEditable(true)
                isEditMode = true
                binding.btnEditSave.text = "Simpan Perubahan"
                binding.btnEditSave.setTextColor(Color.parseColor("#0D6EFD"))
            } else {
                val username = binding.etUsername.text.toString().trim()
                val fullName = binding.etFullName.text.toString().trim()
                val phone = binding.etPhone.text.toString().trim()
                val city = binding.etCity.text.toString().trim()
                val gender = binding.etGender.text.toString().trim()

                if (validateProfileInputs(username, fullName, phone, city)) {
                    Toast.makeText(this, "Menyimpan perubahan...", Toast.LENGTH_SHORT).show()
                    updateProfileData(username, fullName, phone, city, gender)
                }
            }
        }

        // Teks Keluar Akun
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun validateProfileInputs(
        username: String,
        fullName: String,
        phone: String,
        city: String
    ): Boolean {
        // Validasi Username
        val isAlphanumericUsername = username.matches("^[a-zA-Z0-9]+$".toRegex())
        if (username.isEmpty()) {
            binding.etUsername.error = "Username tidak boleh kosong"
            return false
        } else if (username.length < 3) {
            binding.etUsername.error = "Username minimal 3 karakter"
            return false
        } else if (username.length > 8) {
            binding.etUsername.error = "Username maksimal 8 karakter"
            return false
        } else if (!isAlphanumericUsername) {
            binding.etUsername.error = "Username hanya boleh huruf & angka (tanpa spasi/simbol)"
            return false
        }

        // Validasi Nama Lengkap
        val containsNumbersInName = fullName.any { it.isDigit() }
        if (fullName.isEmpty()) {
            binding.etFullName.error = "Nama lengkap harus diisi"
            return false
        } else if (containsNumbersInName) {
            binding.etFullName.error = "Nama lengkap tidak boleh mengandung angka"
            return false
        } else if (fullName.length > 30) {
            binding.etFullName.error = "Nama lengkap maksimal 30 karakter"
            return false
        }

        // Validasi Nomor Handphone
        val isNumericPhone = phone.matches("^[0-9]+$".toRegex())
        if (phone.isEmpty()) {
            binding.etPhone.error = "Nomor handphone harus diisi"
            return false
        } else if (!isNumericPhone) {
            binding.etPhone.error = "Nomor handphone hanya boleh berisi angka"
            return false
        } else if (phone.length < 10) {
            binding.etPhone.error = "Nomor handphone minimal 10 digit"
            return false
        } else if (phone.length > 12) {
            binding.etPhone.error = "Nomor handphone maksimal 12 digit"
            return false
        }

        // Validasi Kota Tempat Tinggal
        if (city.isEmpty()) {
            binding.etCity.error = "Kota tempat tinggal harus diisi"
            return false
        }

        return true
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun setFormEditable(enabled: Boolean) {
        binding.etEmail.isEnabled = false

        binding.etUsername.isEnabled = enabled
        binding.etFullName.isEnabled = enabled
        binding.etPhone.isEnabled = enabled
        binding.etCity.isEnabled = enabled
        binding.etGender.isEnabled = enabled

        val bgRes = if (enabled) Color.WHITE else Color.parseColor("#EEEEEE")
        val textColor = if (enabled) Color.parseColor("#333333") else Color.parseColor("#666666")

        listOf(binding.etUsername, binding.etFullName, binding.etPhone, binding.etCity, binding.etGender).forEach { editText ->
            editText.setBackgroundColor(bgRes)
            editText.setTextColor(textColor)
        }
    }

    private fun updateProfileData(username: String, fullName: String, phone: String, city: String, gender: String) {
        val user = auth.currentUser ?: return

        val profileUpdates = userProfileChangeRequest {
            displayName = username
        }

        user.updateProfile(profileUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userData = mapOf(
                    "username" to username,
                    "full_name" to fullName,
                    "phone" to phone,
                    "city" to city,
                    "gender" to gender
                )

                db.collection("users").document(user.uid)
                    .set(userData, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()

                        setFormEditable(false)
                        isEditMode = false
                        binding.btnEditSave.text = "Edit Profil"
                        binding.btnEditSave.setTextColor(Color.parseColor("#007BFF"))

                        if (fullName.isNotEmpty()) {
                            binding.tvProfileName.text = fullName
                        }
                        binding.tvProfileUsername.text = "@$username"

                        val seedName = if (fullName.isNotEmpty()) fullName else username
                        val avatarUrl = "https://api.dicebear.com/7.x/initials/svg?seed=$seedName"
                        Glide.with(this).load(avatarUrl).into(binding.imgProfile)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Gagal memperbarui Auth profil", Toast.LENGTH_SHORT).show()
            }
        }
    }
}