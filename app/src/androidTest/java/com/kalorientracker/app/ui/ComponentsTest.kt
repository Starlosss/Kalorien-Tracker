package com.kalorientracker.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.ui.common.ConfidenceBadge
import com.kalorientracker.app.ui.common.MacroBar
import com.kalorientracker.app.ui.common.PortionSlider
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.theme.KalorienTheme
import com.kalorientracker.app.ui.theme.Palette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun macroBarShowsCurrentAndTarget() {
        compose.setContent {
            KalorienTheme { MacroBar("Protein", 132.0, 160.0, Palette.Protein) }
        }
        compose.onNodeWithText("Protein").assertIsDisplayed()
        compose.onNodeWithText("132").assertIsDisplayed()
        compose.onNodeWithText(" / 160 g").assertIsDisplayed()
    }

    @Test
    fun confidenceIsShownAsWordsNotPercentages() {
        compose.setContent {
            KalorienTheme { ConfidenceBadge(Confidence.LOW) }
        }
        compose.onNodeWithText("Unsicher").assertIsDisplayed()
    }

    @Test
    fun portionSliderShowsQualitativeLabel() {
        compose.setContent {
            KalorienTheme { PortionSlider(index = 3, onIndexChange = {}) }
        }
        compose.onNodeWithText("Normal").assertIsDisplayed()
        compose.onNodeWithText("Sehr klein").assertIsDisplayed()
        compose.onNodeWithText("Sehr groß").assertIsDisplayed()
    }

    @Test
    fun chipReportsClicks() {
        var clicked = false
        compose.setContent {
            KalorienTheme { SelectChip("Frühstück", selected = false, onClick = { clicked = true }) }
        }
        compose.onNodeWithText("Frühstück").performClick()
        assertTrue(clicked)
    }
}
