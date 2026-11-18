package com.sync.app.ui.screens.settings.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.sync.app.domain.provider.ProviderConfig
import com.sync.app.domain.provider.ProviderId
import com.sync.app.domain.provider.ProviderTier

/**
 * [P-019 + Tier] Sheet pemilihan provider.
 *
 * Menerima [providerConfigs] dari `ProviderRegistry` (bukan `ProviderId.entries`).
 * Setiap baris menampilkan titik warna sesuai tier:
 * - 🟢 Free, 🟡 Credit, 🟠 Trial, 🔴 Paid
 *
 * Hanya baris yang sedang terpilih ([selectedProvider]) yang titiknya
 * beranimasi pulse. Baris lain statis.
 *
 * Tampilan visual lain tidak berubah (UI Freeze D-014 terjaga).
 *
 * Perbaikan Gestur:
 * Menambahkan `nestedScroll` agar swipe down hanya memicu dismiss jika
 * dilakukan pada area drag handle (atas), bukan saat user scroll daftar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionSheet(
    selectedProvider: ProviderId,
    providerConfigs: List<ProviderConfig>,
    onProviderSelected: (ProviderId) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    // Connection untuk mencegah swipe down menutup sheet saat scroll
    val disableSheetDrag = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return available
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
                .nestedScroll(disableSheetDrag)
        ) {
            Text(
                text = "Pilih Penyedia API",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            providerConfigs.forEach { config ->
                val isSelected = config.id == selectedProvider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onProviderSelected(config.id)
                            onDismissRequest()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TierPulseDot(
                            color = tierColor(config.tier),
                            isActive = isSelected
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = config.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TierPulseDot(
    color: Color,
    isActive: Boolean
) {
    if (!isActive) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "tier_pulse")
    val opacity by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tier_pulse_opacity"
    )
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tier_pulse_scale"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer {
                alpha = opacity
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(color)
    )
}

private fun tierColor(tier: ProviderTier): Color = when (tier) {
    ProviderTier.Free -> Color(0xFF2E7D32)
    ProviderTier.Credit -> Color(0xFFF9A825)
    ProviderTier.Trial -> Color(0xFFE65100)
    ProviderTier.Paid -> Color(0xFFC62828)
}