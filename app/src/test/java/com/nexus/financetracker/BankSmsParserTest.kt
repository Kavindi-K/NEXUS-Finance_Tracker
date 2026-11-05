package com.nexus.financetracker

import com.nexus.financetracker.data.util.BankDebitParser
import com.nexus.financetracker.data.util.BankDepositParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BankSmsParserTest {

    @Test
    fun parseIncome_commercialBankSalary() {
        val sms = "Commercial Bank Alert: Your A/C *1234 has been credited with LKR 125,000.00 on 05-NOV-2026. Ref: SALARY-NOV."
        val deposit = BankDepositParser.parse("COMBANK", sms)
        assertNotNull(deposit)
        assertEquals("Commercial Bank", deposit!!.bankName)
        assertEquals(125000.0, deposit.amountLKR, 0.01)
        assertEquals("*1234", deposit.accountNumber)
    }

    @Test
    fun parseIncome_withDotInLkr() {
        val sms = "Your A/C *5678 has received a deposit of LKR. 45,000.00 from Sampath Bank on 05-NOV-2026. Ref: UPWORK-PAY."
        val deposit = BankDepositParser.parse("SAMPATH", sms)
        assertNotNull(deposit)
        assertEquals(45000.0, deposit!!.amountLKR, 0.01)
    }

    @Test
    fun parseIncome_shouldNotParseDebitAsIncome() {
        val sms = "Commercial Bank Alert: Your Card *1234 was debited with LKR 4,250.00 at CARGILLS FOOD CITY on 05-NOV-2026. Ref: POS-9812."
        val deposit = BankDepositParser.parse("COMBANK", sms)
        assertNull("Debit message must not be parsed as income deposit", deposit)
    }

    @Test
    fun parseIncome_shouldNotParseDepositAccountDebitAsIncome() {
        val sms = "Debit from deposit A/C *1234 of LKR 3,000.00 at Keells"
        val deposit = BankDepositParser.parse("BOC", sms)
        assertNull("Debit from deposit account must not be parsed as income", deposit)
    }

    @Test
    fun parseExpense_commercialBankCardDebit() {
        val sms = "Commercial Bank Alert: Your Card *1234 was debited with LKR 4,250.00 at CARGILLS FOOD CITY on 05-NOV-2026. Ref: POS-9812."
        val expense = BankDebitParser.parse("COMBANK", sms)
        assertNotNull(expense)
        assertEquals("Commercial Bank", expense!!.bankName)
        assertEquals(4250.0, expense.amountLKR, 0.01)
        assertEquals("*1234", expense.accountNumber)
        assertEquals("CARGILLS FOOD CITY", expense.merchant)
    }

    @Test
    fun parseExpense_sampathBankFuelSpend() {
        val sms = "Card *5678 used for LKR 2,500.00 at CEYPETCO FUEL on 05-NOV-2026. Ref: FUEL-3321."
        val expense = BankDebitParser.parse("SAMPATH", sms)
        assertNotNull(expense)
        assertEquals("Sampath Bank", expense!!.bankName)
        assertEquals(2500.0, expense.amountLKR, 0.01)
        assertEquals("CEYPETCO FUEL", expense.merchant)
    }

    @Test
    fun parseExpense_bocDrFormat() {
        val sms = "BOC Alert: A/C *9876 Dr. LKR 2,000.00 on 05-11-2026"
        val expense = BankDebitParser.parse("BOC", sms)
        assertNotNull(expense)
        assertEquals("Bank of Ceylon", expense!!.bankName)
        assertEquals(2000.0, expense.amountLKR, 0.01)
        assertEquals("*9876", expense.accountNumber)
    }

    @Test
    fun parseExpense_dotInCurrency_LKR() {
        val sms = "Your account *1234 has been debited for LKR. 2,500.00"
        val expense = BankDebitParser.parse("COMBANK", sms)
        assertNotNull(expense)
        assertEquals(2500.0, expense!!.amountLKR, 0.01)
    }

    @Test
    fun parseExpense_atmCashWithdrawal() {
        val sms = "Commercial Bank Alert: ATM withdrawal of LKR 10,000.00 from A/C *1234 on 05-NOV-2026."
        val expense = BankDebitParser.parse("COMBANK", sms)
        assertNotNull(expense)
        assertEquals(10000.0, expense!!.amountLKR, 0.01)
        assertEquals("ATM Cash Withdrawal", expense.merchant)
        assertEquals("cash", expense.accountType)
    }

    @Test
    fun parseExpense_deterministicIdForDeduplication() {
        val sms = "Commercial Bank Alert: Your Card *1234 was debited with LKR 4,250.00 at CARGILLS FOOD CITY on 05-NOV-2026. Ref: POS-9812."
        val expense1 = BankDebitParser.parse("COMBANK", sms)
        val expense2 = BankDebitParser.parse("COMBANK", sms)
        assertNotNull(expense1)
        assertNotNull(expense2)
        assertEquals(expense1!!.id, expense2!!.id)
    }
}

