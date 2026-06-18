package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.SavedLocation
import com.example.data.model.*
import com.example.data.repository.WeatherRepository
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.viewmodel.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val savedLocs by viewModel.savedLocations.collectAsStateWithLifecycle()
    val gpsPermissionGranted by viewModel.gpsPermissionGranted.collectAsStateWithLifecycle()

    // Activity launcher for Location permission
    val locationPermissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setGpsPermissionGranted(granted)
        if (granted) {
            viewModel.requestCurrentLocation(context)
        }
    }

    // Colors mapping based on the active weather or time of day
    val backgroundBrush = when (val state = uiState) {
        is WeatherUiState.Success -> {
            val code = state.current.weatherCode
            val isDay = state.current.isDay == 1
            val isSevere = state.localAlerts.any { it.severity == AlertSeverity.SEVERE }

            when {
                isSevere -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF4A1521), Color(0xFF1F1115))
                )
                code in listOf(95, 96, 99) -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF2B1B4A), Color(0xFF10091D))
                )
                code in listOf(61, 63, 65, 80, 81, 82) -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF1C2A38), Color(0xFF0F151C))
                )
                !isDay -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1F3C), Color(0xFF0B0D19))
                )
                else -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A73E8), Color(0xFF88C0FC))
                )
            }
        }
        else -> Brush.verticalGradient(
            colors = listOf(Color(0xFF1A73E8), Color(0xFF88C0FC))
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Search Bar & Location Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchCity(it) },
                    placeholder = { Text("Search city, e.g. Tokyo", color = Color.White.copy(alpha = 0.7f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchCity("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_location_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        locationPermissionRequest.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.25f), CircleShape)
                        .size(48.dp)
                        .testTag("gps_track_button")
                ) {
                    Icon(
                        imageVector = if (gpsPermissionGranted) Icons.Default.MyLocation else Icons.Default.LocationOff,
                        contentDescription = "Get GPS Location",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Main UI State Switcher
                when (val state = uiState) {
                    is WeatherUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    is WeatherUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Error Cloud",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Unable to fetch request weather forecast.",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is WeatherUiState.Success -> {
                        WeatherDashboardContent(
                            state = state,
                            savedLocs = savedLocs,
                            viewModel = viewModel
                        )
                    }
                }

                // 2. Search Autocomplete Results drop overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = searchResults.isNotEmpty() || isSearching,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .align(Alignment.TopCenter)
                        .zIndex(2f)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        if (isSearching) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else {
                            LazyColumn {
                                items(searchResults) { city ->
                                    val subtitle = listOfNotNull(city.admin1, city.country).joinToString(", ")
                                    ListItem(
                                        headlineContent = { Text(city.name, fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = "Pin") },
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.loadWeather(
                                                    lat = city.latitude,
                                                    lon = city.longitude,
                                                    name = city.name,
                                                    country = subtitle,
                                                    isGps = false
                                                )
                                            }
                                            .testTag("search_result_item_${city.id}")
                                    )
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDashboardContent(
    state: WeatherUiState.Success,
    savedLocs: List<SavedLocation>,
    viewModel: WeatherViewModel
) {
    val context = LocalContext.current
    val wmoDesc = WeatherRepository.getWmoDescription(state.current.weatherCode)
    val emoji = when (state.current.weatherCode) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55 -> "🌧️"
        61, 63, 65 -> "🌧️"
        71, 73, 75 -> "❄️"
        80, 81, 82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("weather_dashboard_scroll"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement. some_unneeded_spacing_avoided // Standard grid Spacing: 12.dp
    ) {
        // Location Title & Star Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.locationName,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (state.isGpsSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Active Pin Tracker",
                                tint = Color.Green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = state.country ?: "Globe coordinates",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Check if current location is in saved locations as favorite
                val isFav = savedLocs.any { it.id == "${state.latitude}_${state.longitude}" && it.isFavorite }
                IconButton(
                    onClick = {
                        viewModel.toggleFavorite(
                            lat = state.latitude,
                            lon = state.longitude,
                            name = state.locationName,
                            country = state.country,
                            isFav = !isFav
                        )
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        .testTag("favorite_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Pin Favorite",
                        tint = if (isFav) Color(0xFFFFD700) else Color.White
                    )
                }
            }
        }

        // Active Temperature & Main Emoji Grid
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${state.current.temperature.toInt()}°C",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 72.sp
                    )
                    Text(
                        text = "$wmoDesc · Feels like ${state.current.apparentTemperature.toInt()}°C",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Text(
                    text = emoji,
                    fontSize = 80.sp,
                    lineHeight = 80.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Dedicated severe weather keywords monitoring card
        item {
            SevereKeywordsAlertCard(state = state)
        }

        // Programmatic & AI Severe Alert Section
        val hasSevereAlerts = state.localAlerts.isNotEmpty() ||
                              (!state.aiAnalysis.severeConditionsAlert.isNullOrBlank() &&
                               state.aiAnalysis.severeConditionsSeverity != "NONE")

        if (hasSevereAlerts) {
            item {
                Card(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(vertical = 8.dp)
                         .testTag("severe_storm_alert_card"),
                     shape = RoundedCornerShape(16.dp),
                     colors = CardDefaults.cardColors(
                         containerColor = if (state.localAlerts.any { it.severity == AlertSeverity.SEVERE } ||
                                              state.aiAnalysis.severeConditionsSeverity == "SEVERE")
                                              Color(0xFFE53935) else Color(0xFFFFB300)
                     )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Storm warning",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACTIVE SEVERE STORM ALERTS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Show programmatically calculated physical storm warnings
                        state.localAlerts.forEach { alert ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "• ",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Column {
                                    Text(
                                        text = alert.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = alert.description,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }

                        // Add Gemini AI safety interpretation
                        if (!state.aiAnalysis.severeConditionsAlert.isNullOrBlank() &&
                            state.aiAnalysis.severeConditionsSeverity != "NONE") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = Color.White.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🤖 AI RISK ASSESSMENT:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = state.aiAnalysis.severeConditionsAlert,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Safe conditions",
                        tint = Color.Green,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No severe storm warnings active for your location",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Weather Stats Grid (Relative Humidity, Wind Speed, UV indicator, cloud cover)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "METEOROLOGY PARAMETERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        WeatherStatsItem(
                            icon = Icons.Outlined.WaterDrop,
                            title = "HUMIDITY",
                            value = "${state.current.humidity.toInt()}%",
                            modifier = Modifier.weight(1f)
                        )
                        WeatherStatsItem(
                            icon = Icons.Outlined.Air,
                            title = "WIND SPEED",
                            value = "${state.current.windSpeed.toInt()} km/h",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        WeatherStatsItem(
                            icon = Icons.Outlined.Compress,
                            title = "PRESSURE",
                            value = "${state.current.apparentTemperature.toInt()} feels",
                            modifier = Modifier.weight(1f)
                        )
                        WeatherStatsItem(
                            icon = Icons.Outlined.Cloud,
                            title = "RAIN",
                            value = "${state.current.precipitation} mm",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Hourly section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "HOURLY FORECAST (24 HOURS)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.hourlyList) { hourItem ->
                        HourlyCard(hourItem)
                    }
                }
            }
        }

        // Extended 7-Day Forecast Section
        item {
            DailyForecastSection(dailyList = state.dailyList)
        }

        // Gemini Advisor
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_advisor_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2640))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✨",
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GEMINI CO-METEOROLOGIST",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64B5F6)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF64B5F6).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "AI Smart advice",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64B5F6)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = state.aiAnalysis.generalDescription,
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Divider(color = Color.White.copy(alpha = 0.15f))

                    Spacer(modifier = Modifier.height(12.dp))

                    Row {
                        Icon(
                            imageVector = Icons.Outlined.Checkroom,
                            contentDescription = "Suggested outfit",
                            tint = Color(0xFFD1C4E9),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CLOTHING & LAYER RECOMMENDATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB39DDB)
                            )
                            Text(
                                text = state.aiAnalysis.clothingAdvice,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row {
                        Icon(
                            imageVector = Icons.Outlined.LocalActivity,
                            contentDescription = "Tips advice",
                            tint = Color(0xFFC8E6C9),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "OUTDOOR ACTIVITY TIP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5D6A7)
                            )
                            Text(
                                text = state.aiAnalysis.dynamicTip,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // Search history section
        val savedCities = savedLocs.filter { !it.isGps }
        if (savedCities.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PINNED & RECENT PLACES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedCities) { city ->
                            Card(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clickable {
                                        viewModel.loadWeather(
                                            lat = city.latitude,
                                            lon = city.longitude,
                                            name = city.name,
                                            country = city.country,
                                            isGps = false
                                        )
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = city.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (city.isFavorite) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Starred",
                                                tint = Color(0xFFFFD700),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = city.country ?: "Region",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Load",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64B5F6)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.deleteSavedLocation(city.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherStatsItem(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun HourlyCard(item: HourlyForecastItem) {
    val hourDesc = WeatherRepository.getWmoDescription(item.weatherCode)
    val emoji = when (item.weatherCode) {
        0 -> "☀️"
        1, 2 -> "🌤"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55 -> "🌧️"
        61, 63, 65 -> "🌧️"
        71, 73, 75 -> "❄️"
        80, 81, 82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    Card(
        modifier = Modifier
            .width(80.dp)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = WeatherRepository.formatTimeToHour(item.time),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = emoji,
                fontSize = 24.sp,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${item.temperature.toInt()}°C",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (item.precipitationProbability > 0) {
                Text(
                    text = "${item.precipitationProbability}%☔",
                    fontSize = 9.sp,
                    color = Color(0xFF64B5F6),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private val Arrangement.some_unneeded_spacing_avoided: Arrangement.Vertical
    get() = Arrangement.spacedBy(12.dp)

@Composable
fun DailyForecastSection(dailyList: List<DailyForecastItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "EXTENDED 7-DAY FORECAST",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("extended_forecast_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                dailyList.forEachIndexed { index, dailyItem ->
                    DailyForecastRow(item = dailyItem)
                    if (index < dailyList.size - 1) {
                        Divider(
                            color = Color.White.copy(alpha = 0.1f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyForecastRow(item: DailyForecastItem) {
    val dayName = WeatherRepository.formatDateToDay(item.date)
    val conditionDesc = WeatherRepository.getWmoDescription(item.weatherCode)
    val emoji = when (item.weatherCode) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55 -> "🌧️"
        61, 63, 65 -> "🌧️"
        71, 73, 75 -> "❄️"
        80, 81, 82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("daily_forecast_row_${item.date}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Date / Day name (e.g. "Thursday, Jun 18")
        Column(modifier = Modifier.weight(1.5f)) {
            Text(
                text = dayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = conditionDesc,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Precipitation Probability Indicator
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            if (item.precipitationProbability > 0) {
                Text(
                    text = "${item.precipitationProbability}%☔",
                    fontSize = 11.sp,
                    color = Color(0xFF64B5F6),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "0%☔",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }

        // Hi/Lo Temp (e.g. "24° / 14°")
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.tempMax.toInt()}°",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = " / ",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
            Text(
                text = "${item.tempMin.toInt()}°",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SevereKeywordsAlertCard(state: WeatherUiState.Success) {
    val keywords = listOf(
        "storm", "thunderstorm", "hurricane", "tornado", "gale", "blizzard", "cyclone", "typhoon",
        "flood", "flooding", "hail", "lightning", "extreme", "severe", "freeze", "freezing", "frost", "blustery", "gale-force"
    )
    
    val textToScan = buildString {
        append(WeatherRepository.getWmoDescription(state.current.weatherCode)).append(" ")
        append(state.aiAnalysis.generalDescription).append(" ")
        append(state.aiAnalysis.severeConditionsAlert ?: "").append(" ")
        state.hourlyList.forEach {
            append(WeatherRepository.getWmoDescription(it.weatherCode)).append(" ")
        }
        state.dailyList.forEach {
            append(WeatherRepository.getWmoDescription(it.weatherCode)).append(" ")
        }
        state.localAlerts.forEach { alert ->
            append(alert.title).append(" ").append(alert.description).append(" ")
        }
    }.lowercase()

    val matchedKeywords = keywords.filter { keyword ->
        textToScan.contains(keyword)
    }.distinct()

    if (matchedKeywords.isNotEmpty()) {
        val warningMessage = when {
            matchedKeywords.contains("hurricane") || matchedKeywords.contains("tornado") || matchedKeywords.contains("typhoon") || matchedKeywords.contains("cyclone") -> {
                "Extreme Threat: High-speed rotation or life-threatening localized cyclonic winds detected. Seek storm shelter or interior room immediately."
            }
            matchedKeywords.contains("storm") || matchedKeywords.contains("thunderstorm") || matchedKeywords.contains("lightning") -> {
                "Severe Thunderstorm Warning: Severe electrical activity, high winds, or heavy convection detected. Stay indoors and unplug delicate electronic equipment."
            }
            matchedKeywords.contains("flood") || matchedKeywords.contains("flooding") -> {
                "Flash Flood Hazard: High-volume precipitation or drainage saturated. Never drive through flooded streets or underpasses."
            }
            matchedKeywords.contains("blizzard") || matchedKeywords.contains("freeze") || matchedKeywords.contains("freezing") || matchedKeywords.contains("frost") -> {
                "Freezing Warning: Extremely low or sub-zero temperatures detected. Protect exposed plumbing, outdoor plants, and avoid slippery roads."
            }
            matchedKeywords.contains("gale") || matchedKeywords.contains("gale-force") || matchedKeywords.contains("blustery") -> {
                "High Wind Advisory: Damaging winds can knock down tree limbs or power lines. Secure loose patio items and drive with caution."
            }
            else -> {
                "Severe Weather Warning: Dangerous atmospheric fluctuations detected in local forecasts. Stay alert and monitor updates."
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .testTag("severe_weather_keywords_alert_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFD32F2F) // Bold Crimson for warning
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Severe weather warning indicator",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "HIGH-PRIORITY WEATHER WARNING",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = warningMessage,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Displays matched severe weather keywords
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Detected Severe Keywords:",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            matchedKeywords.forEach { kw ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = kw.uppercase(),
                                        color = Color(0xFFD32F2F),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


