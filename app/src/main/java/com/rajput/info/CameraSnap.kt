package com.rajput.info

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object CameraSnap {

    suspend fun capture(ctx: Context): ByteArray? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return null

        return suspendCancellableCoroutine { cont ->
            val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = runCatching {
                mgr.cameraIdList.firstOrNull { id ->
                    mgr.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                }
            }.getOrNull() ?: return@suspendCancellableCoroutine cont.resume(null)

            val chars = runCatching { mgr.getCameraCharacteristics(camId) }.getOrNull()
                ?: return@suspendCancellableCoroutine cont.resume(null)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(ImageFormat.JPEG)
                ?.filter { it.width * it.height <= 1920 * 1080 }
                ?.maxByOrNull { it.width * it.height } ?: Size(1280, 720)

            val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
            var device: CameraDevice? = null
            var session: CameraCaptureSession? = null

            fun cleanup() {
                runCatching { session?.close() }
                runCatching { device?.close() }
                runCatching { reader.close() }
            }

            reader.setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                img.close()
                cleanup()
                if (cont.isActive) cont.resume(bytes)
            }, Handler(Looper.getMainLooper()))

            runCatching {
                mgr.openCamera(camId, object : CameraDevice.StateCallback() {
                    override fun onOpened(cd: CameraDevice) {
                        device = cd
                        val surface = reader.surface
                        runCatching {
                            cd.createCaptureSession(listOf(surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(s: CameraCaptureSession) {
                                        session = s
                                        val req = cd.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                            addTarget(surface)
                                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                        }.build()
                                        runCatching { s.capture(req, null, null) }
                                    }
                                    override fun onConfigureFailed(s: CameraCaptureSession) {
                                        cleanup()
                                        if (cont.isActive) cont.resume(null)
                                    }
                                }, null)
                        }
                    }
                    override fun onDisconnected(cd: CameraDevice) {
                        cd.close()
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onError(cd: CameraDevice, e: Int) {
                        cd.close()
                        if (cont.isActive) cont.resume(null)
                    }
                }, null)
            }.onFailure {
                cleanup()
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
