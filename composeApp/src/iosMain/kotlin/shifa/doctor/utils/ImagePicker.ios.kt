package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

// PHPicker needs no photo-library permission. Every photo is redrawn upright at most PHOTO_MAX_SIDE and re-encoded
// as JPEG, so HEIC photos decode and upload. Adapted from the patient app's picker.
@Composable
actual fun rememberImagePickerLauncher(onPicked: (PickedImage?) -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    val delegate =
        remember {
            object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(
                    picker: PHPickerViewController,
                    didFinishPicking: List<*>,
                ) {
                    picker.dismissViewControllerAnimated(true, completion = null)
                    val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
                    if (provider == null) return picked(null)
                    provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                        val jpeg = data?.downsampled()?.prepared()
                        dispatch_async(dispatch_get_main_queue()) { picked(jpeg?.let { PickedImage("photo.jpg", it) }) }
                    }
                }
            }
        }
    return {
        val config =
            PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter
                selectionLimit = 1
            }
        val picker = PHPickerViewController(config)
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(picker, true, null)
    }
}

@Composable
actual fun rememberCameraLauncher(
    onTaken: (PickedImage?) -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val taken by rememberUpdatedState(onTaken)
    val denied by rememberUpdatedState(onDenied)
    val delegate =
        remember {
            object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>,
                ) {
                    val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                    val jpeg = image?.prepared()
                    picker.dismissViewControllerAnimated(true) { taken(jpeg?.let { PickedImage("photo.jpg", it) }) }
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    picker.dismissViewControllerAnimated(true) { taken(null) }
                }
            }
        }
    val present = {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(picker, true, null)
    }
    return launch@{
        val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(camera)) return@launch taken(null)
        // Without access the system picker shows only a black screen, so a refusal goes to the Settings banner.
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> present()
            AVAuthorizationStatusNotDetermined ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) { if (granted) present() else denied() }
                }
            else -> denied()
        }
    }
}

// Decoded straight to at most PHOTO_MAX_SIDE (and upright) by ImageIO, so a 48 MP photo never sits in memory at
// full size; null if ImageIO can't read it.
@OptIn(ExperimentalForeignApi::class)
private fun NSData.downsampled(): UIImage? {
    // The bridged reference is ours to release; the image source keeps its own.
    val data = CFBridgingRetain(this)
    val source = CGImageSourceCreateWithData(data as CFDataRef?, null)
    CFRelease(data)
    if (source == null) return null
    val options =
        CFDictionaryCreateMutable(null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    val side =
        memScoped {
            val value = alloc<IntVar>().apply { this.value = PHOTO_MAX_SIDE }
            CFNumberCreate(null, kCFNumberIntType, value.ptr)
        }
    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
    CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, side)
    val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
    CFRelease(side)
    CFRelease(options)
    CFRelease(source)
    return image?.let { UIImage.imageWithCGImage(it).also { _ -> CGImageRelease(image) } }
}

// Drawing applies the orientation; the target size caps the longest side (in points, at scale 1 so pixels = points).
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.prepared(): ByteArray? {
    val (w, h) = size.useContents { width to height }
    val longest = maxOf(w, h)
    val factor = if (longest > PHOTO_MAX_SIDE) PHOTO_MAX_SIDE / longest else 1.0
    val (tw, th) = (w * factor) to (h * factor)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(tw, th), false, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, tw, th))
    val redrawn = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return UIImageJPEGRepresentation(redrawn ?: this, PHOTO_JPEG_QUALITY / 100.0)?.toByteArray()
}

actual fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
}
