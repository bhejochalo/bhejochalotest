package com.example.myapplication

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TravelerAdapter(private val travelers: MutableList<Traveler>) :
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

    inner class TravelerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvTravelerName)
        private val tvAirline: TextView = itemView.findViewById(R.id.tvAirline)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvPnr: TextView = itemView.findViewById(R.id.tvPnr)
        private val btnBook: Button = itemView.findViewById(R.id.btnBook)

        fun bind(traveler: Traveler) {
            tvName.text = traveler.name
            tvAirline.text = "Airline: ${traveler.airline}"
            tvDestination.text = "To: ${traveler.destination}"
            tvPnr.text = "PNR: ${traveler.pnr}"

            when (traveler.bookingStatus) {
                "available" -> {
                    btnBook.text = "Book"
                    btnBook.isEnabled = true
                    btnBook.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.green_500))
                }
                "pending" -> {
                    btnBook.text = "Pending"
                    btnBook.isEnabled = false
                    btnBook.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.grey_500))
                }
                "booked" -> {
                    btnBook.text = "Booked"
                    btnBook.isEnabled = false
                    btnBook.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.grey_500))
                }
            }

            btnBook.setOnClickListener {
                if (traveler.bookingStatus == "available") {
                    showConfirmationDialog(traveler)
                }
            }
        }

        private fun showConfirmationDialog(traveler: Traveler) {
            AlertDialog.Builder(itemView.context)
                .setTitle("Confirm Booking")
                .setMessage("Are you sure you want to book with ${traveler.name}?")
                .setPositiveButton("Yes") { dialog, which ->
                    traveler.bookingStatus = "pending"
                    notifyItemChanged(adapterPosition)
                    // Here you would typically also update the booking status in your database
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}