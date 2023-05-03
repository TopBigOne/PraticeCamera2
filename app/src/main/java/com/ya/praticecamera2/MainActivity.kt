package com.ya.praticecamera2

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import android.media.ImageReader
import android.media.MediaActionSound
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.darylgo.camera2.sample.SettableFuture
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.log

class MainActivity : AppCompatActivity(), Handler.Callback {
    companion object {
        private const val TAG = "MainActivity : "
        private const val MSG_OPEN_CAMERA: Int = 1
        private const val MSG_CLOSE_CAMERA: Int = 2
        private const val MSG_CREATE_SESSION: Int = 3
        private const val MSG_CLOSE_SESSION: Int = 4
        private const val MSG_SET_PREVIEW_SIZE: Int = 5
        private const val MSG_START_PREVIEW: Int = 6
        private const val MSG_STOP_PREVIEW: Int = 7
        private const val MSG_SET_IMAGE_SIZE: Int = 8
        private const val MSG_CAPTURE_IMAGE: Int = 9
        private const val MSG_CAPTURE_IMAGE_BURST: Int = 10
        private const val MSG_START_CAPTURE_IMAGE_CONTINUOUSLY: Int = 11
        private const val MSG_CREATE_REQUEST_BUILDERS: Int = 12

        private const val MAX_PREVIEW_WIDTH: Int = 1440
        private const val MAX_PREVIEW_HEIGHT: Int = 1080
        private const val MAX_IMAGE_WIDTH: Int = 4032
        private const val MAX_IMAGE_HEIGHT: Int = 3024

        private const val REQUEST_PERMISSION_CODE: Int = 1
        private val REQUIRED_PERMISSIONS: Array<String> = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val cameraManager: CameraManager by lazy { getSystemService(CameraManager::class.java) }

    private val deviceOrientationListener: DeviceOrientationListener by lazy { DeviceOrientationListener(this) }

    //  view
    private val cameraPreview: CameraPreview by lazy { findViewById<CameraPreview>(R.id.camera_preview) }
    private val thumbnailView: ImageView by lazy { findViewById<ImageView>(R.id.thumbnail_view) }
    private val captureImageButton: ImageButton by lazy { findViewById<ImageButton>(R.id.capture_image_button) }

    // 相机属性
    private var frontCameraCharacteristics: CameraCharacteristics? = null
    private var cameraDeviceFuture: SettableFuture<CameraDevice>? = null
    private var cameraCharacteristicsFuture: SettableFuture<CameraCharacteristics>? = null
    private var captureSessionFuture: SettableFuture<CameraCaptureSession>? = null
    private var previewSurfaceViewFuture: SettableFuture<SurfaceTexture>? = null

    private var previewSurface: Surface? = null
    private var previewDataImageReader: ImageReader? = null
    private var previewDataSurface: Surface? = null

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null


    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var previewImageRequestBuilder: CaptureRequest.Builder? = null
    private var captureImageRequestBuilder: CaptureRequest.Builder? = null


    private var frontCameraId: String? = null

    private var backCameraId: String? = null
    private var backCameraCharacteristics: CameraCharacteristics? = null
    private var jpegSurface: Surface? = null
    private var jpegImageReader: ImageReader? = null


    @SuppressLint("MissingPermission")
    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_OPEN_CAMERA -> {
                Log.d(TAG, "handleMessage: MSG_OPEN_CAMERA")
                cameraDeviceFuture = SettableFuture()
                cameraCharacteristicsFuture = SettableFuture()
                val cameraId = msg.obj as String
                val cameraStateCallback = CameraStateCallback()
                // 打开相机
                cameraManager.openCamera(cameraId, cameraStateCallback, mainHandler);
            }
            MSG_CLOSE_CAMERA -> {
                Log.d(TAG, "handleMessage: MSG_CLOSE_CAMERA")
                val cameraDevice = cameraDeviceFuture?.get()
                cameraDevice?.close()
                cameraDeviceFuture = null
                cameraCharacteristicsFuture = null
            }
            MSG_CLOSE_SESSION -> {
                Log.d(TAG, "Handle message: MSG_CLOSE_SESSION")
                val captureSession = captureSessionFuture?.get()
                captureSession?.close()
                captureSessionFuture = null
            }
            MSG_CREATE_SESSION -> {
                Log.d(TAG, "Handle message: MSG_CREATE_SESSION")
                val sessionStateCallback = SessionStateCallback()
                val outputs = mutableListOf<Surface>()
                val previewSurface = previewSurface
                val previewDataSurface = previewDataSurface
                val jepgSurface = jpegSurface
                previewSurface?.let {
                    outputs.add(previewSurface)
                }

                previewDataSurface?.let {
                    outputs.add(previewDataSurface)
                }

                jepgSurface?.let {
                    outputs.add(jepgSurface)
                }

                captureSessionFuture = SettableFuture()
                val cameraDevice = cameraDeviceFuture?.get()
                // 创建捕捉会话
                cameraDevice?.createCaptureSession(outputs, sessionStateCallback, mainHandler)
            }


            MSG_CREATE_REQUEST_BUILDERS -> {
                Log.d(TAG, "Handle message: MSG_CREATE_REQUEST_BUILDERS")
                val cameraDevice = cameraDeviceFuture?.get()
                cameraDevice?.let {
                    previewImageRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureImageRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                }
            }
            MSG_START_PREVIEW -> {
                Log.d(TAG, "Handle message: MSG_START_PREVIEW")
                val cameraDevice = cameraDeviceFuture?.get()
                val captureSession = captureSessionFuture?.get()
                cameraDevice?.let {
                    if(previewSurface == null) {
                        Log.e(TAG, "handleMessage: previewSurface is NULL")
                    }
                    previewImageRequestBuilder?.addTarget(previewSurface!!)
                    captureImageRequestBuilder?.addTarget(previewSurface!!)

                    previewImageRequestBuilder?.addTarget(previewDataSurface!!)
                    captureImageRequestBuilder?.addTarget(previewDataSurface!!)


                    val previewRequest = previewImageRequestBuilder?.build()
                    previewRequest?.let {
                        captureSession?.setRepeatingRequest(previewRequest, RepeatingCaptureStateCallback(), mainHandler)
                    }

                }
            }

            MSG_STOP_PREVIEW -> {
                Log.d(TAG, "Handle message: MSG_STOP_PREVIEW")
                val captureSession = captureSessionFuture?.get()
                captureSession?.stopRepeating()
            }
            MSG_SET_PREVIEW_SIZE -> {
                Log.d(TAG, "Handle message: msg set image size")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                cameraCharacteristics?.let {
                    // get the optimal preview size according to the specified max width and max height
                    val maxWidth = msg.arg1
                    val maxHeight = msg.arg2
                    val previewSize = getOptimalSize(cameraCharacteristics, SurfaceTexture::class.java, maxWidth, maxHeight)

                    // set the surfaceTexture`s buffer size with preview size.
                    val surfaceTexture = previewSurfaceTextureFuture?.get()
                    surfaceTexture?.let {
                        previewSize?.let {
                            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                            previewSurface = Surface(surfaceTexture)
                        }
                    }
                    val imageFormat = ImageFormat.YUV_420_888
                    val streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    if(streamConfigurationMap?.isOutputSupportedFor(imageFormat) == true) {
                        previewSize?.let {
                            previewDataImageReader = ImageReader.newInstance(previewSize.width, previewSize.height, imageFormat, 3)
                            previewDataImageReader!!.setOnImageAvailableListener(OnPreviewDataAvailableListener(), cameraHandler)
                            previewDataSurface = previewDataImageReader!!.surface
                        }
                    }
                }
            }


            MSG_SET_IMAGE_SIZE -> {
                Log.d(TAG, "Handle message: msg set image size")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                val captureImageRequestBuilder = captureImageRequestBuilder

                if(cameraCharacteristics != null && captureImageRequestBuilder != null) {
                    // Create a JPEG ImageReader instance according to the image size.
                    val maxWidth = msg.arg1
                    val maxHeight = msg.arg2
                    val imageSize = getOptimalSize(cameraCharacteristics, ImageReader::class.java, maxWidth, maxHeight)!!

                    jpegImageReader = ImageReader.newInstance(imageSize.width, imageSize.height, ImageFormat.JPEG, 5)
                    jpegImageReader?.setOnImageAvailableListener(OnJpegImageAvailableListener(), cameraHandler)
                    jpegSurface = jpegImageReader?.surface
                    // Configure the thumbnail size if any suitable size found, no thumbnail will be generated if the thumbnail size is null.
                    val availableThumbnailSizes = cameraCharacteristics[CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES]
                    val thumbnailSize = getOptimalSize(availableThumbnailSizes, maxWidth, maxHeight)
                    captureImageRequestBuilder[CaptureRequest.JPEG_THUMBNAIL_SIZE] = thumbnailSize
                }
            }


            MSG_CAPTURE_IMAGE -> {
                Log.d(TAG, "Handle message: MSG_CAPTURE_IMAGE")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                val captureSession = captureSessionFuture?.get()
                if(captureSession != null && captureImageRequestBuilder != null && jpegSurface != null && cameraCharacteristics != null) {
                    val deviceOrientation = deviceOrientationListener.orientation
                    val jpegOrientation = getJpegOrientation(cameraCharacteristics, deviceOrientation)
                    // configure the jpeg orientation according to the device orientation.
                    captureImageRequestBuilder!![CaptureRequest.JPEG_ORIENTATION] = jpegOrientation

                    // configure the location information
                    captureImageRequestBuilder!![CaptureRequest.JPEG_GPS_LOCATION] = getLocation()

                    // configure the image quality
                    captureImageRequestBuilder!![CaptureRequest.JPEG_QUALITY] = 100
                    // add target surface to receive the jpeg image data
                    captureImageRequestBuilder!!.addTarget(jpegSurface!!)
                    val captureImageRequest = captureImageRequestBuilder!!.build()
                    captureSession.capture(captureImageRequest, CaptureImageStateCallback(), mainHandler)

                }


            }

            MSG_CAPTURE_IMAGE_BURST -> {
                Log.d(TAG, "Handle message: MSG_CAPTURE_IMAGE_BURST: 连拍")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                val burstNumber = msg.arg1
                val captureSession = captureSessionFuture?.get()
                if(captureSession != null && captureImageRequestBuilder != null && jpegSurface != null && cameraCharacteristics != null) {
                    val deviceOrientation = deviceOrientationListener.orientation
                    val jpegOrientation = getJpegOrientation(cameraCharacteristics, deviceOrientation)
                    captureImageRequestBuilder!![CaptureRequest.JPEG_ORIENTATION] = jpegOrientation
                    captureImageRequestBuilder!![CaptureRequest.JPEG_GPS_LOCATION] = getLocation()

                    // Configure the image quality.
                    captureImageRequestBuilder!![CaptureRequest.JPEG_QUALITY] = 100

                    // Add the target surface to receive the jpeg image data.
                    captureImageRequestBuilder!!.addTarget(jpegSurface!!)

                    // Use the burst mode to capture images sequentially.
                    val captureImageRequest = captureImageRequestBuilder?.build()
                    val captureImageRequests = mutableListOf<CaptureRequest>()

                    for (i in 1..burstNumber) {
                        captureImageRequest?.let {
                            captureImageRequests.add(captureImageRequest)
                        }
                    }
                    captureSession.captureBurst(captureImageRequests, CaptureImageStateCallback(), mainHandler)

                }

            }
            MSG_START_CAPTURE_IMAGE_CONTINUOUSLY -> {
                Log.d(TAG, "Handle message: MSG_START_CAPTURE_IMAGE_CONTINUOUSLY")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                val captureSession = captureSessionFuture?.get()
                if(captureSession != null && cameraCharacteristics != null && captureImageRequestBuilder != null && jpegSurface != null) {
                    // Configure the jpeg orientation according to the device orientation.
                    val deviceOrientation = deviceOrientationListener.orientation
                    val jpegOrientation = getJpegOrientation(cameraCharacteristics, deviceOrientation)
                    captureImageRequestBuilder!![CaptureRequest.JPEG_ORIENTATION] = jpegOrientation

                    // Configure the location information.
                    val location = getLocation()
                    captureImageRequestBuilder!![CaptureRequest.JPEG_GPS_LOCATION] = location

                    // Configure the image quality.
                    captureImageRequestBuilder!![CaptureRequest.JPEG_QUALITY] = 100

                    // Add the target surface to receive the jpeg image data.

                    captureImageRequestBuilder!!.addTarget(jpegSurface!!)

                    // Use the repeating mode to capture image continuously.
                    val captureImageRequest = captureImageRequestBuilder!!.build()
                    captureSession.setRepeatingRequest(captureImageRequest, CaptureImageStateCallback(), mainHandler)

                }
            }
            else -> {
                Log.e(TAG, "handleMessage: case : ${msg.what} 不在处理范围之内啊.", )
            }

        }

