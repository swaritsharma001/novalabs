package com.novalabs.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class NovaLabsDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novalabs_dashboard)

        val tvUsername = findViewById<TextView>(R.id.tvUsername)
        val tvUserId = findViewById<TextView>(R.id.tvUserId)
        val cardBotHosting = findViewById<MaterialCardView>(R.id.cardBotHosting)
        val cardProfile = findViewById<MaterialCardView>(R.id.cardProfile)

        val username = intent.getStringExtra("EXTRA_USER_NAME") ?: "Nova User"
        val userId = intent.getStringExtra("EXTRA_USER_ID") ?: "10839201948102"

        tvUsername.text = username
        tvUserId.text = "ID: $userId"

        cardBotHosting.setOnClickListener {
            Toast.makeText(this, "Host Your Own Bot clicked!", Toast.LENGTH_SHORT).show()
        }

        // Tap profile card to log out and clear stored auth token session (Bluecord logout flow)
        cardProfile.setOnClickListener {
            getSharedPreferences("novalabs_auth_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()

            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
