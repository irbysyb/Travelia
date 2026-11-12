package com.travelia

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

fun setupBottomNav(activity: AppCompatActivity, bottomNavView: BottomNavigationView, currentItemId: Int?) {
    // 1. Pasang listener terlebih dahulu agar siap menangani interaksi klik
    bottomNavView.setOnItemSelectedListener { item ->
        // Jika item yang diklik adalah halaman aktif saat ini, abaikan
        if (item.itemId == currentItemId) return@setOnItemSelectedListener true

        when (item.itemId) {
            R.id.nav_home -> {
                if (activity !is MainActivity) {
                    val intent = Intent(activity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    activity.startActivity(intent)
                    activity.finish()
                }
                true
            }
            R.id.nav_tiket -> {
                if (activity !is TiketSayaActivity) {
                    val intent = Intent(activity, TiketSayaActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    activity.startActivity(intent)
                    activity.finish()
                }
                true
            }
            R.id.nav_profil -> {
                if (activity !is ProfilActivity) {
                    val intent = Intent(activity, ProfilActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    activity.startActivity(intent)
                    activity.finish()
                }
                true
            }
            else -> false
        }
    }

    // 2. Gunakan post { } agar state/pilihan item dieksekusi setelah view selesai dilayout oleh sistem
    bottomNavView.post {
        if (currentItemId != null) {
            bottomNavView.selectedItemId = currentItemId
        } else {
            // Jika null (halaman sub-menu), bersihkan semua centang agar tidak ada yang menyala
            bottomNavView.menu.setGroupCheckable(0, true, false)
            for (i in 0 until bottomNavView.menu.size()) {
                bottomNavView.menu.getItem(i).isChecked = false
            }
            bottomNavView.menu.setGroupCheckable(0, true, true)
        }
    }
}