import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
import android.util.Log
import com.example.myapplication.BorzoModels
import com.google.firebase.firestore.SetOptions

object BorzoFirestoreHelper {
    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "BORZO_FIRESTORE"
    private var uniKey = ""
    fun saveOrderResponse(response: BorzoModels.BorzoOrderResponse, uniqueKey: String) {
        try {
            uniKey = uniqueKey;
            val batch = db.batch()
            val orderRef = db.collection("borzo_orders").document(response.order.orderId.toString())

            // Convert complete order data
            val orderData = convertOrderResponseToMap(response)
            batch.set(orderRef, orderData, SetOptions.merge())

            // Save all points as subcollection
            response.order.points.forEach { point ->
                val pointRef = orderRef.collection("points").document(point.pointId)
                batch.set(pointRef, convertPointToMap(point))
            }

            // Save courier if exists
            response.order.courier?.let { courier ->
                val courierId = courier.courierId ?: "unknown_${System.currentTimeMillis()}"
                val courierRef = orderRef.collection("couriers").document(courierId)
                batch.set(courierRef, convertCourierToMap(courier))
            }

            // Commit batch with fallback
            batch.commit().addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "Batch commit failed", task.exception)
                    saveMainOrderOnly(response)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing batch", e)
            saveMainOrderOnly(response)
        }
    }

    private fun saveMainOrderOnly(response: BorzoModels.BorzoOrderResponse) {
        val simplifiedOrder = mapOf<String, Any>(
            "orderId" to response.order.orderId,
            "isSuccessful" to response.isSuccessful,
            "status" to response.order.status,
            "firestoreTimestamp" to FieldValue.serverTimestamp(),
            "failedAt" to System.currentTimeMillis(),
            "uniqueKey" to uniKey
        )

        db.collection("borzo_orders_emergency")
            .document(response.order.orderId.toString())
            .set(simplifiedOrder)
            .addOnFailureListener { e ->
                Log.e(TAG, "Emergency save failed", e)
            }
    }

    private fun convertOrderResponseToMap(response: BorzoModels.BorzoOrderResponse): Map<String, Any> {
        return mapOf<String, Any>(
            "uniqueKey" to uniKey,
            "isSuccessful" to response.isSuccessful,
            "errors" to (response.errors ?: emptyList<String>()),
            "parameterErrors" to (response.parameterErrors ?: emptyMap<String, List<String>>()),
            "order" to convertOrderDetailToMap(response.order),
            "firestoreTimestamp" to FieldValue.serverTimestamp()
        )
    }

    private fun convertOrderDetailToMap(order: BorzoModels.OrderDetail): Map<String, Any> {
        return mapOf<String, Any>(
            "orderId" to order.orderId,
            "orderName" to order.orderName,
            "type" to order.type,
            "vehicleTypeId" to order.vehicleTypeId,
            "createdDatetime" to order.createdDatetime,
            "finishDatetime" to (order.finishDatetime ?: ""),
            "status" to order.status,
            "statusDescription" to order.statusDescription,
            "matter" to order.matter,
            "totalWeightKg" to order.totalWeightKg,
            "isClientNotificationEnabled" to order.isClientNotificationEnabled,
            "isContactPersonNotificationEnabled" to order.isContactPersonNotificationEnabled,
            "loadersCount" to order.loadersCount,
            "backpaymentDetails" to (order.backpaymentDetails ?: ""),
            "paymentAmount" to order.paymentAmount,
            "deliveryFeeAmount" to order.deliveryFeeAmount,
            "weightFeeAmount" to order.weightFeeAmount,
            "insuranceAmount" to order.insuranceAmount,
            "insuranceFeeAmount" to order.insuranceFeeAmount,
            "loadingFeeAmount" to order.loadingFeeAmount,
            "moneyTransferFeeAmount" to order.moneyTransferFeeAmount,
            "doorToDoorFeeAmount" to order.doorToDoorFeeAmount,
            "promoCodeDiscountAmount" to order.promoCodeDiscountAmount,
            "backpaymentAmount" to order.backpaymentAmount,
            "codFeeAmount" to order.codFeeAmount,
            "returnFeeAmount" to order.returnFeeAmount,
            "waitingFeeAmount" to order.waitingFeeAmount,
            "backpaymentPhotoUrl" to (order.backpaymentPhotoUrl ?: ""),
            "itineraryDocumentUrl" to (order.itineraryDocumentUrl ?: ""),
            "waybillDocumentUrl" to (order.waybillDocumentUrl ?: ""),
            "isMotoboxRequired" to order.isMotoboxRequired,
            "isThermoboxRequired" to order.isThermoboxRequired,
            "paymentMethod" to order.paymentMethod,
            "bankCardId" to (order.bankCardId ?: ""),
            "appliedPromoCode" to (order.appliedPromoCode ?: ""),
            "isReturnRequired" to order.isReturnRequired,
            "clientId" to order.clientId
        )
    }

    private fun convertPointToMap(point: BorzoModels.OrderPoint): Map<String, Any> {
        return mapOf<String, Any>(
            "pointId" to point.pointId,
            "pointType" to point.pointType,
            "deliveryId" to (point.deliveryId ?: ""),
            "clientOrderId" to (point.clientOrderId ?: ""),
            "address" to point.address,
            "latitude" to point.latitude,
            "longitude" to point.longitude,
            "requiredStartDatetime" to point.requiredStartDatetime,
            "requiredFinishDatetime" to point.requiredFinishDatetime,
            "arrivalStartDatetime" to (point.arrivalStartDatetime ?: ""),
            "arrivalFinishDatetime" to (point.arrivalFinishDatetime ?: ""),
            "estimatedArrivalDatetime" to (point.estimatedArrivalDatetime ?: ""),
            "courierVisitDatetime" to (point.courierVisitDatetime ?: ""),
            "contactPerson" to mapOf<String, Any>(
                "name" to point.contactPerson.name,
                "phone" to point.contactPerson.phone
            ),
            "takingAmount" to point.takingAmount,
            "buyoutAmount" to point.buyoutAmount,
            "note" to (point.note ?: ""),
            "previousPointDrivingDistanceMeters" to point.previousPointDrivingDistanceMeters,
            "packages" to point.packages.map { pkg ->
                mapOf<String, Any>(
                    "packageId" to (pkg.packageId ?: ""),
                    "weightKg" to (pkg.weightKg ?: 0.0),
                    "description" to (pkg.description ?: "")
                )
            },
            "isCodCashVoucherRequired" to point.isCodCashVoucherRequired,
            "placePhotoUrl" to (point.placePhotoUrl ?: ""),
            "signPhotoUrl" to (point.signPhotoUrl ?: ""),
            "trackingUrl" to point.trackingUrl,
            "checkinCode" to (point.checkinCode ?: ""),
            "checkin" to (point.checkin ?: ""),
            "isReturnPoint" to point.isReturnPoint,
            "isOrderPaymentHere" to point.isOrderPaymentHere,
            "buildingNumber" to (point.buildingNumber ?: ""),
            "entranceNumber" to (point.entranceNumber ?: ""),
            "intercomCode" to (point.intercomCode ?: ""),
            "floorNumber" to (point.floorNumber ?: ""),
            "apartmentNumber" to (point.apartmentNumber ?: ""),
            "invisibleMileNavigationInstructions" to (point.invisibleMileNavigationInstructions ?: ""),
            "delivery" to (point.delivery?.let { delivery ->
                mapOf<String, Any>(
                    "status" to delivery.status
                )
            } ?: emptyMap<String, Any>())
        )
    }

    private fun convertCourierToMap(courier: BorzoModels.Courier): Map<String, Any> {
        return mapOf<String, Any>(
            "courierId" to (courier.courierId ?: "unknown"),
            "name" to (courier.name ?: ""),
            "phone" to (courier.phone ?: ""),
            "vehicleType" to (courier.vehicleType ?: ""),
            "vehicleNumber" to (courier.vehicleNumber ?: ""),
            "rating" to (courier.rating ?: 0.0),
            "photoUrl" to (courier.photoUrl ?: ""),
            "currentLocation" to (courier.currentLocation?.let { loc ->
                mapOf<String, Any>(
                    "latitude" to loc.latitude,
                    "longitude" to loc.longitude,
                    "timestamp" to (loc.timestamp ?: "")
                )
            } ?: emptyMap<String, Any>())
        )
    }
}