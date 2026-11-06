package pt.avus.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalePT: Locale = Locale.forLanguageTag("pt-PT")

fun formatDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", LocalePT).format(Date(millis))

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(LocalePT, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}

/** Horizontal padding shared by all screens. */
val ScreenPadding: Dp = 16.dp

/**
 * Screens sit on a tonal surface and their content (segmented lists, cards, search) on the
 * brighter `surface`, as in Android 16/17 system apps such as Settings.
 */
val screenBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer

/** Colour for content on the page: brighter than [screenBackground] in both light and dark schemes. */
val contentSurface: Color
    @Composable get() = MaterialTheme.colorScheme.let {
        if (it.surface.luminance() > 0.5f) it.surfaceBright else it.surfaceContainerHigh
    }

/** Segmented list item colours on [contentSurface]. */
@Composable
fun segmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(containerColor = contentSurface)

@Composable
fun screenTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = screenBackground, scrolledContainerColor = screenBackground)

/** Corner shapes for item [index] of [count] in a segmented (grouped) list. */
@Composable
fun segmentedShapes(index: Int, count: Int): ListItemShapes = ListItemDefaults.segmentedShapes(index, count)

/** A [Shape] that draws a [Morph] between two Material shapes at [progress] (0 = start, 1 = end). */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * Leading avatar for selectable rows: shows [initials] in a circle and morphs into a
 * "cookie" with a check mark when [selected] (Material 3 Expressive shape morphing).
 */
@Composable
fun SelectableAvatar(
    selected: Boolean,
    initials: String,
    onToggle: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "avatarMorph",
    )
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "avatarColor",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier
            .size(44.dp)
            .clip(MorphShape(morph, progress))
            .background(container)
            .clickable(role = Role.Checkbox, onClickLabel = contentDescription, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            selected,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
            label = "avatarContent",
        ) { isSelected ->
            if (isSelected) Icon(Icons.Filled.Check, contentDescription, tint = content)
            else Text(initials, style = MaterialTheme.typography.titleMediumEmphasized, color = content)
        }
    }
}

/** Up to two initials for an avatar: "Lar Nossa Senhora" -> "LN". */
fun initialsOf(name: String): String {
    val skip = setOf("de", "da", "do", "das", "dos", "e", "a", "o")
    return name.split(Regex("[\\s\\-–—\"'(),.]+"))
        .filter { it.isNotBlank() && it.lowercase() !in skip && it.first().isLetter() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
}

/** An icon centred in an expressive shape, used as leading content and in empty states. */
@Composable
fun ShapeIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Circle,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier.size(size).clip(shape.toShape()).background(containerColor),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(iconSize), tint = contentColor) }
}

/** Filter chip with the expressive check mark when selected. */
@Composable
fun CheckFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
    )
}

/** Compact chip that opens a list of options (e.g. district filter). */
@Composable
fun OptionPicker(
    label: String,
    value: String?,
    options: List<String>,
    allLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = value != null,
            onClick = { expanded = true },
            label = {
                Text(value ?: label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
            },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
            enabled = options.isNotEmpty(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                    trailingIcon = if (option == value) ({ Icon(Icons.Filled.Check, null) }) else null,
                )
            }
        }
    }
}

/** A titled group of content on a tonal surface with large rounded corners. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = contentSurface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** Section heading above a segmented list. */
@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Filled.Warning else Icons.Filled.Info, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeIcon(
            icon,
            shape = MaterialShapes.Cookie9Sided,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 120.dp,
            iconSize = 48.dp,
        )
        Spacer(Modifier.padding(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.padding(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(16.dp))
        actions()
    }
}

/** Small round icon used as a status dot inside list rows. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}
