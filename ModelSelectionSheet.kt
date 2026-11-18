package com.sync.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.sync.app.domain.provider.AiModel
import com.sync.app.domain.provider.CustomProviderConfig
import com.sync.app.domain.provider.ProviderConfig
import com.sync.app.domain.provider.ProviderId
import com.sync.app.domain.provider.ProviderTier
import com.sync.app.ui.screens.settings.components.ApiKeyDialog
import com.sync.app.ui.theme.DeepNavy

// Warna Pastel Badge
private val BadgeTextBg = Color(0xFFE3F2FD)
private val BadgeTextFg = Color(0xFF1565C0)
private val BadgeImageBg = Color(0xFFF3E5F5)
private val BadgeImageFg = Color(0xFF7B1FA2)
private val BadgeCodeBg = Color(0xFFE0F7FA)
private val BadgeCodeFg = Color(0xFF00838F)
private val BadgeFreeBg = Color(0xFFE8F5E9)
private val BadgeFreeFg = Color(0xFF2E7D32)
private val BadgeProBg = Color(0xFFFFF3E0)
private val BadgeProFg = Color(0xFFE65100)

private enum class SheetListMode { PROVIDER, MODEL }
private enum class FilterType { TEKS, KODING, GAMBAR, FREE, PRO }

/**
 * [P-019 + Tier + P-035] Sheet pemilihan model & provider.
 *
 * [P-035] Fix: Sebelumnya `providerConfigs` dan `customProviders` diiterasi
 * terpisah, menyebabkan duplikasi ID `custom:xxx` (karena `allConfigs()`
 * sudah mencakup custom). Sekarang hanya iterasi `providerConfigs`, dan
 * custom provider dideteksi via `CustomProviderConfig.isCustomId()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSheet(
    selectedProvider: ProviderId,
    providerConfigs: List<ProviderConfig>,
    customProviders: List<CustomProviderConfig>,
    models: List<AiModel>,
    selectedModelId: String?,
    isLoading: Boolean,
    error: String?,
    hasApiKey: Boolean,
    onProviderSelected: (ProviderId) -> Unit,
    onModelSelected: (AiModel) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onAddCustomProvider: () -> Unit,
    onEditCustomProvider: (CustomProviderConfig) -> Unit,
    onDeleteCustomProvider: (CustomProviderConfig) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var listMode by remember { mutableStateOf(SheetListMode.MODEL) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    var activeFilters by remember { mutableStateOf(setOf<FilterType>()) }

    val toggleFilter = { type: FilterType ->
        activeFilters = if (activeFilters.contains(type)) {
            activeFilters - type
        } else {
            activeFilters + type
        }
    }

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

    val selectedProviderDisplayName = providerConfigs
        .find { it.id == selectedProvider }
        ?.displayName
        ?: selectedProvider.value

    val selectedModelName = models.find { it.id == selectedModelId }?.displayName
        ?: if (isLoading) "Memuat..." else "Pilih Model"

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .nestedScroll(disableSheetDrag)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pilih Model",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            // 1. Kapsul Dropdown Provider & Model
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            listMode = SheetListMode.PROVIDER
                            activeFilters = emptySet()
                        },
                    shape = RoundedCornerShape(50),
                    color = DeepNavy.copy(alpha = 0.05f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedProviderDisplayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Lucide.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { listMode = SheetListMode.MODEL },
                    shape = RoundedCornerShape(50),
                    color = DeepNavy.copy(alpha = 0.05f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedModelName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                imageVector = Lucide.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Baris Filter (Multi-select)
            if (listMode == SheetListMode.MODEL) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            text = "Teks",
                            isSelected = activeFilters.contains(FilterType.TEKS),
                            bgColor = BadgeTextBg, contentColor = BadgeTextFg,
                            onClick = { toggleFilter(FilterType.TEKS) }
                        )
                    }
                    item {
                        FilterChip(
                            text = "Koding",
                            isSelected = activeFilters.contains(FilterType.KODING),
                            bgColor = BadgeCodeBg, contentColor = BadgeCodeFg,
                            onClick = { toggleFilter(FilterType.KODING) }
                        )
                    }
                    item {
                        FilterChip(
                            text = "Gambar",
                            isSelected = activeFilters.contains(FilterType.GAMBAR),
                            bgColor = BadgeImageBg, contentColor = BadgeImageFg,
                            onClick = { toggleFilter(FilterType.GAMBAR) }
                        )
                    }
                    item {
                        FilterChip(
                            text = "Gratis",
                            isSelected = activeFilters.contains(FilterType.FREE),
                            bgColor = BadgeFreeBg, contentColor = BadgeFreeFg,
                            onClick = { toggleFilter(FilterType.FREE) }
                        )
                    }
                    item {
                        FilterChip(
                            text = "Pro",
                            isSelected = activeFilters.contains(FilterType.PRO),
                            bgColor = BadgeProBg, contentColor = BadgeProFg,
                            onClick = { toggleFilter(FilterType.PRO) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Area List
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                color = DeepNavy.copy(alpha = 0.05f)
            ) {
                if (listMode == SheetListMode.PROVIDER) {
                    ProviderList(
                        selectedProvider = selectedProvider,
                        providerConfigs = providerConfigs,
                        customProviders = customProviders,
                        onProviderSelected = { provider ->
                            onProviderSelected(provider)
                            listMode = SheetListMode.MODEL
                        },
                        onAddCustomProvider = onAddCustomProvider,
                        onEditCustomProvider = onEditCustomProvider,
                        onDeleteCustomProvider = onDeleteCustomProvider
                    )
                } else {
                    ModelList(
                        models = models,
                        selectedModelId = selectedModelId,
                        isLoading = isLoading,
                        error = error,
                        activeFilters = activeFilters,
                        onModelSelected = { model ->
                            onModelSelected(model)
                            onDismissRequest()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. API Key Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = CircleShape,
                color = DeepNavy.copy(alpha = 0.05f),
                onClick = { showApiKeyDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "API Key · $selectedProviderDisplayName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasApiKey) "Terkonfigurasi" else "Belum diatur",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (hasApiKey) "Ubah" else "Atur",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            providerName = selectedProviderDisplayName,
            initialKey = "",
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                onSaveApiKey(key)
                showApiKeyDialog = false
            }
        )
    }
}

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    bgColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val finalBgColor = if (isSelected) contentColor else bgColor
    val finalContentColor = if (isSelected) Color.White else contentColor

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(finalBgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = finalContentColor
        )
    }
}

/**
 * [P-035] ProviderList di-fix:
 * - Hanya iterasi `providerConfigs` (yang sudah mencakup built-in + remote + custom).
 * - Custom provider dideteksi via [CustomProviderConfig.isCustomId].
 * - `customProviders` hanya dipakai sebagai lookup untuk metadata (models.size).
 * - Ini mencegah duplikasi key di LazyColumn.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderList(
    selectedProvider: ProviderId,
    providerConfigs: List<ProviderConfig>,
    customProviders: List<CustomProviderConfig>,
    onProviderSelected: (ProviderId) -> Unit,
    onAddCustomProvider: () -> Unit,
    onEditCustomProvider: (CustomProviderConfig) -> Unit,
    onDeleteCustomProvider: (CustomProviderConfig) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(providerConfigs, key = { it.id.value }) { config ->
            val isSelected = config.id == selectedProvider
            val isCustom = CustomProviderConfig.isCustomId(config.id)

            if (isCustom) {
                val customData = customProviders.find { it.id == config.id.value }
                if (customData != null) {
                    CustomProviderRow(
                        config = config,
                        customData = customData,
                        isSelected = isSelected,
                        onProviderSelected = onProviderSelected,
                        onEditCustomProvider = onEditCustomProvider,
                        onDeleteCustomProvider = onDeleteCustomProvider
                    )
                }
            } else {
                BuiltInProviderRow(
                    config = config,
                    isSelected = isSelected,
                    onProviderSelected = onProviderSelected
                )
            }
        }

        // Tombol "Tambah Provider Custom"
        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddCustomProvider() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Lucide.Plus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Tambah Provider Custom",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BuiltInProviderRow(
    config: ProviderConfig,
    isSelected: Boolean,
    onProviderSelected: (ProviderId) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onProviderSelected(config.id) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomProviderRow(
    config: ProviderConfig,
    customData: CustomProviderConfig,
    isSelected: Boolean,
    onProviderSelected: (ProviderId) -> Unit,
    onEditCustomProvider: (CustomProviderConfig) -> Unit,
    onDeleteCustomProvider: (CustomProviderConfig) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onProviderSelected(config.id) },
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TierPulseDot(
                    color = tierColor(ProviderTier.Free),
                    isActive = isSelected
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${customData.models.size} model · Long-press untuk kelola",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Lucide.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        ) {
            DropdownMenuItem(
                text = { Text("Edit", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.Pencil, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    showMenu = false
                    onEditCustomProvider(customData)
                }
            )
            DropdownMenuItem(
                text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showMenu = false
                    onDeleteCustomProvider(customData)
                }
            )
        }
    }
}

@Composable
private fun ModelList(
    models: List<AiModel>,
    selectedModelId: String?,
    isLoading: Boolean,
    error: String?,
    activeFilters: Set<FilterType>,
    onModelSelected: (AiModel) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    val filteredModels = models.filter { model ->
        if (activeFilters.isEmpty()) return@filter true

        val isFree = isFreeModel(model)
        val hasImage = hasVisionCapability(model)
        val hasCode = !model.id.contains("guard", true)
        val hasText = true

        val capabilityFilters = setOf(FilterType.TEKS, FilterType.KODING, FilterType.GAMBAR)
        val priceFilters = setOf(FilterType.FREE, FilterType.PRO)

        val selectedCaps = activeFilters.intersect(capabilityFilters)
        val selectedPrices = activeFilters.intersect(priceFilters)

        val matchesCapability = if (selectedCaps.isEmpty()) true else {
            (selectedCaps.contains(FilterType.TEKS) && hasText) ||
            (selectedCaps.contains(FilterType.KODING) && hasCode) ||
            (selectedCaps.contains(FilterType.GAMBAR) && hasImage)
        }

        val matchesPrice = if (selectedPrices.isEmpty()) true else {
            (selectedPrices.contains(FilterType.FREE) && isFree) ||
            (selectedPrices.contains(FilterType.PRO) && !isFree)
        }

        matchesCapability && matchesPrice
    }

    if (filteredModels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Tidak ada model yang cocok dengan filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filteredModels, key = { it.id }) { model ->
            val isSelected = model.id == selectedModelId

            val isFree = isFreeModel(model)
            val hasImage = hasVisionCapability(model)
            val hasCode = !model.id.contains("guard", true)
            val hasText = true

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModelSelected(model) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = "Terpilih",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasText) {
                        InfoBadge(text = "Teks", icon = Lucide.MessageSquare, bgColor = BadgeTextBg, contentColor = BadgeTextFg)
                    }
                    if (hasImage) {
                        InfoBadge(text = "Gambar", icon = Lucide.Image, bgColor = BadgeImageBg, contentColor = BadgeImageFg)
                    }
                    if (hasCode) {
                        InfoBadge(text = "Koding", icon = Lucide.Code, bgColor = BadgeCodeBg, contentColor = BadgeCodeFg)
                    }
                    if (isFree) {
                        InfoBadge(text = "FREE", bgColor = BadgeFreeBg, contentColor = BadgeFreeFg)
                    } else {
                        InfoBadge(text = "PRO", bgColor = BadgeProBg, contentColor = BadgeProFg)
                    }
                }
            }
        }
    }
}

private fun isFreeModel(model: AiModel): Boolean = when (model.providerId.value) {
    "groq", "mistral", "gemini" -> true
    "openrouter" -> model.id.endsWith(":free", ignoreCase = true) ||
        model.id.contains(":free", ignoreCase = true)
    else -> CustomProviderConfig.isCustomId(model.providerId)
}

private fun hasVisionCapability(model: AiModel): Boolean =
    model.providerId.value == "gemini" ||
        model.id.contains("vision", true) ||
        model.id.contains("vl", true) ||
        model.id.contains("claude-3", true)

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

@Composable
private fun InfoBadge(
    text: String,
    bgColor: Color,
    contentColor: Color,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor
        )
    }
}