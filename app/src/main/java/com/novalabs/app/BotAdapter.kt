package com.novalabs.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class BotAdapter(
    private var bots: List<NxtBot>,
    private val onToggle: (NxtBot) -> Unit,
    private val onDelete: (NxtBot) -> Unit
) : RecyclerView.Adapter<BotAdapter.BotViewHolder>() {

    class BotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBotName: TextView = view.findViewById(R.id.tvBotName)
        val tvBotStatus: TextView = view.findViewById(R.id.tvBotStatus)
        val tvBotMetrics: TextView = view.findViewById(R.id.tvBotMetrics)
        val btnToggleBot: MaterialButton = view.findViewById(R.id.btnToggleBot)
        val btnDeleteBot: MaterialButton = view.findViewById(R.id.btnDeleteBot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bot_session, parent, false)
        return BotViewHolder(view)
    }

    override fun onBindViewHolder(holder: BotViewHolder, position: Int) {
        val bot = bots[position]
        holder.tvBotName.text = bot.username

        if (bot.isBanned) {
            holder.tvBotStatus.text = "● Banned"
            holder.tvBotStatus.setTextColor(Color.parseColor("#F23F43"))
            holder.tvBotStatus.setBackgroundColor(Color.parseColor("#341818"))
            holder.btnToggleBot.isEnabled = false
            holder.btnToggleBot.text = "Banned"
        } else if (bot.isRunning) {
            holder.tvBotStatus.text = "● Running"
            holder.tvBotStatus.setTextColor(Color.parseColor("#23A55A"))
            holder.tvBotStatus.setBackgroundColor(Color.parseColor("#182A1B"))
            holder.btnToggleBot.isEnabled = true
            holder.btnToggleBot.text = "Stop"
            holder.btnToggleBot.setBackgroundColor(Color.parseColor("#DA373C"))
        } else {
            holder.tvBotStatus.text = "● Offline"
            holder.tvBotStatus.setTextColor(Color.parseColor("#80848E"))
            holder.tvBotStatus.setBackgroundColor(Color.parseColor("#202225"))
            holder.btnToggleBot.isEnabled = true
            holder.btnToggleBot.text = "Start"
            holder.btnToggleBot.setBackgroundColor(Color.parseColor("#5865F2"))
        }

        val ram = bot.ramUsage ?: "N/A"
        val cpu = bot.cpuUsage ?: "N/A"
        val ping = if (!bot.latency.isNullOrEmpty()) " • Ping: ${bot.latency}ms" else ""
        holder.tvBotMetrics.text = "RAM: $ram • CPU: $cpu$ping"

        holder.btnToggleBot.setOnClickListener { onToggle(bot) }
        holder.btnDeleteBot.setOnClickListener { onDelete(bot) }
    }

    override fun getItemCount(): Int = bots.size

    fun updateData(newBots: List<NxtBot>) {
        this.bots = newBots
        notifyDataSetChanged()
    }
}
