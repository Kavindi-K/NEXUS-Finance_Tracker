package com.nexus.financetracker.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parsed information extracted from a physical paper bill or receipt image via OCR.
 *
 * @param amountLKR Total payable amount extracted from the receipt.
 * @param date The transaction date extracted directly from the physical receipt,
 *             or fallback to current date if completely absent on the bill.
 * @param isDateDetected Whether a valid transaction date was recognized on the receipt chit.
 * @param rawText Complete OCR recognized text.
 */
data class ParsedReceipt(
    val amountLKR: Double,
    val date: Date = Date(),
    val rawText: String = "",
    val isDateDetected: Boolean = false
)

/**
 * Utility for parsing OCR text from paper bills, supermarket receipts, fuel chits,
 * utility bills, and merchant invoices into structured [ParsedReceipt] data.
 * Designed for automatic cash expense logging.
 */
object ReceiptParser {

    private val DATE_FORMATS = listOf(
        // 4-digit year formats
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "yyyy.MM.dd",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd.MM.yyyy",
        "MM/dd/yyyy",
        "MM-dd-yyyy",

        // Textual month formats
        "dd-MMM-yyyy",
        "dd MMM yyyy",
        "dd/MMM/yyyy",
        "dd-MMMM-yyyy",
        "dd MMMM yyyy",
        "MMM dd, yyyy",
        "MMM dd yyyy",
        "MMMM dd, yyyy",
        "MMMM dd yyyy",
        "dd-MMM-yy",
        "dd MMM yy",
        "yyyy-MMM-dd",

        // 2-digit year formats (yy-MM-dd and dd-MM-yy)
        "yy-MM-dd",
        "yy/MM/dd",
        "yy.MM.dd",
        "dd/MM/yy",
        "dd-MM-yy",
        "dd.MM.yy",
        "MM/dd/yy",
        "MM-dd-yy"
    )

    private val DATE_REGEX_PATTERNS = listOf(
        // 1. Textual month names: "12 Oct 2024", "12-Oct-2024", "12/Oct/2024", "12 October 2024", "12-Oct-24"
        Pattern.compile("""\b(\d{1,2}[\s/\.-](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\s/\.-]\d{2,4})\b""", Pattern.CASE_INSENSITIVE),
        // 2. Month-first textual: "Oct 12, 2024", "October 12, 2024", "Oct 12 2024"
        Pattern.compile("""\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{1,2},?\s+\d{2,4})\b""", Pattern.CASE_INSENSITIVE),
        // 3. ISO format: "2024-10-12", "2024/10/12", "2024.10.12"
        Pattern.compile("""\b(\d{4}[/\.-]\d{1,2}[/\.-]\d{1,2})\b"""),
        // 4. Standard numerical: "12/10/2024", "12-10-2024", "12.10.2024", "12/10/24", "12-10-24", "12.10.24", "26-10-13"
        Pattern.compile("""\b(\d{1,2}[/\.-]\d{1,2}[/\.-]\d{2,4})\b""")
    )

    /**
     * Parses raw OCR text from a photographed bill/receipt into structured expense data.
     * Returns null if no valid total amount could be recognized.
     */
    fun parse(rawText: String): ParsedReceipt? {
        if (rawText.isBlank()) return null

        val lines = rawText.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // 1. Total Amount Extraction
        val amount = extractAmount(rawText, lines) ?: return null

        // 2. Receipt Date Extraction (Priority: explicitly printed date on receipt chit)
        val extractedDate = extractDate(rawText, lines)
        val date = extractedDate ?: Date()

        return ParsedReceipt(
            amountLKR = amount,
            date = date,
            rawText = rawText,
            isDateDetected = extractedDate != null
        )
    }

