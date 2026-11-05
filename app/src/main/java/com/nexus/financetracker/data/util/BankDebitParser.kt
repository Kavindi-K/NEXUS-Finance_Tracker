package com.nexus.financetracker.data.util

import com.nexus.financetracker.domain.model.DetectedBankExpense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parses incoming debit, card purchase, and expense alerts from Sri Lankan banks into [DetectedBankExpense].
 * Strictly scoped to outbound expense detection only.
 */
object BankDebitParser {

    private val DEBIT_KEYWORDS = listOf(
        "DEBITED", "DEBIT", " DR ", " DR. ", " DR:", " DR/", "DR ", "DR.",
        "PURCHASE", "PURCHASED", "PAID", "PAYMENT", "SPENT", "SPEND",
        "WITHDRAWAL", "WITHDRAWN", "WITHDREW", "CARD USED", "USED FOR", "USED AT",
        "CHARGED", "CHARGE", "TRANSFERRED", "TRANSFER TO", "SENT TO", "TRF TO",
        "SETTLED", "BILL PAYMENT", "POS", "OUTWARD"
    )

    /**
     * Parses an incoming SMS or simulated alert body into a DetectedBankExpense.
     * Returns null if the text does not match an outgoing debit/expense alert.
     */
    fun parse(sender: String = "", messageBody: String): DetectedBankExpense? {
        val upperBody = messageBody.uppercase(Locale.US)
        val upperSender = sender.uppercase(Locale.US)

        // If it explicitly indicates an incoming salary/credit and doesn't indicate a debit, skip
        val isExplicitCredit = (upperBody.contains("CREDITED TO") ||
                upperBody.contains("HAS BEEN CREDITED") ||
                upperBody.contains("TRANSFER CREDIT") ||
                (upperBody.contains("CREDITED") && !upperBody.contains("DEBIT"))) &&
                !upperBody.contains("DEBIT") &&
                !upperBody.contains("DR.") &&
                !upperBody.contains("DR ")

        if (isExplicitCredit) return null

        // Must contain outgoing/debit indicators
        val isDebit = DEBIT_KEYWORDS.any { upperBody.contains(it) }
        if (!isDebit) return null

        // Detect Bank Name
        val bankName = when {
            upperSender.contains("COMBANK") || upperBody.contains("COMMERCIAL BANK") || upperBody.contains("COMBANK") -> "Commercial Bank"
            upperSender.contains("SAMPATH") || upperBody.contains("SAMPATH") -> "Sampath Bank"
            upperSender.contains("HNB") || upperBody.contains("HATTON NATIONAL") || upperBody.contains("HNB") -> "Hatton National Bank"
            upperSender.contains("BOC") || upperBody.contains("BANK OF CEYLON") || upperBody.contains("BOC") -> "Bank of Ceylon"
            upperSender.contains("PEOPLES") || upperBody.contains("PEOPLE'S BANK") || upperBody.contains("PEOPLES BANK") -> "People's Bank"
            upperSender.contains("SEYLAN") || upperBody.contains("SEYLAN") -> "Seylan Bank"
            upperSender.contains("NDB") || upperBody.contains("NDB") -> "NDB Bank"
            upperSender.contains("NTB") || upperBody.contains("NATIONS TRUST") || upperBody.contains("FRIMI") -> "Nations Trust Bank"
            upperSender.contains("DFCC") || upperBody.contains("DFCC") -> "DFCC Bank"
            upperSender.contains("PAN ASIA") || upperBody.contains("PABC") -> "Pan Asia Bank"
            upperSender.contains("CARGILLS") || upperBody.contains("CARGILLS BANK") -> "Cargills Bank"
            upperSender.contains("UNION") || upperBody.contains("UNION BANK") -> "Union Bank"
            upperSender.contains("AMANA") || upperBody.contains("AMANA BANK") -> "Amana Bank"
            upperSender.contains("HSBC") || upperBody.contains("HSBC") -> "HSBC"
            else -> "Bank Expense"
        }

        // Extract Amount: supports "LKR 4,250.00", "LKR. 4,250.00", "LKR: 4250", "Rs. 4,250.00", "SLR 4,250", etc.
        val amountPattern = Pattern.compile("""(?:LKR|RS|SLR)[\.:/-]?\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val amountMatcher = amountPattern.matcher(messageBody)
        val amount = if (amountMatcher.find()) {
            val raw = amountMatcher.group(1)?.replace(",", "")
            raw?.toDoubleOrNull() ?: return null
        } else {
            return null
        }

        if (amount <= 0.0) return null

        // Extract Account/Card Number: supports "A/C *1234", "Card *5678", "ending in 1234", "Account 1234"
        val acctPattern = Pattern.compile("""(?:A\/C|ACCOUNT|CARD|ENDING IN|ENDING)\s*\*?([0-9Xx*]+)""", Pattern.CASE_INSENSITIVE)
        val acctMatcher = acctPattern.matcher(messageBody)
        val account = if (acctMatcher.find()) {
            val num = acctMatcher.group(1) ?: "1234"
            if (!num.startsWith("*")) "*${num.takeLast(4)}" else num
        } else {
            "*1234"
        }

        // Detect ATM withdrawal vs POS/Card purchase
        val isAtm = upperBody.contains("ATM") || upperBody.contains("WITHDRAWAL") || upperBody.contains("WITHDRAWN")

        // Extract Merchant / Store name: e.g. "at CARGILLS on 04-NOV", "to KECELLS on ...", "at UBER"
        var merchant: String? = null
        if (isAtm) {
            merchant = "ATM Cash Withdrawal"
        } else {
            val merchantPattern = Pattern.compile(
                """(?:AT|TO|MERCHANT)\s+([A-Za-z0-9\s\-&'.]+?)(?:\s+(?:ON|REF|DATE|VAL|\.|FOR)\b|$)""",
                Pattern.CASE_INSENSITIVE
            )
            val merchantMatcher = merchantPattern.matcher(messageBody)
            if (merchantMatcher.find()) {
                val found = merchantMatcher.group(1)?.trim()
                if (!found.isNullOrBlank() && found.length > 2 && !found.equals("ATM", ignoreCase = true)) {
                    merchant = found
                }
            }
        }

        // Extract Reference: e.g. "Ref: 98765" or "Ref: POS-1234"
        val refPattern = Pattern.compile("""(?:REF|REFERENCE)[:\s]+([A-Za-z0-9\-_]+)""", Pattern.CASE_INSENSITIVE)
        val refMatcher = refPattern.matcher(messageBody)
        val reference = if (refMatcher.find()) refMatcher.group(1) else null

        // Default account: CASH for ATM withdrawal, CARD for card/POS/online debits
        val accountType = if (isAtm) ExpenseAccount.CASH else ExpenseAccount.CARD

        // Deterministic ID based on SMS content to prevent duplicates between live receiver and inbox scanner
        val contentSignature = "${bankName}_${account}_${amount}_${merchant ?: reference ?: ""}_${messageBody.trim()}"
        val deterministicId = "sms_exp_${contentSignature.hashCode().toString(16)}"

        return DetectedBankExpense(
            id = deterministicId,
            bankName = bankName,
            accountNumber = account,
            amountLKR = amount,
            date = Date(),
            merchant = merchant ?: (reference ?: "Card Debit"),
            reference = reference,
            rawText = messageBody,
            accountType = accountType,
            classification = ExpenseClassification.DISCRETIONARY
        )
    }
}
