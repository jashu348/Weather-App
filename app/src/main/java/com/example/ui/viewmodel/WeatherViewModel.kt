package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.SavedLocation
import com.example.data.model.*
import com.example.data.repository.WeatherRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(
        val locationName: String,
        val country: String?,
        val current: CurrentWeather,
        val hourlyList: List<HourlyForecastItem>,
        val dailyList: List<DailyForecastItem>,
        val localAlerts: List<WeatherAlert>,
        val aiAnalysis: GeminiWeatherAnalysis,
        val latitude: Double,
        val longitude: Double,
        val isGpsSelected: Boolean
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _gpsPermissionGranted = MutableStateFlow(false)
    val gpsPermissionGranted: StateFlow<Boolean> = _gpsPermissionGranted.asStateFlow()

    // Expose saved locations directly from Room DB
    val savedLocations: StateFlow<List<SavedLocation>> = repository.allSavedLocations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Fetch default weather (New York) temporarily, and if Location is approved, we will update with Location!
        // This solves the cold-start blank screen issue beautifully.
        loadWeather(40.7128, -74.0060, "New York", "United States", isGps = false)
    }

    fun setGpsPermissionGranted(granted: Boolean) {
        _gpsPermissionGranted.value = granted
    }

    fun searchCity(query: String) {
        _searchQuery.value = query
        if (query.trim().length >= 2) {
            _isSearching.value = true
            viewModelScope.launch {
                try {
                    val response = repository.searchCity(query.trim())
                    _searchResults.value = response.results ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isSearching.value = false
                }
            }
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun loadWeather(
        lat: Double,
        lon: Double,
        name: String,
        country: String?,
        isGps: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                _searchQuery.value = ""
                _searchResults.value = emptyList()

                // 1. Fetch weather forecast from Open-Meteo
                val forecast = repository.fetchForecast(lat, lon)
                val current = forecast.current ?: throw Exception("Current weather details are unavailable.")

                // 2. Parse hourly items
                val hourlyList = mutableListOf<HourlyForecastItem>()
                forecast.hourly?.let { hourly ->
                    val nowMillis = System.currentTimeMillis()
                    // Filter or keep up to 24 upcoming hours
                    for (i in hourly.time.indices) {
                        // Let's parse time
                        val timeStr = hourly.time[i]
                        val formatParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
                        val timeMillis = formatParser.parse(timeStr)?.time ?: 0L

                        // Keep items that are +/- few hours or in future. To be simple, we grab 24 items starting from current hour
                        hourlyList.add(
                            HourlyForecastItem(
                                time = timeStr,
                                temperature = hourly.temperature[i],
                                weatherCode = hourly.weatherCode[i],
                                humidity = hourly.humidity[i],
                                precipitationProbability = hourly.precipitationProbability[i]
                            )
                        )
                    }
                }

                // Subset hourly list to future 24 items
                val upcomingHourly = getUpcoming24Hours(hourlyList)

                // 2.5 Parse daily items
                val dailyList = mutableListOf<DailyForecastItem>()
                forecast.daily?.let { daily ->
                    for (i in daily.time.indices) {
                        dailyList.add(
                            DailyForecastItem(
                                date = daily.time[i],
                                weatherCode = daily.weatherCode.getOrNull(i) ?: 0,
                                tempMax = daily.temperatureMax.getOrNull(i) ?: 0.0,
                                tempMin = daily.temperatureMin.getOrNull(i) ?: 0.0,
                                precipitationProbability = daily.precipitationProbabilityMax.getOrNull(i) ?: 0
                            )
                        )
                    }
                }

                // 3. Generate deterministic severe warnings programmatically
                val localAlerts = repository.generateDeterministicAlerts(current)

                // 4. Fetch Gemini AI rich weather and clothing advisor analysis
                val weatherDesc = WeatherRepository.getWmoDescription(current.weatherCode)
                val aiAnalysis = repository.analyzeWeatherWithAi(
                    locationName = name,
                    current = current,
                    weatherDesc = weatherDesc,
                    hourlyList = upcomingHourly
                )

                // 5. Update success state
                _uiState.value = WeatherUiState.Success(
                    locationName = name,
                    country = country,
                    current = current,
                    hourlyList = upcomingHourly,
                    dailyList = dailyList,
                    localAlerts = localAlerts,
                    aiAnalysis = aiAnalysis,
                    latitude = lat,
                    longitude = lon,
                    isGpsSelected = isGps
                )

                // Save or insert current location as last visited if not duplicate of current GPS
                if (!isGps) {
                    val entity = SavedLocation(
                        id = "${lat}_${lon}",
                        name = name,
                        country = country,
                        latitude = lat,
                        longitude = lon,
                        isFavorite = false,
                        isGps = false,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.saveLocation(entity)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = WeatherUiState.Error(e.localizedMessage ?: "Failed to load weather forecast.")
            }
        }
    }

    private fun getUpcoming24Hours(allHourly: List<HourlyForecastItem>): List<HourlyForecastItem> {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val currentHourStr = formatter.format(Date()).substringBefore(":") + ":00"
        val currentHourIndex = allHourly.indexOfFirst { it.time.contains(currentHourStr) }
        val startIndex = if (currentHourIndex != -1) currentHourIndex else 0
        return allHourly.drop(startIndex).take(24)
    }

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(context: Context) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        viewModelScope.launch {
                            // Fetch names if possible, but Open-Meteo or general fallback location name "My Location" is great!
                            val lat = location.latitude
                            val lon = location.longitude
                            repository.updateGpsLocation(lat, lon, "My Location", "Current Area")
                            loadWeather(lat, lon, "My Location", "Current Area", isGps = true)
                        }
                    } else {
                        // fallback to last generic location or default New York
                        _uiState.value = WeatherUiState.Error("Real-time GPS coordinates are temporarily empty. Please ensure location sensors are active, or search your city manually below.")
                    }
                }.addOnFailureListener {
                    _uiState.value = WeatherUiState.Error("GPS Location request failed: ${it.localizedMessage}. Search manually below.")
                }
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("Could not retrieve GPS coordinates: ${e.localizedMessage}")
            }
        }
    }

    fun toggleFavorite(lat: Double, lon: Double, name: String, country: String?, isFav: Boolean) {
        viewModelScope.launch {
            val key = "${lat}_${lon}"
            val entity = SavedLocation(
                id = key,
                name = name,
                country = country,
                latitude = lat,
                longitude = lon,
                isFavorite = isFav,
                isGps = false,
                timestamp = System.currentTimeMillis()
            )
            repository.saveLocation(entity)
        }
    }

    fun deleteSavedLocation(id: String) {
        viewModelScope.launch {
            repository.deleteLocationById(id)
        }
    }
}

class WeatherViewModelFactory(private val repository: WeatherRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