    private fun extractAmount(rawText: String, lines: List<String>): Double? {
        // High-priority patterns with explicit total indicators
        val totalPatterns = listOf(
            Pattern.compile("""(?:GRAND\s*TOTAL|NET\s*TOTAL|TOTAL\s*AMOUNT|TOTAL\s*PAYABLE|TOTAL\s*DUE|AMOUNT\s*DUE)\s*[:\-]?\s*(?:LKR|RS\.?)?\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:^|\s)TOTAL\s*[:\-]?\s*(?:LKR|RS\.?)?\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:^|\s)NET\s*[:\-]?\s*(?:LKR|RS\.?)?\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:LKR|RS\.?)\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        )

        for (pat in totalPatterns) {
            val matcher = pat.matcher(rawText)
            val candidates = mutableListOf<Double>()
            while (matcher.find()) {
                val rawVal = matcher.group(1)?.replace(",", "")
                val num = rawVal?.toDoubleOrNull()
                if (num != null && num in 1.0..10_000_000.0) {
                    candidates.add(num)
                }
            }
            if (candidates.isNotEmpty()) {
                // Usually the last occurrence in a receipt is the final payable total
                return candidates.last()
            }
        }

        // Fallback: search for decimal amount numbers on lines without 'CASH', 'TENDER', 'CHANGE'
        val fallbackPattern = Pattern.compile("""([\d,]+\.\d{2})""")
        val fallbackCandidates = mutableListOf<Double>()
        for (line in lines) {
            val upperLine = line.uppercase(Locale.US)
            if (upperLine.contains("CHANGE") || upperLine.contains("CASH TENDER") || upperLine.contains("BALANCE")) {
                continue
            }
            val matcher = fallbackPattern.matcher(line)
            while (matcher.find()) {
                val num = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (num != null && num in 1.0..5_000_000.0) {
                    fallbackCandidates.add(num)
                }
            }
        }

        return fallbackCandidates.maxOrNull()
    }

    private fun extractDate(rawText: String, lines: List<String>): Date? {
        val dateKeywordPattern = Pattern.compile(
            """(?i)\b(?:DATE|DT|DATED|BILL\s*DATE|TXN\s*DATE|TRANS\s*DATE|INVOICE\s*DATE|RECEIPT\s*DATE|DATE\s*/\s*TIME)\b\s*[:\-\s]?\s*(.*)"""
        )

        // Pass 1: Look on lines with explicit date indicator keywords (Highest Priority)
        for (line in lines) {
            val matcher = dateKeywordPattern.matcher(line)
            if (matcher.find()) {
                val candidateText = matcher.group(1)?.takeIf { it.isNotBlank() } ?: line
                findDateInString(candidateText)?.let { return it }
            }
        }

        // Pass 2: Look across lines, skipping lines that clearly represent contact/tax numbers
        for (line in lines) {
            val upper = line.uppercase(Locale.US)
            if (upper.contains("TEL:") || upper.contains("TEL ") || upper.contains("PHONE") ||
                upper.contains("MOBILE") || upper.contains("FAX") || upper.contains("VAT") ||
                upper.contains("TIN") || upper.contains("SVAT") || upper.contains("REG NO") ||
                upper.contains("HOTLINE")) {
                continue
            }
            findDateInString(line)?.let { return it }
        }

        // Pass 3: Search entire text as a general fallback
        return findDateInString(rawText)
    }

    private fun findDateInString(text: String): Date? {
        val foundCandidates = mutableListOf<Date>()

        for (regex in DATE_REGEX_PATTERNS) {
            val matcher = regex.matcher(text)
            while (matcher.find()) {
                val rawDateStr = matcher.group(1)?.trim() ?: continue
                val candidates = parseDateCandidates(rawDateStr)
                foundCandidates.addAll(candidates)
            }
        }

        return selectBestDate(foundCandidates)
    }

    private fun parseDateCandidates(rawDateStr: String): List<Date> {
        val candidates = mutableListOf<Date>()
        val futureLimit = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }.time

        for (fmtStr in DATE_FORMATS) {
            try {
                val sdf = SimpleDateFormat(fmtStr, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(rawDateStr)
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply { time = parsed }
                    val year = cal.get(Calendar.YEAR)
                    // Valid receipt year range: 2000 to current year + 1
                    if (year in 2000..2035 && !parsed.after(futureLimit)) {
                        candidates.add(parsed)
                    }
                }
            } catch (_: Exception) {
                // Try next format
            }
        }
        return candidates
    }

    private fun selectBestDate(candidates: List<Date>): Date? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // When multiple interpretations exist:
        // (e.g. 26-10-13 as yy-MM-dd -> 2026-10-13 vs dd-MM-yy -> 2013-10-26)
        // Pick the date closest to the current time, because physical receipts are current/recent transactions
        val now = System.currentTimeMillis()
        return candidates.minByOrNull { Math.abs(now - it.time) }
    }
}
