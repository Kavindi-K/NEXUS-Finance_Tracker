package com.nexus.financetracker.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudinaryImageUploader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CLOUD_NAME = "ddfougzkl"
        private const val UPLOAD_PRESET = "nexus_preset"
        private const val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"
        private const val MAX_IMAGE_DIMENSION = 1024
        private const val COMPRESSION_QUALITY = 82
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun uploadProfileImage(imageUri: Uri, userId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val imageBytes = compressImage(imageUri)
                ?: throw IllegalArgumentException("Could not read or process the selected image.")

            android.util.Log.d("Cloudinary", "Uploading ${imageBytes.size} bytes to $UPLOAD_URL with preset $UPLOAD_PRESET")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .addFormDataPart(
                    "file",
                    "avatar.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            android.util.Log.d("Cloudinary", "Response code: ${response.code}, body: $responseBody")

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                val errorMsg = parseErrorMessage(responseBody) ?: "Cloudinary upload failed with code ${response.code}"
                throw IllegalStateException(errorMsg)
            }

            val json = JSONObject(responseBody)
            val secureUrl = json.optString("secure_url")
            if (secureUrl.isBlank()) {
                throw IllegalStateException("Cloudinary did not return a secure image URL.")
            }

            android.util.Log.d("Cloudinary", "Obtained secureUrl: $secureUrl")
            secureUrl
        }
    }

    private fun compressImage(imageUri: Uri): ByteArray? {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(imageUri) ?: return null

            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size to fit MAX_IMAGE_DIMENSION
            var sampleSize = 1
            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth > MAX_IMAGE_DIMENSION || origHeight > MAX_IMAGE_DIMENSION) {
                val halfWidth = origWidth / 2
                val halfHeight = origHeight / 2
                while ((halfWidth / sampleSize) >= MAX_IMAGE_DIMENSION || (halfHeight / sampleSize) >= MAX_IMAGE_DIMENSION) {
                    sampleSize *= 2
                }
            }

            // Decode bitmap with inSampleSize
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val secondStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val bitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions)
            secondStream.close()

            if (bitmap == null) return null

            // Scale down if still slightly larger
            val scaledBitmap = scaleBitmapToMax(bitmap, MAX_IMAGE_DIMENSION)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun scaleBitmapToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt()
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun parseErrorMessage(jsonString: String?): String? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            val json = JSONObject(jsonString)
            json.optJSONObject("error")?.optString("message")
        } catch (_: Exception) {
            null
        }
    }
}
