package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Image as SkiaImage
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIScreen
import platform.UIKit.UISwitch
import platform.UIKit.UIView
import platform.UIKit.accessibilityElementsHidden
import platform.UIKit.isAccessibilityElement
import platform.UIKit.drawInRect
import kotlin.math.min

/** Static native glyphs render inside Compose so transparent pixels never create an interop hole. */
@OptIn(ExperimentalForeignApi::class)
internal fun iosSymbolBitmap(symbol: String): ImageBitmap? {
    val image = UIImage.systemImageNamed(symbol) ?: return null
    val imageSize = image.size.useContents { width to height }
    if (imageSize.first <= 0 || imageSize.second <= 0) return null
    val side = 24.0
    val scale = min(side / imageSize.first, side / imageSize.second)
    val width = imageSize.first * scale
    val height = imageSize.second * scale
    val format = UIGraphicsImageRendererFormat().apply {
        opaque = false
        this.scale = UIScreen.mainScreen.scale
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(side, side), format = format)
    val data = renderer.PNGDataWithActions {
        image.drawInRect(CGRectMake((side - width) / 2, (side - height) / 2, width, height))
    }
    val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: return null
    return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}

/** A padded native host contains iOS switch visual overflow and fits its actual laid-out size. */
@OptIn(ExperimentalForeignApi::class)
internal class IosSwitchView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    val control = UISwitch()

    init {
        backgroundColor = UIColor.clearColor
        opaque = false
        isAccessibilityElement = false
        accessibilityElementsHidden = true
        userInteractionEnabled = false
        control.backgroundColor = UIColor.clearColor
        control.opaque = false
        control.isAccessibilityElement = false
        control.accessibilityElementsHidden = true
        control.userInteractionEnabled = false
        addSubview(control)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        control.transform = CGAffineTransformMakeScale(1.0, 1.0)
        control.sizeToFit()
        val available = bounds.useContents { size.width to size.height }
        val native = control.bounds.useContents { size.width to size.height }
        if (native.first <= 0 || native.second <= 0) return
        val scale = min(1.0, min(
            (available.first - 16.0).coerceAtLeast(0.0) / native.first,
            (available.second - 8.0).coerceAtLeast(0.0) / native.second,
        ))
        control.transform = CGAffineTransformMakeScale(scale, scale)
        control.center = CGPointMake(available.first / 2, available.second / 2)
    }
}
