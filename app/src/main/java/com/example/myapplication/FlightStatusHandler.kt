import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class FlightStatusHandler(private val apiKey: String) {

    sealed class FlightStatusResult {
        data class Success(
            val status: String,
            val departureTime: String,
            val arrivalTime: String,
            val delay: Int,
            val departureAirport: String,
            val arrivalAirport: String,
            val departureAirportCode: String,
            val arrivalAirportCode: String
        ) : FlightStatusResult() {
            fun isDelayed() = delay > 0
        }

        data class Error(val message: String) : FlightStatusResult()
    }

    fun getFlightStatus(flightNumber: String): FlightStatusResult {
        return try {
            val client = OkHttpClient()

            val url = "http://api.aviationstack.com/v1/flights?access_key=$apiKey&flight_iata=$flightNumber"

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return FlightStatusResult.Error("HTTP Error: ${response.code}")
            }

            val jsonResponse = response.body?.string() ?: return FlightStatusResult.Error("Empty response body")
            val jsonObject = JSONObject(jsonResponse)
            val dataArray = jsonObject.getJSONArray("data")

            if (dataArray.length() == 0) {
                return FlightStatusResult.Error("No data found for flight number $flightNumber")
            }

            val flightInfo = dataArray.getJSONObject(0)
            val flightStatus = flightInfo.getString("flight_status")
            val departure = flightInfo.getJSONObject("departure")
            val arrival = flightInfo.getJSONObject("arrival")

            val departureTime = departure.getString("scheduled")
            val arrivalTime = arrival.getString("scheduled")
            val delay = departure.optInt("delay", 0)
            val departureAirport = departure.getString("airport")
            val arrivalAirport = arrival.getString("airport")
            val departureAirportCode = departure.getString("iata")
            val arrivalAirportCode = arrival.getString("iata")


            FlightStatusResult.Success(
                status = flightStatus,
                departureTime = departureTime,
                arrivalTime = arrivalTime,
                delay = delay,
                departureAirport = departureAirport,
                arrivalAirport = arrivalAirport,
                departureAirportCode = departureAirportCode,
                arrivalAirportCode = arrivalAirportCode
            )
        } catch (e: Exception) {
            e.printStackTrace()
            FlightStatusResult.Error("Unexpected error: ${e.localizedMessage}")
        }
    }
}
