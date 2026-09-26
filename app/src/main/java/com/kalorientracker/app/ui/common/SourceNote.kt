package com.kalorientracker.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kalorientracker.app.ui.theme.Palette

/**
 * Where the numbers come from. Every value the app computes is traceable to a published
 * reference, so the plan can be checked instead of believed.
 */
@Composable
fun SourceNote(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel("Grundlage")
        Spacer(Modifier.height(8.dp))
        SOURCES.forEach { (topic, detail) ->
            Text(topic, style = MaterialTheme.typography.titleSmall, color = Palette.TextSecondary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "Nachlesen: dge.de/wissenschaft/referenzwerte",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
        )
    }
}

private val SOURCES = listOf(
    "Energiebedarf" to "Ruheenergieverbrauch × PAL. So leitet die Deutsche Gesellschaft für Ernährung (DGE) ihre Referenzwerte ab. Die Formel nutzt Gewicht und Alter. Welcher PAL-Wert gilt, schätzt die App aus deinen Schritten. Die Stufen selbst (1,4 bis 1,9) stammen von der DGE.",
    "Fett und Kohlenhydrate" to "DGE-Richtwerte: 30 % der Energie aus Fett, bei viel Bewegung 35 %. Kohlenhydrate sind der Rest, über 50 %.",
    "Protein" to "Der DGE-Referenzwert ist 0,8 g je kg Körpergewicht. Für deine Ziele rechnet die App höher, mit 1,6 bis 2,0 g je kg nach dem ISSN Position Stand „Protein and Exercise“ (2017). Weniger als den DGE-Wert setzt sie nie an.",
    "Ballaststoffe, Zucker, Salz" to "DGE: mindestens 30 g Ballaststoffe und 14,6 g je 1.000 kcal, höchstens 10 % der Energie als freier Zucker und ebenso viel als gesättigte Fettsäuren, höchstens 6 g Salz.",
    "Sport" to "MET-Werte aus dem Compendium of Physical Activities. Gerechnet wird nur die Energie über dem Ruheumsatz, damit nichts doppelt zählt.",
    "Abnehmen" to "500 kcal Defizit pro Tag, das entspricht etwa 0,5 kg pro Woche. So steht es in der S3-Leitlinie „Prävention und Therapie der Adipositas“.",
    "BMI" to "Einordnung nach der WHO. Der BMI geht nicht in den Kalorienbedarf ein. Er ordnet nur dein Gewicht ein.",
)