        return false
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startCameraThread()

        val cameraIdList = cameraManager.cameraIdList

        cameraIdList.forEach { cameraId ->
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            if(cameraCharacteristics.isHardwareLevelSupported(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)) {
                if(cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = cameraId
                    frontCameraCharacteristics = cameraCharacteristics
                } else if(cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = cameraId
                    backCameraCharacteristics = cameraCharacteristics
                }
            }
        }
        previewSurfaceTextureFuture = SettableFuture()
        cameraPreview.surfaceTextureListener = PreviewSurfaceTextureListener()
        captureImageButton.setOnTouchListener(CaptureImageButtonListener(this))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.camera_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.switch_camera -> {
                switchCamera()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @MainThread
    private fun switchCamera() {
        val cameraDevice = cameraDeviceFuture?.get()
        val oloCameraId = cameraDevice?.id
        val newCameraId = if(oloCameraId == frontCameraId) backCameraId else frontCameraId
        if(newCameraId != null) {
            closeCamera()
            openCamera(newCameraId)
            createCaptureRequestBuilder()
            setPreviewSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
            setImageSize(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            createSession()
            startPreview()
        }
    }

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraThread")
        cameraThread?.start()
        cameraHandler = Handler(cameraThread!!.looper, this)
    }


    /**
     * core 流程
     */
    override fun onResume() {
        super.onResume()
        deviceOrientationListener.enable()
        if(checkRequiredPermissions()) {
            Log.d(TAG, "onResume: checkRequiredPermissions ok.")
            val cameraId = backCameraId ?: frontCameraId
            openCamera(cameraId)
            createCaptureRequestBuilder()
            setPreviewSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
            setImageSize(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            createSession()
            startPreview()
        }
    }

    private fun openCamera(cameraId: String?) {
        cameraHandler?.obtainMessage(MSG_OPEN_CAMERA, cameraId)?.sendToTarget()
    }

    private fun createCaptureRequestBuilder() {
        cameraHandler?.sendEmptyMessage(MSG_CREATE_REQUEST_BUILDERS)


    }

    private fun setPreviewSize(maxPreviewWidth: Int, maxPreviewHeight: Int) {
        cameraHandler?.obtainMessage(MSG_SET_PREVIEW_SIZE, maxPreviewWidth, maxPreviewHeight)
                ?.sendToTarget()

    }

    private fun setImageSize(maxImageWidth: Int, maxImageHeight: Int) {
        cameraHandler?.obtainMessage(MSG_SET_IMAGE_SIZE, maxImageWidth, maxImageHeight)
                ?.sendToTarget()

    }

    private fun createSession() {
        cameraHandler?.sendEmptyMessage(MSG_CREATE_SESSION)
    }

    private fun startPreview() {
        cameraHandler?.sendEmptyMessage(MSG_START_PREVIEW)

    }

    private fun closeCamera() {
        cameraHandler?.sendEmptyMessage(MSG_CLOSE_CAMERA)
    }

    private var previewSurfaceTextureFuture: SettableFuture<SurfaceTexture>? = null


    /**
     * YUV 数据监听
     */
    private inner class OnPreviewDataAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            val image = reader?.acquireNextImage()
            image?.use {
                val plane = image.planes
                val yPlane = plane[0]
                val uPlane = plane[1]
                val vPlane = plane[2]
                val yBuffer = yPlane.buffer
                val uBuffer = uPlane.buffer
                val vBuffer = vPlane.buffer

            }


        }

    }

