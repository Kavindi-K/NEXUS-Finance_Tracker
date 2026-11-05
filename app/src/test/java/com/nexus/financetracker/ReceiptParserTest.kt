package com.nexus.financetracker

import com.nexus.financetracker.data.util.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReceiptParserTest {

    @Test
    fun parse_cargillsFoodCityReceipt_extractsCorrectly() {
        val ocr = """
            CARGILLS FOOD CITY
            STAPLE ROAD, COLOMBO 02
            TEL: 011-2423321
            DATE: 12/10/2026 14:32
            CASHIER: SUNIL
            ---------------------------------
            FRESH MILK 1L         380.00
            KEGALLE EGGS 10PK     420.00
            PRIMA FLOUR 1KG       240.00
            SUNLIGHT SOAP         110.00
            ---------------------------------
            SUB TOTAL            1,150.00
            DISCOUNT                0.00
            TOTAL AMOUNT         1,150.00
            CASH TENDERED        2,000.00
            CHANGE                 850.00
            THANK YOU COME AGAIN
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(1150.00, result!!.amountLKR, 0.01)
        assertTrue(result.isDateDetected)

        val cal = Calendar.getInstance().apply { time = result.date }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(9, cal.get(Calendar.MONTH)) // 0-based: 9 = October
        assertEquals(12, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parse_keellsReceipt_extractsCorrectly() {
        val ocr = """
            KEELLS SUPER
            UNION PLACE
            DATE: 04/11/2026
            TOTAL: LKR 3,420.50
            CASH: 5000.00
            CHANGE: 1579.50
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(3420.50, result!!.amountLKR, 0.01)
        assertTrue(result.isDateDetected)
    }

    @Test
    fun parse_ceypetcoFuelReceipt_extractsCorrectly() {
        val ocr = """
            CEYPETCO FILLING STATION
            KANDY ROAD, KADAWATHA
            DATE: 01-11-2026
            AUTO DIESEL 12.5L
            PRICE/L: 310.00
            NET AMOUNT: RS. 3875.00
            CASH PAYMENT
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(3875.00, result!!.amountLKR, 0.01)
        assertTrue(result.isDateDetected)
    }

    @Test
    fun parse_pereraAndSonsBakery_extractsCorrectly() {
        val ocr = """
            PERERA & SONS BAKERS
            KOLLUPITIYA BRANCH
            DATE: 2026-10-28
            FISH PASTRY x 2       260.00
            CHICKEN BUN x 2       300.00
            ICED COFFEE           250.00
            TOTAL PAYABLE: 810.00
            CASH PAID: 1000.00
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(810.00, result!!.amountLKR, 0.01)
        assertTrue(result.isDateDetected)
    }

    @Test
    fun parse_cebElectricityBill_extractsCorrectly() {
        val ocr = """
            CEB - CEYLON ELECTRICITY BOARD
            ACCOUNT NO: 1234567890
            BILL MONTH: OCTOBER 2026
            DATE: 20/10/2026
            TOTAL DUE: LKR 4,650.00
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(4650.00, result!!.amountLKR, 0.01)
        assertTrue(result.isDateDetected)
    }

    @Test
    fun parse_historicalReceiptDate_extractsReceiptDateNotScanningDate() {
        // Physical receipt from a cash payment made in 2024
        val ocr = """
            COMMUNITY PHARMACY
            GALLE ROAD, BAMBALAPITIYA
            TEL: 011-2589012
            DT: 15/05/2024
            PANADOL 500MG         120.00
            VITAMIN C             450.00
            TOTAL DUE: LKR 570.00
            CASH RECEIVED: 1,000.00
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(570.00, result!!.amountLKR, 0.01)
        assertTrue("Receipt date must be detected", result.isDateDetected)

        val cal = Calendar.getInstance().apply { time = result.date }
        assertEquals("Receipt year must match physical bill", 2024, cal.get(Calendar.YEAR))
        assertEquals("Receipt month must match physical bill", Calendar.MAY, cal.get(Calendar.MONTH))
        assertEquals("Receipt day must match physical bill", 15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parse_receiptWithPhoneAndRegistrationNumbers_prioritizesActualDateLine() {
        val ocr = """
            MEGA HARDWARE MART
            REG NO: 12/04/85
            TEL: 011-234-5678
            FAX: 011-234-5679
            INVOICE DATE : 22-Sep-2025
            SCREW PACK x 5        500.00
            NET TOTAL : 500.00
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(500.00, result!!.amountLKR, 0.01)
        assertTrue(result.isDateDetected)

        val cal = Calendar.getInstance().apply { time = result.date }
        assertEquals(2025, cal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, cal.get(Calendar.MONTH))
        assertEquals(22, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parse_twoDigitYearAtStart_parsesAs2026Oct13_not2013Oct26() {
        // Receipt with date formatted as 26-10-13 (yy-MM-dd)
        val ocr = """
            KEELLS SUPERMARKET
            DATE: 26-10-13
            TOTAL: LKR 1,500.00
            CASH PAYMENT
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(1500.00, result!!.amountLKR, 0.01)
        assertTrue(result.isDateDetected)

        val cal = Calendar.getInstance().apply { time = result.date }
        assertEquals("Year must be 2026, not 2013", 2026, cal.get(Calendar.YEAR))
        assertEquals("Month must be October (9)", Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals("Day must be 13, not 26", 13, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parse_twoDigitYearWithSlashes_parsesAs2026Oct13() {
        val ocr = """
            CARGILLS FOOD CITY
            DATE: 26/10/13 14:20
            TOTAL: LKR 3,200.00
            CASH
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertNotNull(result)
        assertEquals(3200.00, result!!.amountLKR, 0.01)

        val cal = Calendar.getInstance().apply { time = result.date }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(13, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parse_blankText_returnsNull() {
        val result = ReceiptParser.parse("   ")
        assertNull(result)
    }
}
