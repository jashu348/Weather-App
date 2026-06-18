package com.example.data.repository

import com.example.data.api.WeatherApiClient
import com.example.data.api.GeminiApiClient
import com.example.data.db.SavedLocation
import com.example.data.db.SavedLocationDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale

class WeatherRepository(private val dao: SavedLocationDao) {

    val allSavedLocations: Flow<List<SavedLocation>> = dao.getAllLocations()

    suspend fun saveLocation(location: SavedLocation) {
        dao.insertLocation(location)
    }

    suspend fun deleteLocation(location: SavedLocation) {
        dao.deleteLocation(location)
    }

    suspend fun deleteLocationById(id: String) {
        dao.deleteLocationById(id)
    }

    suspend fun updateGpsLocation(lat: Double, lon: Double, city: String?, country: String?) {
        dao.deleteGpsLocations()
        val gpsLoc = SavedLocation(
            id = "gps_current",
            name = city ?: "My Location",
            country = country ?: "Current Location",
            latitude = lat,
            longitude = lon,
            isFavorite = false,
            isGps = true
        )
        dao.insertLocation(gpsLoc)
    }

    suspend fun fetchForecast(latitude: Double, longitude: Double): WeatherResponse {
        return WeatherApiClient.weatherService.getForecast(latitude, longitude)
    }

    suspend fun searchCity(name: String): GeocodingResponse {
        return WeatherApiClient.geocodingService.searchCity(name)
    }

    suspend fun analyzeWeatherWithAi(
        locationName: String,
        current: CurrentWeather,
        weatherDesc: String,
        hourlyList: List<HourlyForecastItem>
    ): GeminiWeatherAnalysis {
        val hourlyStr = hourlyList.take(8).joinToString(", ") { item ->
            "${formatTimeToHour(item.time)}: ${item.temperature}°C (${getWmoDescription(item.weatherCode)})"
        }
        return GeminiApiClient.analyzeWeather(
            locationName = locationName,
            tempCelsius = current.temperature,
            humidityPct = current.humidity,
            apparentTempCelsius = current.apparentTemperature,
            isDay = current.isDay == 1,
            windSpeedKmh = current.windSpeed,
            weatherCodeDesc = weatherDesc,
            hourlySummary = hourlyStr
        )
    }

    // Helper to generate deterministic severe storm or hazard alerts locally
    fun generateDeterministicAlerts(current: CurrentWeather): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()

        // 1. Thunderstorm Alert (WMO 95, 96, 99)
        if (current.weatherCode in listOf(95, 96, 99)) {
            alerts.add(
                WeatherAlert(
                    title = "Severe Thunderstorm Warning",
                    description = "Active electrical lightning and convective storms. Stay indoors, disconnect electronics, and avoid water lines.",
                    severity = AlertSeverity.SEVERE,
                    parameter = "Thunderstorm / Lightning"
                )
            )
        }

        // 2. High Wind Gale Warning
        if (current.windSpeed >= 50.0) {
            alerts.add(
                WeatherAlert(
                    title = "Gale Force Wind Warning",
                    description = "Hazardous wind gusts exceeding ${current.windSpeed} km/h detected. Secure outdoor objects and expect potential power lines damage.",
                    severity = AlertSeverity.SEVERE,
                    parameter = "Wind Speed"
                )
            )
        } else if (current.windSpeed >= 30.0) {
            alerts.add(
                WeatherAlert(
                    title = "Blustery Wind Advisory",
                    description = "Strong sustained winds of ${current.windSpeed} km/h. High-profile vehicles should exercise caution.",
                    severity = AlertSeverity.WARNING,
                    parameter = "Wind Speed"
                )
            )
        }

        // 3. Excessive Heat Warning
        if (current.temperature >= 38.0) {
            alerts.add(
                WeatherAlert(
                    title = "Excessive Heat Warning",
                    description = "Extremely high temperatures of ${current.temperature}°C (Feels like ${current.apparentTemperature}°C). Risk of heat stroke! Hydrate persistently and stay in cooling systems.",
                    severity = AlertSeverity.SEVERE,
                    parameter = "Extreme Heat"
                )
            )
        } else if (current.temperature >= 32.0) {
            alerts.add(
                WeatherAlert(
                    title = "Heat Advisory",
                    description = "High temperatures of ${current.temperature}°C. Limit strenuous outdoor work and check on vulnerable neighbors.",
                    severity = AlertSeverity.WARNING,
                    parameter = "Heat"
                )
            )
        }