    private val mediaActionSound: MediaActionSound = MediaActionSound()
    private val captureResults: BlockingQueue<CaptureResult> = LinkedBlockingDeque()

    private inner class CaptureImageStateCallback : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest,
                timestamp: Long, frameNumber: Long) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            cameraHandler?.post {
                mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
            }
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest,
                result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            captureResults.put(result)
        }
    }

    private inner class DeviceOrientationListener(ctx: Context) : OrientationEventListener(ctx) {
        var orientation: Int = 0
        override fun onOrientationChanged(orientation: Int) {
            this.orientation = orientation
        }
    }

    private val saveImageExecutor: Executor = Executors.newSingleThreadExecutor()

    private inner class OnJpegImageAvailableListener : ImageReader.OnImageAvailableListener {
        private val dateFormat: DateFormat = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault())
        private val cameraDir: String = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/Camera"

        @WorkerThread
        override fun onImageAvailable(imageReader: ImageReader?) {
            imageReader?.let {
                val image = imageReader.acquireNextImage()
                val captureResult = captureResults.take()
                if(image != null && captureResult != null) {
                    image.use {
                        // jpeg 的数据只在 planes[0]中
                        val jpegByteBuffer = it.planes[0].buffer
                        val jpegByteArray = ByteArray(jpegByteBuffer.remaining())
                        // 把 jpegByteBuffer 的数据 copy到 jpegByteArray
                        jpegByteBuffer.get(jpegByteArray)
                        val width = it.width
                        val height = it.height
                        saveImageExecutor.execute {
                            val date = System.currentTimeMillis()
                            val title = "IMG_${dateFormat.format(date)}"// e.g. IMG_20190211100833786
                            val displayName = "$title.jpeg"// e.g. IMG_20190211100833786.jpeg
                            val path = "$cameraDir/$displayName"// e.g. /sdcard/DCIM/Camera/IMG_20190211100833786.jpeg
                            val orientation = captureResult.get(CaptureResult.JPEG_ORIENTATION)
                            val location = captureResult.get(CaptureResult.JPEG_GPS_LOCATION)
                            val longitude = location?.longitude ?: 0.0
                            val latitude = location?.latitude ?: 0.0


                            Log.d(TAG, "onImageAvailable:  path is : $path")


                            // write the jpeg data into the specified file.
                            File(path).writeBytes(jpegByteArray)
                            // Insert the image information into the media store.
                            // 说的直白一点，就是让拍摄的图片在相册里能快速显示出来
                            val values = ContentValues()
                            values.put(MediaStore.Images.ImageColumns.TITLE, title)
                            values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, displayName)
                            values.put(MediaStore.Images.ImageColumns.DATA, path)
                            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, date)
                            values.put(MediaStore.Images.ImageColumns.WIDTH, width)
                            values.put(MediaStore.Images.ImageColumns.HEIGHT, height)
                            values.put(MediaStore.Images.ImageColumns.ORIENTATION, orientation)
                            values.put(MediaStore.Images.ImageColumns.LONGITUDE, longitude)
                            values.put(MediaStore.Images.ImageColumns.LATITUDE, latitude)
                            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            // Refresh the thumbnail of image.
                            val thumbnail = getThumbnail(path)
                            if(thumbnail != null) {
                                runOnUiThread {
                                    thumbnailView.setImageBitmap(thumbnail)
                                    thumbnailView.scaleX = 0.8F
                                    thumbnailView.scaleY = 0.8F
                                    // 动画show 一下；
                                    thumbnailView.animate().setDuration(50).scaleX(1.0F)
                                            .scaleY(1.0F).start()
                                }
                            }
                        }

                    }

                }

            }
        }
    }


    /**
     * 获取最优的预览size
     */
    @WorkerThread
    private fun getOptimalSize(cameraCharacteristics: CameraCharacteristics, clazz: Class<*>,
            maxWidth: Int, maxHeight: Int): Size? {
        val streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val supportedSizes = streamConfigurationMap?.getOutputSizes(clazz)
        return getOptimalSize(supportedSizes, maxWidth, maxHeight)

    }

    private fun getOptimalSize(supportedSizes: Array<Size>?, maxWidth: Int, maxHeight: Int): Size? {
        // 宽高比。
        val aspectRatio = maxWidth.toFloat() / maxHeight
        supportedSizes?.let {
            supportedSizes.forEach {
                if(it.width.toFloat() / it.height == aspectRatio && it.height <= maxHeight && it.width <= maxWidth) {
                    return it
                }
            }
        }
        return null
    }

    private fun getDisplayRotation(cameraCharacteristics: CameraCharacteristics): Int {
        val degrees = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        // 如果是后置摄像头
        if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            return (360 - (sensorOrientation + degrees) % 360) % 360
        }
        return (sensorOrientation - degrees + 360) % 360
    }

    private fun getJpegOrientation(cameraCharacteristics: CameraCharacteristics,
            deviceOrientation: Int): Int {
        var myDeviceOrientation = deviceOrientation
        if(myDeviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0
        }
        val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        myDeviceOrientation = (myDeviceOrientation + 45) / 90 * 90

        val facingFront = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        if(facingFront) {
            myDeviceOrientation -= myDeviceOrientation
        }
        return (sensorOrientation + myDeviceOrientation + 360) % 360
    }


    /**
     * Returns a thumbnail bitmap from the specified jpeg file.
     */
    @WorkerThread
    private fun getThumbnail(jpegPath: String): Bitmap? {
        // 创建 ExifInterface
        val exifInterface = ExifInterface(jpegPath)
        val orientation = when (exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_NORMAL -> 0.0F
            ExifInterface.ORIENTATION_ROTATE_90 -> 90.0F
            ExifInterface.ORIENTATION_ROTATE_180 -> 180.0F
            ExifInterface.ORIENTATION_ROTATE_270 -> 270.0F
            else -> 0.0F
        }

        var thumbnail = if(exifInterface.hasThumbnail() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            exifInterface.thumbnailBitmap
        } else {
            val options = BitmapFactory.Options()
            options.inSampleSize = 16
            BitmapFactory.decodeFile(jpegPath, options)
        }

        if(orientation != 0.0F && thumbnail != null) {
            val matrix = Matrix()
            matrix.setRotate(orientation)
            thumbnail = Bitmap.createBitmap(thumbnail, 0, 0, thumbnail.width, thumbnail.height, matrix, true)
        }

        return thumbnail
    }

    @WorkerThread
    private fun getLocation(): Location? {
        val locationManager = getSystemService(LocationManager::class.java)
        if(locationManager != null && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }
        return null
    }


    /**
     * Checks if all required permissions are granted and requests denied permissions.
     *
     * @return Returns true if all required permissions are granted.
     */
    private fun checkRequiredPermissions(): Boolean {
        val deniedPermissions = mutableListOf<String>()
        for (permission in REQUIRED_PERMISSIONS) {
            if(ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(permission)
            }
        }
        if(deniedPermissions.isEmpty().not()) {
            requestPermissions(deniedPermissions.toTypedArray(), REQUEST_PERMISSION_CODE)
        }
        return deniedPermissions.isEmpty()
    }

    /**
     * 获取相机属性： 前置摄像头和后置摄像头 的属性类
     */
    private fun getCameraCharacteristics(cameraId: String): CameraCharacteristics? {
        return when (cameraId) {
            frontCameraId -> frontCameraCharacteristics
            backCameraId -> backCameraCharacteristics
            else -> null
        }
    }

    private inner class CameraStateCallback : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDeviceFuture?.set(camera)
            cameraCharacteristicsFuture?.set(getCameraCharacteristics(camera.id))

        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDeviceFuture?.set(camera)
            closeCamera()

        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDeviceFuture?.set(camera)
            closeCamera()

        }

    }

    private inner class SessionStateCallback : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSessionFuture?.set(session)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            captureSessionFuture?.set(session)
        }

        override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            captureSessionFuture?.set(session)
        }

    }

    private inner class PreviewSurfaceTextureListener : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            previewSurfaceTextureFuture?.set(surface)

        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {

        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false

        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

        }

    }

    private inner class RepeatingCaptureStateCallback : CameraCaptureSession.CaptureCallback() {
        @MainThread
        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest,
                timestamp: Long, frameNumber: Long) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
        }

        @MainThread
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest,
                result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
        }
    }


    private inner class CaptureImageButtonListener(context: Context) :
            GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
        private val gestureDetector: GestureDetector = GestureDetector(context, this)
        private var isLongPress: Boolean = false


        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if(event?.actionMasked == MotionEvent.ACTION_UP && isLongPress) {
                onLongPressUp()
            }
            return gestureDetector.onTouchEvent(event)
        }


        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            captureImage()
            return true
        }

        override fun onLongPress(e: MotionEvent?) {
            isLongPress = true
            startCaptureImageContinuously()
        }

        private fun onLongPressUp() {
            isLongPress = false
            stopCaptureImageContinuously()
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            captureImageBurst(10)
            return true
        }
    }

    private fun startCaptureImageContinuously() {
        cameraHandler?.sendEmptyMessage(MSG_START_CAPTURE_IMAGE_CONTINUOUSLY)
    }

    private fun stopCaptureImageContinuously() {
        startPreview()
    }

    private fun captureImage() {
        cameraHandler?.sendEmptyMessage(MSG_CAPTURE_IMAGE)
    }

    private fun captureImageBurst(burstNumber: Int) {
        cameraHandler?.obtainMessage(MSG_CAPTURE_IMAGE_BURST, burstNumber, 0)?.sendToTarget()
    }

}