package com.sync.app.ui.screens.chat

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@Serializable
data class WeatherResponse(
    val current_weather: CurrentWeather
)

@Serializable
data class CurrentWeather(
    val temperature: Double,
    val weathercode: Int
)

data class WeatherState(
    val temperature: Double? = null,
    val weatherCode: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _weatherState = MutableStateFlow(WeatherState())
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @SuppressLint("MissingPermission")
    fun fetchWeather() {
        if (_weatherState.value.isLoading || _weatherState.value.temperature != null) return

        _weatherState.value = _weatherState.value.copy(isLoading = true, error = null)

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                fetchFromApi(location.latitude, location.longitude)
            } else {
                _weatherState.value = _weatherState.value.copy(isLoading = false, error = "Lokasi tidak ditemukan")
            }
        }.addOnFailureListener { e ->
            _weatherState.value = _weatherState.value.copy(isLoading = false, error = e.message)
        }
    }

    private fun fetchFromApi(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                val request = Request.Builder().url(url).build()
                
                // Menggunakan Dispatchers.IO untuk network call
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val weather = json.decodeFromString<WeatherResponse>(body)
                            _weatherState.value = WeatherState(
                                temperature = weather.current_weather.temperature,
                                weatherCode = weather.current_weather.weathercode,
                                isLoading = false
                            )
                        }
                    } else {
                        _weatherState.value = _weatherState.value.copy(isLoading = false, error = "Gagal memuat cuaca")
                    }
                }
            } catch (e: Exception) {
                _weatherState.value = _weatherState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}