        // 4. Extreme Cold / Freeze Warning
        if (current.temperature <= -5.0) {
            alerts.add(
                WeatherAlert(
                    title = "Hard Freeze Warning",
                    description = "DANGEROUS sub-freezing temperature of ${current.temperature}°C. Exposed pipes could break, and danger of immediate frostbite exists.",
                    severity = AlertSeverity.SEVERE,
                    parameter = "Extreme Cold"
                )
            )
        } else if (current.temperature <= 1.0) {
            alerts.add(
                WeatherAlert(
                    title = "Frost Advisory",
                    description = "Near-freezing temperature of ${current.temperature}°C. Unprotected sensitive vegetation and livestock can suffer freeze damage.",
                    severity = AlertSeverity.WARNING,
                    parameter = "Frost"
                )
            )
        }

        // 5. Heavy Precipitation warning
        if (current.precipitation >= 15.0 || current.rain >= 15.0 || current.showers >= 15.0) {
            alerts.add(
                WeatherAlert(
                    title = "Flash Flood Warning",
                    description = "Heavy convective rainfall of ${current.precipitation} mm detected. Be wary of rising water in low-lying fields or streets. Turn around, don't drown!",
                    severity = AlertSeverity.SEVERE,
                    parameter = "Precipitation"
                )
            )
        } else if (current.precipitation >= 5.0 || current.rain >= 5.0 || current.showers >= 5.0) {
            alerts.add(
                WeatherAlert(
                    title = "Heavy Rainfall Advisory",
                    description = "Moderate to heavy rain observed. Reduce road driving speeds to avoid sudden hydroplaning.",
                    severity = AlertSeverity.WARNING,
                    parameter = "Precipitation"
                )
            )
        }

        // 6. Heavy Snow warning
        if (current.snowfall >= 8.0) {
            alerts.add(
                WeatherAlert(
                    title = "Heavy Snow / Blizzard Advisory",
                    description = "Rapid snow accumulation of ${current.snowfall} cm causing poor visibility blockages on transit. Limit non-essential travel.",
                    severity = AlertSeverity.SEVERE,
                    parameter = "Snowfall"
                )
            )
        }

        return alerts
    }

    companion object {
        fun getWmoDescription(code: Int): String {
            return when (code) {
                0 -> "Clear Sky"
                1 -> "Mainly Clear"
                2 -> "Partly Cloudy"
                3 -> "Overcast"
                45 -> "Fog"
                48 -> "Depositing Rime Fog"
                51 -> "Light Drizzle"
                53 -> "Moderate Drizzle"
                55 -> "Dense Drizzle"
                56 -> "Slight Freezing Drizzle"
                57 -> "Dense Freezing Drizzle"
                61 -> "Slight Rain"
                63 -> "Moderate Rain"
                65 -> "Heavy Rain"
                66 -> "Slight Freezing Rain"
                67 -> "Heavy Freezing Rain"
                71 -> "Slight Snowfall"
                73 -> "Moderate Snowfall"
                75 -> "Heavy Snowfall"
                77 -> "Snow Grains"
                80 -> "Slight Rain Showers"
                81 -> "Moderate Rain Showers"
                82 -> "Violent Rain Showers"
                85 -> "Slight Snow Showers"
                86 -> "Heavy Snow Showers"
                95 -> "Thunderstorm"
                96 -> "Thunderstorm with Slight Hail"
                99 -> "Thunderstorm with Heavy Hail"
                else -> "Unknown weather code"
            }
        }

        fun formatTimeToHour(isoTime: String): String {
            return try {
                // Open-Meteo uses ISO-8601 strings like "2026-06-18T05:00"
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
                val formatter = SimpleDateFormat("h:mm a", Locale.US)
                val date = parser.parse(isoTime)
                if (date != null) formatter.format(date) else isoTime
            } catch (e: Exception) {
                // If it fails, strip the date part. Example: "2026-06-18T05:00" -> "05:00"
                if (isoTime.contains("T")) isoTime.substringAfter("T") else isoTime
            }
        }
    }
}
