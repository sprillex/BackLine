package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.AiSkill

class AiSkillAdapter(
    private var skills: List<AiSkill>,
    private val isSkillEnabled: (String) -> Boolean,
    private val onToggleEnable: (AiSkill, Boolean) -> Unit,
    private val onItemLongClick: (AiSkill) -> Unit
) : RecyclerView.Adapter<AiSkillAdapter.AiSkillViewHolder>() {

    class AiSkillViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDisplayName: TextView = view.findViewById(R.id.tvSkillDisplayName)
        val tvSkillId: TextView = view.findViewById(R.id.tvSkillId)
        val tvSkillSummary: TextView = view.findViewById(R.id.tvSkillSummary)
        val switchEnabled: Switch = view.findViewById(R.id.switchSkillEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AiSkillViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_skill, parent, false)
        return AiSkillViewHolder(view)
    }

    override fun onBindViewHolder(holder: AiSkillViewHolder, position: Int) {
        val skill = skills[position]
        holder.tvDisplayName.text = skill.displayName
        holder.tvSkillId.text = "ID: ${skill.id}"
        holder.tvSkillSummary.text = skill.summary

        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = isSkillEnabled(skill.id)
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggleEnable(skill, isChecked)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(skill)
            true
        }
    }

    override fun getItemCount(): Int = skills.size

    fun updateSkills(newSkills: List<AiSkill>) {
        skills = newSkills
        notifyDataSetChanged()
    }
}
