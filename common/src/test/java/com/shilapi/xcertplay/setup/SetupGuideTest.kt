package com.shilapi.xcertplay.setup

import com.shilapi.xcertplay.setup.SetupGuide.Feature
import com.shilapi.xcertplay.setup.SetupGuide.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuideTest {
    @Test fun theGuideOffersOnlyCarPlayLevelChoices() {
        val features = SetupGuide.features().associateBy { it.feature }
        assertEquals(Status.TESTED, features.getValue(Feature.AUTO_CONNECT).status)
        assertEquals(Status.TESTED, features.getValue(Feature.LOCATION).status)
        assertEquals(2, SetupGuide.features().size)
    }

    @Test fun noGuideChoiceNeedsAdb() {
        assertTrue(SetupGuide.features().none { it.needsAdb })
    }

    @Test fun theGuideHasThreeStepsBeforeCompletion() {
        assertEquals(0, SetupGuide.STEP_CONNECTION)
        assertEquals(1, SetupGuide.STEP_IPHONE)
        assertEquals(2, SetupGuide.STEP_FEATURES)
        assertEquals(3, SetupGuide.STEP_DONE)
        assertEquals(4, SetupGuide.STEP_COUNT)
    }

    @Test fun guideOpensByItselfOnlyOnAFreshSetup() {
        assertTrue(SetupGuide.shouldOpenOnLaunch(seen = false, phoneChosen = false))
        assertFalse(SetupGuide.shouldOpenOnLaunch(seen = false, phoneChosen = true))
        assertFalse(SetupGuide.shouldOpenOnLaunch(seen = true, phoneChosen = false))
    }
}
