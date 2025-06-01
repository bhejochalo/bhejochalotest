package com.example.myapplication

import java.util.Date

/**
 * Contains all data models for Borzo API responses and database storage
 */
object BorzoModels {

    // Top-level API response model
    data class BorzoOrderResponse(
        val isSuccessful: Boolean,
        val errors: List<String>? = null,
        val parameterErrors: Map<String, List<String>>? = null,
        val order: OrderDetail,
        val firestoreTimestamp: Date = Date()
    )

    // Order details model
    data class OrderDetail(
        val orderId: String,
        val orderName: String,
        val type: String,
        val vehicleTypeId: Int,
        val createdDatetime: String,
        val finishDatetime: String? = null,
        val status: String,
        val statusDescription: String,
        val matter: String,
        val totalWeightKg: Double,
        val isClientNotificationEnabled: Boolean,
        val isContactPersonNotificationEnabled: Boolean,
        val loadersCount: Int,
        val backpaymentDetails: String? = null,
        val points: List<OrderPoint>,
        val paymentAmount: Double,
        val deliveryFeeAmount: Double,
        val weightFeeAmount: Double,
        val insuranceAmount: Double,
        val insuranceFeeAmount: Double,
        val loadingFeeAmount: Double,
        val moneyTransferFeeAmount: Double,
        val doorToDoorFeeAmount: Double,
        val promoCodeDiscountAmount: Double,
        val backpaymentAmount: Double,
        val codFeeAmount: Double,
        val returnFeeAmount: Double,
        val waitingFeeAmount: Double,
        val backpaymentPhotoUrl: String? = null,
        val itineraryDocumentUrl: String? = null,
        val waybillDocumentUrl: String? = null,
        val courier: Courier? = null,
        val isMotoboxRequired: Boolean = false,
        val isThermoboxRequired: Boolean = false,
        val paymentMethod: String,
        val bankCardId: String? = null,
        val appliedPromoCode: String? = null,
        val isReturnRequired: Boolean = false,
        val clientId: String
    )

    // Delivery point model
    data class OrderPoint(
        val pointId: String,
        val pointType: String,
        val deliveryId: String? = null,
        val clientOrderId: String? = null,
        val address: String,
        val latitude: String,
        val longitude: String,
        val requiredStartDatetime: String,
        val requiredFinishDatetime: String,
        val arrivalStartDatetime: String? = null,
        val arrivalFinishDatetime: String? = null,
        val estimatedArrivalDatetime: String? = null,
        val courierVisitDatetime: String? = null,
        val contactPerson: ContactPerson,
       // val takingAmount: Double,
       // val buyoutAmount: Double,
        val note: String? = null,
      //  val previousPointDrivingDistanceMeters: Int,
        val packages: List<Package> = emptyList(),
        val isCodCashVoucherRequired: Boolean = false,
        val placePhotoUrl: String? = null,
        val signPhotoUrl: String? = null,
        val trackingUrl: String,
        val checkinCode: String? = null,
        val checkin: String? = null,
        val isReturnPoint: Boolean = false,
        val isOrderPaymentHere: Boolean = false,
        val buildingNumber: String? = null,
        val entranceNumber: String? = null,
        val intercomCode: String? = null,
        val floorNumber: String? = null,
        val apartmentNumber: String? = null,
        val invisibleMileNavigationInstructions: String? = null,
        val delivery: DeliveryStatus? = null
    )

    // Contact person model
    data class ContactPerson(
        val name: String,
        val phone: String
    )

    // Package model
    data class Package(
        val packageId: String? = null,
        val weightKg: Double? = null,
        val lengthCm: Double? = null,
        val widthCm: Double? = null,
        val heightCm: Double? = null,
        val description: String? = null
    )

    // Delivery status model
    data class DeliveryStatus(
        val status: String,
        val description: String? = null,
        val updatedAt: String? = null
    )

    // Courier model
    data class Courier(
        val courierId: String? = null,
        val name: String? = null,
        val phone: String? = null,
        val vehicleType: String? = null,
        val vehicleNumber: String? = null,
        val photoUrl: String? = null,
        val rating: Double? = null
    )

    // Request model for creating orders
    data class CreateOrderRequest(
        val type: String = "standard",
        val matter: String,
        val vehicleTypeId: Int,
        val points: List<OrderPointRequest>,
        val orderId: String? = null,
        val scheduledAt: String? = null,
        val note: String? = null
    )

    // Order point request model
    data class OrderPointRequest(
        val address: String,
        val contactPerson: ContactPerson,
        val requiredStartDatetime: String? = null,
        val requiredFinishDatetime: String? = null,
        val note: String? = null
    )
}