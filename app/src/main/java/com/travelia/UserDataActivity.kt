package com.travelia

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.travelia.databinding.ActivityUserDataBinding
import java.util.Calendar

class UserDataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDataBinding

    private val daftarKotaTempatTinggal = arrayOf(
        "Banda Aceh", "Indrapuri / Seulimeum", "Jantho", "Sigli", "Beureunun",
        "Lueng Putu", "Meureudu", "Samalanga", "Bireun", "Lhokseumawe",
        "Lhoksukon", "Panton Labu", "Idi Rayeuk", "Peureulak", "Langsa",
        "Kuala Simpang", "Pangkalan Brandan", "Stabat", "Binjai", "Medan"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@UserDataActivity, "Silakan selesaikan pengisian data diri terlebih dahulu.", Toast.LENGTH_SHORT).show()
            }
        })

        setupGenderDropdown()
        setupCityDropdown()
        setupDatePicker()

        binding.btnFinish.setOnClickListener {
            if (validateInputs()) {
                simpanDataUserKeFirestore()
            }
        }
    }

    private fun setupGenderDropdown() {
        val genders = arrayOf("Laki-laki", "Perempuan")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        binding.actvGender.setAdapter(adapter)

        binding.actvGender.setOnClickListener { binding.actvGender.showDropDown() }
        binding.actvGender.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.actvGender.showDropDown() }
    }

    private fun setupCityDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, daftarKotaTempatTinggal)
        (binding.etCity as? AutoCompleteTextView)?.let { autoComplete ->
            autoComplete.setAdapter(adapter)
            autoComplete.setOnClickListener { autoComplete.showDropDown() }
            autoComplete.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) autoComplete.showDropDown() }
        }
    }

    private fun setupDatePicker() {
        binding.etBirthDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val formattedDate = String.format("%02d/%02d/%d", selectedDay, selectedMonth + 1, selectedYear)
                    binding.etBirthDate.setText(formattedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }
    }

    private fun simpanDataUserKeFirestore() {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return

        val fullName = binding.etFullName.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val gender = binding.actvGender.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val birthDate = binding.etBirthDate.text.toString().trim()

        // Cross-check: Data ini dipetakan dan dikirim ke dokumen "users/{userId}" di Firestore
        val userData = hashMapOf(
            "full_name" to fullName,
            "city" to city,
            "gender" to gender,
            "phone" to phone,
            "birthDate" to birthDate,
            "role" to "user"
        )

        binding.btnFinish.isEnabled = false

        db.collection("users").document(userId)
            .set(userData, SetOptions.merge()) // Menggunakan merge agar data username sebelumnya tidak tertimpa
            .addOnSuccessListener {
                Toast.makeText(this, "Data diri berhasil disimpan!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoadingActivity::class.java).apply {
                    putExtra(LoadingActivity.EXTRA_TYPE, LoadingActivity.TYPE_REGISTER)
                }
                startActivity(intent)
                finishAffinity()
            }
            .addOnFailureListener { e ->
                binding.btnFinish.isEnabled = true
                Toast.makeText(this, "Gagal menyimpan data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun validateInputs(): Boolean {
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val isNumericPhone = phone.matches("^[0-9]+$".toRegex())

        // Cek apakah nama mengandung angka (jika ada angka, maka invalid)
        val containsNumbers = fullName.any { it.isDigit() }

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Nama lengkap harus diisi"
            return false
        }
        if (containsNumbers) {
            binding.etFullName.error = "Nama lengkap tidak boleh mengandung angka"
            return false
        }
        if (fullName.length > 30) {
            binding.etFullName.error = "Nama lengkap maksimal 30 karakter"
            return false
        }
        if (binding.etCity.text.toString().trim().isEmpty()) {
            binding.etCity.error = "Kota tempat tinggal harus diisi"
            return false
        }
        if (binding.actvGender.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Pilih jenis kelamin anda", Toast.LENGTH_SHORT).show()
            return false
        }
        if (phone.isEmpty()) {
            binding.etPhone.error = "Nomor handphone harus diisi"
            return false
        }
        if (!isNumericPhone) {
            binding.etPhone.error = "Nomor handphone hanya boleh berisi angka"
            return false
        }
        if (phone.length < 10) {
            binding.etPhone.error = "Nomor handphone minimal 10 digit"
            return false
        }
        if (phone.length > 12) {
            binding.etPhone.error = "Nomor handphone maksimal 12 digit"
            return false
        }
        if (binding.etBirthDate.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Pilih tanggal lahir anda", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}