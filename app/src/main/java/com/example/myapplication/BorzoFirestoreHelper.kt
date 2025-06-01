import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
import android.util.Log
import com.example.myapplication.BorzoModels
import com.google.firebase.firestore.SetOptions
object BorzoFirestoreHelper {
    private val db = FirebaseFirestore.getInstance()

    fun saveOrderResponse(response: BorzoModels.BorzoOrderResponse) {
        try {
            val batch = db.batch()
            val orderRef = db.collection("borzo_orders").document(response.order.orderId)

            // Explicitly declare map types
            val orderData = mapOf<String, Any>(
                "isSuccessful" to response.isSuccessful,
                "errors" to (response.errors ?: emptyList<String>()),
                "parameterErrors" to (response.parameterErrors ?: emptyMap<String, List<String>>()),
                "order" to convertOrderToMap(response.order),
                "firestoreTimestamp" to FieldValue.serverTimestamp()
            )
            batch.set(orderRef, orderData, SetOptions.merge())

            response.order.points.forEach { point ->
                val pointRef = orderRef.collection("points").document(point.pointId)
                batch.set(pointRef, convertPointToMap(point))
            }

            response.order.courier?.let { courier ->
                val courierId = courier.courierId ?: "unknown_${System.currentTimeMillis()}"
                val courierRef = orderRef.collection("couriers").document(courierId)
                batch.set(courierRef, convertCourierToMap(courier))
            }

            batch.commit().addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("BORZO_FIRESTORE", "Batch commit failed", task.exception)
                    saveMainOrderOnly(response)
                }
            }
        } catch (e: Exception) {
            Log.e("BORZO_FIRESTORE", "Error preparing batch", e)
            saveMainOrderOnly(response)
        }
    }

    private fun saveMainOrderOnly(response: BorzoModels.BorzoOrderResponse) {
        val simplifiedOrder = mapOf<String, Any>(
            "orderId" to response.order.orderId,
            "isSuccessful" to response.isSuccessful,
            "status" to response.order.status,
            "firestoreTimestamp" to FieldValue.serverTimestamp()
        )

        db.collection("borzo_orders_emergency")
            .document(response.order.orderId)
            .set(simplifiedOrder)
            .addOnFailureListener { e ->
                Log.e("BORZO_FIRESTORE", "Emergency save failed", e)
            }
    }

    // Explicit return types for conversion functions
    private fun convertOrderToMap(order: BorzoModels.OrderDetail): Map<String, Any> {
        return mapOf<String, Any>(
            "orderId" to order.orderId,
            "orderName" to order.orderName,
            "type" to order.type,
            "vehicleTypeId" to order.vehicleTypeId,
            "createdDatetime" to order.createdDatetime,
            "status" to order.status,
            "statusDescription" to order.statusDescription,
            "paymentAmount" to order.paymentAmount,
            "clientId" to order.clientId
        )
    }

    private fun convertPointToMap(point: BorzoModels.OrderPoint): Map<String, Any> {
        return mapOf<String, Any>(
            "pointId" to point.pointId,
            "pointType" to point.pointType,
            "address" to point.address,
            "contactPerson" to mapOf<String, Any>(
                "name" to point.contactPerson.name,
                "phone" to point.contactPerson.phone
            ),
            "packages" to point.packages.map { pkg ->
                mapOf<String, Any>(
                    "packageId" to (pkg.packageId ?: ""),
                    "weightKg" to (pkg.weightKg ?: 0.0),
                    "description" to (pkg.description ?: "")
                )
            }
        )
    }

    private fun convertCourierToMap(courier: BorzoModels.Courier): Map<String, Any> {
        return mapOf<String, Any>(
            "courierId" to (courier.courierId ?: "unknown"),
            "name" to (courier.name ?: ""),
            "phone" to (courier.phone ?: ""),
            "vehicleType" to (courier.vehicleType ?: "unknown"),
            "rating" to (courier.rating ?: 0.0)
        )
    }
}