package nl.statenbijbel.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.statenbijbel.app.Accent
import nl.statenbijbel.app.AppState
import nl.statenbijbel.app.AppText
import nl.statenbijbel.app.LocalReadingColors
import nl.statenbijbel.app.Prefs
import nl.statenbijbel.app.Screen
import nl.statenbijbel.app.Theme

@Composable
fun SettingsScreen(state: AppState) {
    val palette = LocalReadingColors.current
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        ScreenHeader("Instellingen", { state.screen = Screen.READER })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Weergave")
            SettingRow("Thema") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Theme.entries.forEach { theme ->
                        Chip(theme.label, Prefs.theme == theme) { Prefs.saveTheme(theme) }
                    }
                }
            }
            AccentPicker()
            SliderRow("Tekstgrootte", Prefs.textSize, 13, 34) { Prefs.saveTextSize(it) }
            SliderRow("Regelafstand", Prefs.lineHeight, 110, 220) { Prefs.saveLineHeight(it) }
            SwitchRow("Schreefletter (serif)", Prefs.serif) { Prefs.saveSerif(it) }

            SectionHeader("Kanttekeningen")
            SwitchRow("Toon nummers in de tekst", Prefs.showNoteMarkers) { Prefs.saveNoteMarkers(it) }
            SwitchRow("Compacte versregels", Prefs.compactVerses) { Prefs.saveCompactVerses(it) }

            SectionHeader("Overig")
            SwitchRow("Vegen om van hoofdstuk te wisselen", Prefs.swipeNavigation) {
                Prefs.saveSwipeNavigation(it)
            }
            SwitchRow("Scherm aan laten tijdens lezen", Prefs.keepScreenOn) { Prefs.saveKeepScreenOn(it) }

            SectionHeader("Over")
            Text(
                "Statenvertaling met de kanttekeningen van de Statenvertalers " +
                    "(editie 1888). 31.171 verzen, 59.385 kanttekeningen en 48.466 " +
                    "onderlinge verwijzingen — volledig offline op dit toestel.\n\n" +
                    "Teksteditie: github.com/Isidore-Guild/statenvertaling (CC0).",
                style = AppText.small,
                lineHeight = 19.sp,
                color = palette.muted,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AccentPicker() {
    val palette = LocalReadingColors.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Accentkleur", style = AppText.body, color = palette.ink, modifier = Modifier.weight(1f))
            Text(Prefs.accent.label, style = AppText.small, color = palette.muted)
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Accent.entries.forEach { accent ->
                val chosen = Prefs.accent == accent
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.color(palette.dark))
                        .border(
                            if (chosen) 3.dp else 1.dp,
                            if (chosen) palette.ink else palette.divider,
                            CircleShape,
                        )
                        .clickable { Prefs.saveAccent(accent) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) {
                        Icon(
                            Icons.Default.Check, accent.label,
                            tint = palette.paper,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val palette = LocalReadingColors.current
    Text(
        text,
        style = AppText.sectionLabel,
        color = palette.accent,
        modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    val palette = LocalReadingColors.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(label, style = AppText.body, color = palette.ink, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    val palette = LocalReadingColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!value) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppText.body, color = palette.ink, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onToggle)
    }
}

@Composable
private fun SliderRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val palette = LocalReadingColors.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row {
            Text(label, style = AppText.body, color = palette.ink, modifier = Modifier.weight(1f))
            Text("$value", style = AppText.small, color = palette.muted)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}
