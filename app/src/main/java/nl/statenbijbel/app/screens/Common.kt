package nl.statenbijbel.app.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.statenbijbel.app.AppText
import nl.statenbijbel.app.LocalReadingColors
import nl.statenbijbel.app.SNIPPET_SCALE
import nl.statenbijbel.app.readingStyle

@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    val palette = LocalReadingColors.current
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onBack) {
                Icon(Icons.Default.ArrowBack, "Terug", tint = palette.muted)
            }
            Text(title, style = AppText.title, color = palette.ink, modifier = Modifier.weight(1f))
            extra()
        }
        HorizontalDivider(color = palette.divider)
    }
}

@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReadingColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, style = AppText.body) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = palette.muted) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton({ onQueryChange("") }) {
                    Icon(Icons.Default.Close, "Wissen", tint = palette.muted)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// A place in the Bible with a few lines of its text: search results, bookmarks, citations.
@Composable
internal fun ReferenceRow(
    reference: String,
    text: AnnotatedString,
    onClick: () -> Unit,
    detail: String = "",
    maxLines: Int = 3,
) {
    val palette = LocalReadingColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(reference, style = AppText.reference, color = palette.accent)
            if (detail.isNotEmpty()) {
                Text("  $detail", style = AppText.caption, color = palette.muted, maxLines = 1)
            }
        }
        Text(
            text,
            style = readingStyle(SNIPPET_SCALE),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    HorizontalDivider(color = palette.divider)
}

fun noteLabel(number: Int, catchword: String): String =
    if (catchword.isBlank()) "kantt. $number" else "kantt. $number · $catchword"

@Composable
internal fun EmptyMessage(text: String) {
    Text(
        text,
        style = AppText.body,
        color = LocalReadingColors.current.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(30.dp),
    )
}

@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val palette = LocalReadingColors.current
    val shape = RoundedCornerShape(50)
    Surface(
        color = if (active) palette.accent.copy(alpha = 0.16f) else Color.Transparent,
        shape = shape,
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .border(1.dp, if (active) palette.accent.copy(alpha = 0.5f) else palette.divider, shape),
    ) {
        Text(
            label,
            style = AppText.small,
            color = if (active) palette.accent else palette.muted,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
