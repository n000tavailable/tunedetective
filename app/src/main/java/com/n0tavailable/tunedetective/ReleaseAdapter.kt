package com.n0tavailable.tunedetective

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReleaseAdapter(private val artistNames: ArrayList<String>) : RecyclerView.Adapter<ReleaseAdapter.ReleaseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReleaseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_release, parent, false)
        return ReleaseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReleaseViewHolder, position: Int) {
        val artistName = artistNames[position]
        holder.bind(artistName)
    }

    override fun getItemCount(): Int {
        return artistNames.size
    }

    inner class ReleaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artistNameTextView: TextView = itemView.findViewById(R.id.artistNameTextView)

        fun bind(artistName: String) {
            artistNameTextView.text = artistName
        }
    }
}