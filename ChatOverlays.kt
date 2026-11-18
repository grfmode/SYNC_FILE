package com.sync.app.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ChevronsUp
import com.composables.icons.lucide.Lucide
import com.sync.app.ui.theme.DeepNavy

@Composable
fun ScrollToBottomButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Lucide.ArrowDown,
                    contentDescription = "Scroll to Bottom",
                    tint = DeepNavy,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun HistorySwipeUpHint(
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY < -50f) onSwipeUp()
                        dragOffsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount -> dragOffsetY += dragAmount }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "chevron_bounce")
        val bounceOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "chevron_offset"
        )
        val alphaAnim by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "chevron_alpha"
        )

        Icon(
            imageVector = Lucide.ChevronsUp,
            contentDescription = "Swipe up for history",
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { translationY = bounceOffset }
                .alpha(alphaAnim),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}