package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import timber.log.Timber

/** A black-on-white QR code a phone camera can scan */
@Composable
fun QrCode(
    text: String,
    modifier: Modifier = Modifier,
) {
    val matrix: BitMatrix? =
        remember(text) {
            try {
                QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
            } catch (ex: Exception) {
                Timber.w(ex, "Could not make a QR code")
                null
            }
        }
    if (matrix == null) return
    Canvas(
        modifier =
            modifier
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(8.dp),
    ) {
        val cell = minOf(size.width / matrix.width, size.height / matrix.height)
        val left = (size.width - cell * matrix.width) / 2
        val top = (size.height - cell * matrix.height) / 2
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(left + x * cell, top + y * cell),
                        // A hair bigger so there are no thin lines between cells
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}
