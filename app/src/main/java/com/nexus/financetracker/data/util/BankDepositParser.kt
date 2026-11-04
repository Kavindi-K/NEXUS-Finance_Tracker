package com.nexus.financetracker.data.util

import com.nexus.financetracker.domain.model.DetectedBankDeposit
import com.nexus.financetracker.domain.model.IncomeSource
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parses incoming credit/deposit alerts from Sri Lankan banks into [DetectedBankDeposit].
 * Strictly scoped to inbound income detection only.
 */
object BankDepositParser {

    /**
     * Parses an incoming SMS or simulated alert body into a DetectedBankDeposit.
     * Returns null if the text does not match an incoming credit/deposit alert.
     */
    fun parse(sender: String = "", messageBody: String): DetectedBankDeposit? {
        val upperBody = messageBody.uppercase(Locale.US)
        val upperSender = sender.uppercase(Locale.US)

        // Only parse incoming credit/deposit messages (ignore debit/expense alerts)
        val isCredit = upperBody.contains("CREDITED") ||
                upperBody.contains("DEPOSIT") ||
                upperBody.contains("RECEIVED") ||
                upperBody.contains("TRANSFER CREDIT")

        if (!isCredit) return null

        // Detect Bank Name
        val bankName = when {
            upperSender.contains("COMBANK") || upperBody.contains("COMMERCIAL BANK") || upperBody.contains("COMBANK") -> "Commercial Bank"
            upperSender.contains("SAMPATH") || upperBody.contains("SAMPATH") -> "Sampath Bank"
            upperSender.contains("HNB") || upperBody.contains("HATTON NATIONAL") || upperBody.contains("HNB") -> "Hatton National Bank"
            upperSender.contains("BOC") || upperBody.contains("BANK OF CEYLON") || upperBody.contains("BOC") -> "Bank of Ceylon"
            upperSender.contains("SEYLAN") || upperBody.contains("SEYLAN") -> "Seylan Bank"
            upperSender.contains("NDB") || upperBody.contains("NDB") -> "NDB Bank"
            upperSender.contains("NTB") || upperBody.contains("NATIONS TRUST") -> "Nations Trust Bank"
            upperSender.contains("DFCC") || upperBody.contains("DFCC") -> "DFCC Bank"
            upperSender.contains("PAN ASIA") || upperBody.contains("PABC") -> "Pan Asia Bank"
            upperSender.contains("HSBC") || upperBody.contains("HSBC") -> "HSBC"
            else -> "Bank Deposit"
        }

        // Extract Amount: e.g. "LKR 135,000.00" or "Rs. 135,000" or "LKR 135000"
        val amountPattern = Pattern.compile("""(?:LKR|RS\.?)\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val amountMatcher = amountPattern.matcher(messageBody)
        val amount = if (amountMatcher.find()) {
            val raw = amountMatcher.group(1)?.replace(",", "")
            raw?.toDoubleOrNull() ?: return null
        } else {
            return null
        }

        if (amount <= 0.0) return null

        // Extract Account Number: e.g. "A/C *1234" or "A/C 1234" or "Account 1234"
        val acctPattern = Pattern.compile("""(?:A\/C|ACCOUNT)\s*\*?([0-9Xx*]+)""", Pattern.CASE_INSENSITIVE)
        val acctMatcher = acctPattern.matcher(messageBody)
        val account = if (acctMatcher.find()) {
            val num = acctMatcher.group(1) ?: "1234"
            if (!num.startsWith("*")) "*${num.takeLast(4)}" else num
        } else {
            "*1234"
        }

        // Extract Reference: e.g. "Ref: SALARY-OCT" or "Ref: 98765"
        val refPattern = Pattern.compile("""(?:REF|REFERENCE)[:\s]+([A-Za-z0-9\-_]+)""", Pattern.CASE_INSENSITIVE)
        val refMatcher = refPattern.matcher(messageBody)
        val reference = if (refMatcher.find()) refMatcher.group(1) else null

        // Auto-suggest source based on reference and text
        val suggestedSource = when {
            upperBody.contains("SALARY") || upperBody.contains("PAYROLL") || upperBody.contains("WAGE") -> IncomeSource.SALARY
            upperBody.contains("FREELANCE") || upperBody.contains("UPWORK") || upperBody.contains("FIVERR") || upperBody.contains("CONSULTING") -> IncomeSource.FREELANCE
            upperBody.contains("CRYPTO") || upperBody.contains("BINANCE") -> IncomeSource.CRYPTO
            else -> IncomeSource.SALARY // Most common bank credit in SL
        }

        return DetectedBankDeposit(
            bankName = bankName,
            accountNumber = account,
            amountLKR = amount,
            date = Date(),
            reference = reference ?: "Bank Deposit",
            rawText = messageBody,
            suggestedSource = suggestedSource
        )
    }
}

