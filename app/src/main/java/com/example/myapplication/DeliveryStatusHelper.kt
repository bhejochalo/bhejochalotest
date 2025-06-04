package com.example.myapplication

class DeliveryStatusHelper {
    companion object {
        private val deliveryStatusMessages = mapOf(
            "invalid" to "Invalid draft delivery",
            "draft" to "Draft delivery",
            "planned" to "Planned delivery (No courier assigned)",
            "active" to "Delivery in process (Courier on the way)",
            "finished" to "Delivery finished (Courier delivered the parcel)",
            "canceled" to "Delivery canceled",
            "delayed" to "Delivery delayed",
            "courier_assigned" to "Courier assigned, but still not departed",
            "courier_departed" to "Courier departed to the pick-up point",
            "courier_at_pickup" to "Courier at the pick-up point",
            "parcel_picked_up" to "Courier took parcel at the pick-up point",
            "courier_arrived" to "Courier has arrived and is waiting for a customer",
            "deleted" to "Delivery deleted",
            "return_planned" to "Return planned",
            "return_courier_assigned" to "Courier assigned to return delivery",
            "return_courier_departed" to "Courier departed for return delivery",
            "return_courier_picked_up" to "Courier picked up return delivery",
            "return_finished" to "Delivery is returned",
            "reattempt_planned" to "Reattempt planned",
            "reattempt_courier_assigned" to "Courier assigned to reattempt delivery",
            "reattempt_courier_departed" to "Courier departed for reattempt delivery",
            "reattempt_courier_picked_up" to "Courier picked up reattempt delivery",
            "reattempt_finished" to "Reattempt is finished (delivery finished)"
        )

        fun getStatusMessage(status: String): String {
            return deliveryStatusMessages[status] ?: "Unknown delivery status"
        }
    }
}