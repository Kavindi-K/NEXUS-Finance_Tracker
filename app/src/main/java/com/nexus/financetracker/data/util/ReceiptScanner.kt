package com.nexus.financetracker.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Helper utility for capturing bill/receipt photos and executing on-device
 * ML Kit text recognition asynchronously.
 */
object ReceiptScanner {

    /**
     * Creates a temporary file in cache and returns a content URI via FileProvider
     * suitable for the system Camera intent.
     */
    fun createTempImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Runs on-device ML Kit OCR over the provided image URI and parses recognized
     * text using [ReceiptParser].
     */
    suspend fun processImageUri(context: Context, uri: Uri): ParsedReceipt? =
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val parsed = ReceiptParser.parse(visionText.text)
                        continuation.resume(parsed)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
}

