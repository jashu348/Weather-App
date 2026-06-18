package com.example.data.api

import com.example.MainActivity
import com.example.BuildConfig
import com.example.data.model.GeminiWeatherAnalysis
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}

object GeminiApiClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun analyzeWeather(
        locationName: String,
        tempCelsius: Double,
        humidityPct: Double,
        apparentTempCelsius: Double,
        isDay: Boolean,
        windSpeedKmh: Double,
        weatherCodeDesc: String,
        hourlySummary: String
    ): GeminiWeatherAnalysis {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getFallbackAnalysis(tempCelsius, weatherCodeDesc)
        }

        val prompt = """
            You are a meteorological expert and outdoor clothing advisor. Direct your report to a resident in $locationName.
            Current Conditions:
            - Temperature: $tempCelsius°C (Apparent Feels-Like: $apparentTempCelsius°C)
            - Relative Humidity: $humidityPct%
            - Wind Speed: $windSpeedKmh km/h
            - Weather: $weatherCodeDesc
            - Time of Day: ${if (isDay) "Day" else "Night"}
            - Hourly trend for the day: $hourlySummary

            Output a valid JSON object matching this schema. STRICTLY return only valid JSON without markdown wrapping (no ```json or similar). Keep responses clear, professional, warm, and highly informative:
            {
              "generalDescription": "A beautiful and descriptive summary of current weather conditions tailored to the location, highlighting temperature spikes, dampness, or clear conditions.",
              "severeConditionsAlert": "A concise severe storm warning or hazard notification ONLY if hazardous weather exists (e.g., gusty winds over 40km/h, extreme temperatures below 0°C or above 38°C, thunderstorms, heavy snow). Otherwise, return null.",
              "severeConditionsSeverity": "One of: NONE, INFO, WARNING, SEVERE",
              "clothingAdvice": "Practical, step-by-step attire guide for going out right now (e.g. layering, rain gear, windbreakers, thermal underwear, sunblock, sunglasses, footwear).",
              "dynamicTip": "A friendly daily life or outdoor activity suggestion (e.g. 'Excellent day for a jog!' or 'Better stay inside and keep cozy. Use high wind precautions.')"
            }
        """.trimIndent()

        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.3,
                responseMimeType = "application/json"
            )
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty candidates array from Gemini")

            val adapter = moshi.adapter(GeminiWeatherAnalysis::class.java)
            adapter.fromJson(jsonText) ?: throw Exception("Failed to deserialize Gemini response")
        } catch (e: Exception) {
            e.printStackTrace()
            // Clean up backticks in case model didn't follow the JSON MIME request properly
            try {
                val jsonTextCleaned = responseTextCleaner(e.message ?: "")
                if (jsonTextCleaned.isNotEmpty()) {
                    val adapter = moshi.adapter(GeminiWeatherAnalysis::class.java)
                    adapter.fromJson(jsonTextCleaned)!!
                } else {
                    getFallbackAnalysis(tempCelsius, weatherCodeDesc)
                }
            } catch (inner: Exception) {
                getFallbackAnalysis(tempCelsius, weatherCodeDesc)
            }
        }
    }

    private fun responseTextCleaner(raw: String): String {
        var txt = raw.trim()
        if (txt.startsWith("```json")) {
            txt = txt.removePrefix("```json").trim()
        } else if (txt.startsWith("```")) {
            txt = txt.removePrefix("```").trim()
        }
        if (txt.endsWith("```")) {
            txt = txt.removeSuffix("```").trim()
        }
        return txt
    }

    private fun getFallbackAnalysis(tempCelsius: Double, weatherCodeDesc: String): GeminiWeatherAnalysis {
        val clothing = when {
            tempCelsius <= 5 -> "Heavy winter jacket, thick scarf, gloves, and insulated beanies. Layer up heavily!"
            tempCelsius in 6.0..15.0 -> "A warm sweater or wool cardigan with a windbreaker or medium-weight jacket."
            tempCelsius in 16.0..22.0 -> "Comfortable long sleeves, active hoodie, or a light cardigan with chinos/denim."
            tempCelsius in 23.0..28.0 -> "Breathable cotton t-shirt with shorts or light trousers. Perfect pleasant attire."
            else -> "Super light, heat-managing sleeveless tops, linen wear, sunglasses, a sun hat, and plenty of sunscreen."
        }

        val dynamicTip = when {
            weatherCodeDesc.contains("rain", ignoreCase = true) || weatherCodeDesc.contains("shower", ignoreCase = true) ->
                "Don't forget your umbrella today, and choose waterproof boots to stay completely dry!"
            weatherCodeDesc.contains("thunderstorm", ignoreCase = true) || weatherCodeDesc.contains("snow", ignoreCase = true) ->
                "Expect potential disruptions. Secure outdoor items and favor comforting indoor exercises."
            tempCelsius >= 32.0 ->
                "Stay out of direct sunlight, seek air-conditioned environments, and drink plenty of water."
            else -> "A fantastic day to enjoy local walks, stay active, and embrace the outdoors."
        }

        val defaultGeneral = "Currently experiencing $weatherCodeDesc with temperatures hovering around ${tempCelsius}°C. Ideal for local adaptive planning."

        return GeminiWeatherAnalysis(
            generalDescription = defaultGeneral,
            severeConditionsAlert = if (tempCelsius <= 0.0) "Freezing temperature hazard! Prepare for icy surfaces." else null,
            severeConditionsSeverity = if (tempCelsius <= 0.0) "WARNING" else "NONE",
            clothingAdvice = clothing,
            dynamicTip = dynamicTip
        )
    }
}
