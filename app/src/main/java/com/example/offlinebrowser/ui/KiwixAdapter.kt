package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.KiwixBook

class KiwixAdapter(
    private var books: List<KiwixBook> = emptyList(),
    private val onDownloadClick: (KiwixBook) -> Unit
) : RecyclerView.Adapter<KiwixAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvLanguage: TextView = view.findViewById(R.id.tvLanguage)
        val tvSize: TextView = view.findViewById(R.id.tvSize)
        val btnDownload: Button = view.findViewById(R.id.btnDownload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kiwix_book, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = books[position]
        holder.tvTitle.text = book.title
        holder.tvDescription.text = book.description
        holder.tvLanguage.text = book.language
        holder.tvSize.text = book.size
        holder.btnDownload.setOnClickListener { onDownloadClick(book) }
    }

    override fun getItemCount() = books.size

    fun submitList(newBooks: List<KiwixBook>) {
        books = newBooks
        notifyDataSetChanged()
    }
}
