package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TravelerAdapter(private val travelers: List<Traveler>) :
    RecyclerView.Adapter<TravelerAdapter.TravelerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TravelerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_traveler_card, parent, false)
        return TravelerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TravelerViewHolder, position: Int) {
        val traveler = travelers[position]
        holder.bind(traveler)
    }

    override fun getItemCount() = travelers.size

    class TravelerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvTravelerName)
        private val tvAirline: TextView = itemView.findViewById(R.id.tvAirline)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvPnr: TextView = itemView.findViewById(R.id.tvPnr)

        fun bind(traveler: Traveler) {
            tvName.text = traveler.name
            tvAirline.text = "Airline: ${traveler.airline}"
            tvDestination.text = "To: ${traveler.destination}"
            tvPnr.text = "PNR: ${traveler.pnr}"
        }
    }
}