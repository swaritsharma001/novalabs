package com.novalabs.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class NovaLabsDashboardActivity : AppCompatActivity() {

    // Tab buttons & containers
    private lateinit var tabBots: MaterialButton
    private lateinit var tabPricing: MaterialButton
    private lateinit var tabSessions: MaterialButton
    private lateinit var containerBotsTab: NestedScrollView
    private lateinit var containerPricingTab: NestedScrollView
    private lateinit var containerSessionsTab: NestedScrollView

    // Bots Tab Views
    private lateinit var rvBots: RecyclerView
    private lateinit var tvEmptyBots: TextView
    private lateinit var btnRefreshBots: MaterialButton
    private lateinit var btnAddBot: MaterialButton
    private lateinit var tvPlanBadge: TextView
    private lateinit var btnConfigApi: MaterialButton

    // Pricing & PopPay Tab Views
    private lateinit var btnPayPopPayUpi: MaterialButton
    private lateinit var btnPopPayWeb: MaterialButton
    private lateinit var etUtrNumber: TextInputEditText
    private lateinit var btnSubmitUtr: MaterialButton
    private lateinit var tvUtrStatus: TextView

    // Sessions Tab Views
    private lateinit var tvUsername: TextView
    private lateinit var tvUserId: TextView
    private lateinit var tvPremiumBadge: TextView
    private lateinit var btnSwitchAccount: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private lateinit var botAdapter: BotAdapter
    private var currentSession: UserSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novalabs_dashboard)

        initViews()
        setupTabs()
        loadActiveSession()
        setupRecyclerView()
        setupListeners()
        loadBotsData()
    }

    private fun initViews() {
        tabBots = findViewById(R.id.tabBots)
        tabPricing = findViewById(R.id.tabPricing)
        tabSessions = findViewById(R.id.tabSessions)
        containerBotsTab = findViewById(R.id.containerBotsTab)
        containerPricingTab = findViewById(R.id.containerPricingTab)
        containerSessionsTab = findViewById(R.id.containerSessionsTab)

        rvBots = findViewById(R.id.rvBots)
        tvEmptyBots = findViewById(R.id.tvEmptyBots)
        btnRefreshBots = findViewById(R.id.btnRefreshBots)
        btnAddBot = findViewById(R.id.btnAddBot)
        tvPlanBadge = findViewById(R.id.tvPlanBadge)
        btnConfigApi = findViewById(R.id.btnConfigApi)

        btnPayPopPayUpi = findViewById(R.id.btnPayPopPayUpi)
        btnPopPayWeb = findViewById(R.id.btnPopPayWeb)
        etUtrNumber = findViewById(R.id.etUtrNumber)
        btnSubmitUtr = findViewById(R.id.btnSubmitUtr)
        tvUtrStatus = findViewById(R.id.tvUtrStatus)

        tvUsername = findViewById(R.id.tvUsername)
        tvUserId = findViewById(R.id.tvUserId)
        tvPremiumBadge = findViewById(R.id.tvPremiumBadge)
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun setupTabs() {
        tabBots.setOnClickListener { switchTab(0) }
        tabPricing.setOnClickListener { switchTab(1) }
        tabSessions.setOnClickListener { switchTab(2) }
    }

    private fun switchTab(index: Int) {
        val activeBg = Color.parseColor("#7C3AED")
        val inactiveBg = Color.TRANSPARENT
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#94A3B8")

        // Reset
        tabBots.setBackgroundColor(if (index == 0) activeBg else inactiveBg)
        tabBots.setTextColor(if (index == 0) activeText else inactiveText)
        containerBotsTab.visibility = if (index == 0) View.VISIBLE else View.GONE

        tabPricing.setBackgroundColor(if (index == 1) activeBg else inactiveBg)
        tabPricing.setTextColor(if (index == 1) activeText else inactiveText)
        containerPricingTab.visibility = if (index == 1) View.VISIBLE else View.GONE

        tabSessions.setBackgroundColor(if (index == 2) activeBg else inactiveBg)
        tabSessions.setTextColor(if (index == 2) activeText else inactiveText)
        containerSessionsTab.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    private fun loadActiveSession() {
        currentSession = SessionManager.getActiveSession(this)

        if (currentSession == null) {
            val username = intent.getStringExtra("EXTRA_USER_NAME") ?: "NxtIndia User"
            val userId = intent.getStringExtra("EXTRA_USER_ID") ?: "10839201948102"
            val prefs = getSharedPreferences("novalabs_auth_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("STORE_AUTHED_TOKEN", "") ?: ""

            currentSession = UserSession(
                id = userId,
                username = username,
                token = token
            ).also {
                SessionManager.saveSession(this, it, setActive = true)
            }
        }

        currentSession?.let {
            tvUsername.text = it.username
            tvUserId.text = "ID: ${it.id}"
            if (it.isPremium) {
                tvPremiumBadge.text = "★ Premium Active"
                tvPremiumBadge.setTextColor(Color.parseColor("#57F287"))
                tvPlanBadge.text = "Premium (Unlimited)"
                tvPlanBadge.setTextColor(Color.parseColor("#57F287"))
            } else {
                tvPremiumBadge.text = "Free Tier"
                tvPremiumBadge.setTextColor(Color.parseColor("#FEE75C"))
                tvPlanBadge.text = "Free Tier (2 Max)"
                tvPlanBadge.setTextColor(Color.parseColor("#FEE75C"))
            }
        }
    }

    private fun setupRecyclerView() {
        botAdapter = BotAdapter(
            bots = emptyList(),
            onToggle = { bot -> toggleBotState(bot) },
            onDelete = { bot -> confirmDeleteBot(bot) }
        )
        rvBots.layoutManager = LinearLayoutManager(this)
        rvBots.adapter = botAdapter
    }

    private fun setupListeners() {
        btnRefreshBots.setOnClickListener {
            loadBotsData()
        }

        btnAddBot.setOnClickListener {
            showAddBotDialog()
        }

        btnConfigApi.setOnClickListener {
            showApiConfigDialog()
        }

        btnSwitchAccount.setOnClickListener {
            showAccountSwitcherDialog()
        }

        btnLogout.setOnClickListener {
            currentSession?.let { SessionManager.removeSession(this, it.id) }
            getSharedPreferences("novalabs_auth_prefs", Context.MODE_PRIVATE).edit().clear().apply()

            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        // PopPay & UPI Payments
        btnPayPopPayUpi.setOnClickListener {
            val token = currentSession?.token ?: ""
            btnPayPopPayUpi.isEnabled = false
            Toast.makeText(this, "Generating PopPay order...", Toast.LENGTH_SHORT).show()

            PopPayClient.createOrder(this, token, amount = "100.00") { result ->
                btnPayPopPayUpi.isEnabled = true
                result.onSuccess { order ->
                    val opened = PopPayClient.openUpiIntent(this, upiUrl = order.upiDeepLink, amount = order.payableAmount, orderId = order.orderId)
                    if (!opened) {
                        Toast.makeText(this, "No UPI app found. Opening PopPay checkout...", Toast.LENGTH_SHORT).show()
                        PopPayClient.openPopPayWebCheckout(this, checkoutUrl = order.checkoutUrl)
                    }
                }.onFailure {
                    PopPayClient.openUpiIntent(this, amount = "100")
                }
            }
        }

        btnPopPayWeb.setOnClickListener {
            val token = currentSession?.token ?: ""
            btnPopPayWeb.isEnabled = false
            PopPayClient.createOrder(this, token, amount = "100.00") { result ->
                btnPopPayWeb.isEnabled = true
                result.onSuccess { order ->
                    PopPayClient.openPopPayWebCheckout(this, checkoutUrl = order.checkoutUrl)
                }.onFailure {
                    PopPayClient.openPopPayWebCheckout(this)
                }
            }
        }

        btnSubmitUtr.setOnClickListener {
            val utr = etUtrNumber.text?.toString()?.trim().orEmpty()
            if (utr.isEmpty()) {
                Toast.makeText(this, "Please enter your 12-digit UTR number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val token = currentSession?.token ?: return@setOnClickListener
            btnSubmitUtr.isEnabled = false
            tvUtrStatus.visibility = View.VISIBLE
            tvUtrStatus.text = "Verifying UTR transaction..."

            NxtIndiaApiClient.submitUtr(this, token, utr) { result ->
                btnSubmitUtr.isEnabled = true
                result.onSuccess { msg ->
                    tvUtrStatus.setTextColor(Color.parseColor("#57F287"))
                    tvUtrStatus.text = "✓ $msg Premium activated!"
                    etUtrNumber.setText("")
                    currentSession?.let {
                        val updated = it.copy(isPremium = true)
                        SessionManager.saveSession(this, updated, setActive = true)
                        loadActiveSession()
                    }
                }.onFailure { err ->
                    tvUtrStatus.setTextColor(Color.parseColor("#EF4444"))
                    tvUtrStatus.text = "✗ ${err.localizedMessage ?: "UTR submission failed"}"
                }
            }
        }
    }

    private fun loadBotsData() {
        val token = currentSession?.token ?: return
        btnRefreshBots.isEnabled = false

        NxtIndiaApiClient.fetchBots(this, token) { result ->
            btnRefreshBots.isEnabled = true
            result.onSuccess { bots ->
                botAdapter.updateData(bots)
                tvEmptyBots.visibility = if (bots.isEmpty()) View.VISIBLE else View.GONE
            }.onFailure { err ->
                tvEmptyBots.visibility = View.VISIBLE
                tvEmptyBots.text = "Backend status: ${err.localizedMessage ?: "Connecting..."}\nTap 'API Host' to set backend URL or '+ Add Bot' to connect."
            }
        }
    }

    private fun toggleBotState(bot: NxtBot) {
        val token = currentSession?.token ?: return
        val action = if (bot.isRunning) "Stopping" else "Starting"
        Toast.makeText(this, "$action ${bot.username}...", Toast.LENGTH_SHORT).show()

        if (bot.isRunning) {
            NxtIndiaApiClient.stopBot(this, token, bot.id) { res ->
                res.onSuccess {
                    Toast.makeText(this, "Bot stopped successfully", Toast.LENGTH_SHORT).show()
                    loadBotsData()
                }.onFailure { e ->
                    Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            NxtIndiaApiClient.startBot(this, token, bot.id) { res ->
                res.onSuccess {
                    Toast.makeText(this, "Bot started successfully", Toast.LENGTH_SHORT).show()
                    loadBotsData()
                }.onFailure { e ->
                    Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDeleteBot(bot: NxtBot) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Bot")
            .setMessage("Are you sure you want to delete ${bot.username}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val token = currentSession?.token ?: return@setPositiveButton
                NxtIndiaApiClient.deleteBot(this, token, bot.id) { res ->
                    res.onSuccess {
                        Toast.makeText(this, "Bot deleted", Toast.LENGTH_SHORT).show()
                        loadBotsData()
                    }.onFailure { e ->
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddBotDialog() {
        val input = EditText(this).apply {
            hint = "Enter Discord Bot Token..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(40, 32, 40, 32)
        }

        val container = FrameLayout(this).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(48, 24, 48, 16) }
            layoutParams = params
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Discord Bot")
            .setMessage("Paste your Discord Bot token to host it on NXT India:")
            .setView(container)
            .setPositiveButton("Add Bot") { _, _ ->
                val botToken = input.text.toString().trim()
                if (botToken.isEmpty()) {
                    Toast.makeText(this, "Token cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val token = currentSession?.token ?: return@setPositiveButton
                NxtIndiaApiClient.addBotToken(this, token, botToken) { res ->
                    res.onSuccess {
                        Toast.makeText(this, "Bot added successfully!", Toast.LENGTH_SHORT).show()
                        loadBotsData()
                    }.onFailure { e ->
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAccountSwitcherDialog() {
        val sessions = SessionManager.getSessions(this)
        val activeId = currentSession?.id

        val items = sessions.map { session ->
            val activeMark = if (session.id == activeId) " (Active)" else ""
            "${session.username}$activeMark\nID: ${session.id}"
        }.toMutableList().apply {
            add("+ Log in to another account")
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Switch Account / Session")
            .setItems(items) { _, which ->
                if (which == items.size - 1) {
                    startActivity(Intent(this, LoginActivity::class.java))
                } else {
                    val selected = sessions[which]
                    SessionManager.setActiveSession(this, selected.id)
                    loadActiveSession()
                    loadBotsData()
                    Toast.makeText(this, "Switched to ${selected.username}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showApiConfigDialog() {
        val currentUrl = NxtIndiaApiClient.getBaseUrl(this)
        val input = EditText(this).apply {
            setText(currentUrl)
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(40, 32, 40, 32)
        }

        val container = FrameLayout(this).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(48, 24, 48, 16) }
            layoutParams = params
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Configure NXT India Backend")
            .setMessage("Set backend API base URL (e.g. http://10.0.2.2:5000 or custom server):")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    NxtIndiaApiClient.setBaseUrl(this, newUrl)
                    Toast.makeText(this, "Backend URL updated", Toast.LENGTH_SHORT).show()
                    loadBotsData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
