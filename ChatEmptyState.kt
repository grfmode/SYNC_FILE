package com.sync.app.ui.screens.chat.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudFog
import com.composables.icons.lucide.CloudLightning
import com.composables.icons.lucide.CloudRain
import com.composables.icons.lucide.CloudSun
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Snowflake
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Sun
import com.sync.app.ui.screens.chat.WeatherViewModel
import com.sync.app.ui.theme.DeepNavy
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun ChatEmptyState(
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    var isVisible by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            viewModel.fetchWeather()
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
        if (hasLocationPermission) {
            viewModel.fetchWeather()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetingPair = remember(hour) {
        val variants = when (hour) {
            in 4..10 -> listOf(
                Pair("Selamat Pagi. Sistem siap.", "Mari selaraskan ide. Mau mulai dari mana?"),
                Pair("Pagi yang cerah. Markas terbuka.", "Satu ruang, banyak otak. Siap beraksi?"),
                Pair("Selamat Pagi. Konteks dimuat.", "Pikiranmu tersinkronisasi. Mari kita mulai.")
            )
            in 11..14 -> listOf(
                Pair("Selamat Siang. Sistem optimal.", "Mari selesaikan misi. Apa rencanamu?"),
                Pair("Siang. Markas pusat aktif.", "Waktunya produktif. Mau kerjakan apa?"),
                Pair("Selamat Siang. Semua siap.", "Satu ruang, banyak otak. Mari beraksi.")
            )
            in 15..18 -> listOf(
                Pair("Selamat Sore. Konteks dimuat.", "Waktunya menyelesaikan misi. Mulai dari mana?"),
                Pair("Sore. Sistem bersiaga.", "Mari tuntaskan ide-ide hari ini."),
                Pair("Selamat Sore. Koneksi stabil.", "Pikiranmu tersinkronisasi. Lanjutkan progresmu.")
            )
            else -> listOf(
                Pair("Selamat Malam. Mode senyap.", "Pikiranmu tersinkronisasi. Mari kita ciptakan sesuatu."),
                Pair("Malam. Markas tenang.", "Satu ruang, banyak otak. Waktunya eksplorasi."),
                Pair("Selamat Malam. Sistem siap.", "Mari selaraskan ide sebelum beristirahat.")
            )
        }
        variants.random()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(DeepNavy.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Sparkles,
                        contentDescription = null,
                        tint = DeepNavy,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = greetingPair.first,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = greetingPair.second,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // T-032: Weather Widget (Fix Smart Cast)
                val currentTemp = weatherState.temperature
                val currentCode = weatherState.weatherCode

                if (currentTemp != null && currentCode != null) {
                    val (weatherIcon, weatherDesc) = getWeatherInfo(currentCode)
                    Surface(
                        shape = CircleShape,
                        color = DeepNavy.copy(alpha = 0.05f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = weatherIcon,
                                contentDescription = weatherDesc,
                                tint = DeepNavy,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${currentTemp.roundToInt()}°C · $weatherDesc",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = DeepNavy
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper: Mapping kode cuaca WMO (Open-Meteo) ke Ikon Lucide & Teks Indonesia
private fun getWeatherInfo(code: Int): Pair<ImageVector, String> {
    return when (code) {
        0 -> Pair(Lucide.Sun, "Cerah")
        1, 2 -> Pair(Lucide.CloudSun, "Cerah Berawan")
        3 -> Pair(Lucide.Cloud, "Mendung")
        45, 48 -> Pair(Lucide.CloudFog, "Berkabut")
        51, 53, 55, 56, 57 -> Pair(Lucide.CloudRain, "Gerimis")
        61, 63, 65, 66, 67, 80, 81, 82 -> Pair(Lucide.CloudRain, "Hujan")
        71, 73, 75, 77, 85, 86 -> Pair(Lucide.Snowflake, "Salju")
        95, 96, 99 -> Pair(Lucide.CloudLightning, "Badai Petir")
        else -> Pair(Lucide.Cloud, "Berawan")
    }
}