package com.netlocker.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.netlocker.ui.theme.netLocker

/** Rounded navy card with a subtle blue border — the base surface used across the app. */
@Composable
fun NetLockerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = MaterialTheme.netLocker.cardBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.netLocker.card,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            content = content,
        )
    }
}

/** Small rounded label: [icon] (optional) + [text], tinted by [color] on [container]. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    container: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** 44dp round outlined icon button used in headers (search, settings, more, edit...). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(maxOf(size, 48.dp)) // touch target is never below 48dp
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.netLocker.card)
                .border(1.dp, MaterialTheme.netLocker.cardBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Rounded selectable filter chip: label + optional count. */
@Composable
fun NetLockerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.netLocker
    val container = if (selected) MaterialTheme.colorScheme.primary else colors.card
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val border = if (selected) MaterialTheme.colorScheme.primary else colors.cardBorder
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
        }
        Text(label, color = content, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count != null) {
            Text(
                text = count.toString(),
                color = content,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** Rounded search field with leading search icon and a clear button. */
@Composable
fun NetLockerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onQueryChange("") }
                        .semantics { contentDescription = "Clear search" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.netLocker.card,
            unfocusedContainerColor = MaterialTheme.netLocker.card,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.netLocker.cardBorder,
        ),
    )
}

/** An app icon rendered from its [Drawable], with a neutral placeholder if it has none. */
@Composable
fun AppIconImage(icon: Drawable?, size: Dp, modifier: Modifier = Modifier, cornerRadius: Dp = 10.dp) {
    val shape = RoundedCornerShape(cornerRadius)
    if (icon != null) {
        val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
        val bitmap = remember(icon, px) { icon.toBitmap(px, px).asImageBitmap() }
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        Box(modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** Centered illustration-style empty state with an optional action button. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.netLocker.cardBorderStrong.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(actionLabel, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * Shown while the installed-app scan is still running (it takes a couple of seconds on
 * a phone with hundreds of apps): a spinner + message, over pulsing placeholder rows
 * shaped like the real list so the layout doesn't jump when the apps arrive.
 */
@Composable
fun AppListLoading(modifier: Modifier = Modifier, message: String = "Loading installed apps…", rows: Int = 6) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = message },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(10.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        repeat(rows) {
            NetLockerCard(modifier = Modifier.fillMaxWidth().alpha(pulse)) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(150.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        Box(Modifier.width(100.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
            }
        }
    }
}

/** Two-tone "NetLocker" wordmark: "Net" in the text color, "Locker" in brand blue. */
@Composable
fun NetLockerWordmark(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text = androidx.compose.ui.text.buildAnnotatedString {
            append("Net")
            withStyle(androidx.compose.ui.text.SpanStyle(color = primary)) { append("Locker") }
        },
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}
