package com.minimarket.aepos.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import com.google.firebase.storage.StorageMetadata
import com.minimarket.aepos.data.remote.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageOptimizer {

    /**
     * Procesa, escala, recorta al centro y comprime la imagen a formato WebP (600x600 @ 80% calidad)
     * idéntico a imageOptimizer.cjs en tienda-pos.
     */
    suspend fun optimizeToWebp(
        context: Context,
        inputUri: Uri,
        targetWidth: Int = 600,
        targetHeight: Int = 600,
        quality: Int = 80
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(Exception("No se pudo abrir la imagen seleccionada."))

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
                ?: return@withContext Result.failure(Exception("Formato de imagen no soportado."))

            // Recorte y escalado proporcional tipo "fit: cover" (sin deformar)
            val croppedScaledBitmap = cropAndScaleCenter(originalBitmap, targetWidth, targetHeight)

            // Comprimir a formato WebP
            val outputStream = ByteArrayOutputStream()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                croppedScaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, outputStream)
            } else {
                @Suppress("DEPRECATION")
                croppedScaledBitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
            }

            val webpBytes = outputStream.toByteArray()
            Result.success(webpBytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sube los bytes WebP directamente a Firebase Storage en la ruta 'productos/{catCode}-{unique}.webp'
     * con Content-Type: 'image/webp', idéntico a tienda-pos.
     */
    suspend fun uploadProductImageToStorage(
        webpBytes: ByteArray,
        categoria: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val storage = FirebaseConfig.storage
            val catCode = categoria.trim()
                .take(3)
                .uppercase()
                .replace(Regex("[^A-Z]"), "")
                .ifBlank { "GEN" }
            val unique = UUID.randomUUID().toString().take(4).uppercase()
            val fileName = "productos/$catCode-$unique.webp"

            val storageRef = storage.reference.child(fileName)
            val metadata = StorageMetadata.Builder()
                .setContentType("image/webp")
                .build()

            storageRef.putBytes(webpBytes, metadata).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Guarda una copia local en caché para visualización offline instantánea.
     */
    suspend fun saveWebpLocally(
        context: Context,
        webpBytes: ByteArray,
        productId: String
    ): String = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "prod_${productId}.webp")
        FileOutputStream(file).use { it.write(webpBytes) }
        file.absolutePath
    }

    /**
     * Escala y recorta al centro ("Center Crop / Fit Cover")
     */
    private fun cropAndScaleCenter(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()

        val scale = maxOf(targetW / srcW, targetH / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale

        val dx = (targetW - scaledW) / 2f
        val dy = (targetH - scaledH) / 2f

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(src, matrix, null)
        return output
    }
}
