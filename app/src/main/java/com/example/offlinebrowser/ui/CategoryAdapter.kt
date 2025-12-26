package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R

class CategoryAdapter(
    private val onCategoryClick: (String) -> Unit
) : ListAdapter<String, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_category_title)
        val tvViewAll: TextView = view.findViewById(R.id.tv_view_all)
        val container: View = view.findViewById(R.id.category_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = getItem(position)
        holder.tvTitle.text = category

        holder.container.setOnClickListener {
            onCategoryClick(category)
        }

        // "View All" acts same as clicking the container for now, or could be separate
        holder.tvViewAll.setOnClickListener {
            onCategoryClick(category)
        }
    }
}

class CategoryDiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}
