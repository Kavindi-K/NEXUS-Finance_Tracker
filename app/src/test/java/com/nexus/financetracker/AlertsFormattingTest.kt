package com.nexus.financetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsFormattingTest {

    @Test
    fun goalAchievedMessage_formatsCorrectlyWithoutIllegalFormatConversionException() {
        val targetAmountLKR = 100000.0
        val targetFormatted = "LKR %,.0f".format(targetAmountLKR)
        val message = "Congratulations! You've reached 100% of your target ($targetFormatted)!"

        assertTrue(message.contains("100% of your target (LKR 100,000)!"))
    }

    @Test
    fun goalAchievedMessage_escapedPercent_doesNotThrow() {
        val targetAmountLKR = 50000.0
        val formatted = "Congratulations! You've reached 100%% of your target (LKR %,.0f)!".format(targetAmountLKR)
        assertEquals("Congratulations! You've reached 100% of your target (LKR 50,000)!", formatted)
    }
}

