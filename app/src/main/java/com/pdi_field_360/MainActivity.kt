package com.pdi_field_360

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import com.arashivision.sdk.camera.InstaCameraSDK
import com.arashivision.sdk.camera.api.CameraDevice
import com.arashivision.sdk.camera.api.preview.CameraStreamListener
import com.arashivision.sdk.camera.api.preview.PreviewStreamParamsUpdate
import com.arashivision.sdk.camera.core.model.ConnectType
import com.arashivision.sdk.camera.core.model.FunctionMode

import com.arashivision.sdk.common.exception.InstaException

import com.arashivision.sdk.media.api.listener.PlayerViewListener
import com.arashivision.sdk.media.api.params.PreviewParams
import com.arashivision.sdk.media.player.preview.InstaCapturePlayerView

import com.pdi_field_360.ui.theme.PDIField360Theme

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.arashivision.sdk.media.InstaMediaSDK



class MainActivity : ComponentActivity() {

    // ============================================================
    // CÁMARA
    // ============================================================

    private var cameraDevice: CameraDevice? = null


    // ============================================================
    // ESTADOS
    // ============================================================

    private var cameraConnected by mutableStateOf(false)

    private var cameraStatus by mutableStateOf(
        "Cámara desconectada"
    )

    private var captureStatus by mutableStateOf(
        "Esperando conexión..."
    )


    // ============================================================
    // VIDEO
    // ============================================================

    private var isRecording by mutableStateOf(false)

    private var recordingSeconds by mutableIntStateOf(0)

    private var recordingJob: Job? = null


    // ============================================================
    // LIVE VIEW
    // ============================================================

    private var previewPlayerView: InstaCapturePlayerView? = null

    private var previewStarted by mutableStateOf(false)

    private var previewFirstFrame by mutableStateOf(false)

    private var previewWidth = 1280

    private var previewHeight = 960

    private var previewFps = 30


    // ============================================================
    // PERMISOS
    // ============================================================

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            // Los permisos se verifican al iniciar.
        }


    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        // --------------------------------------------------------
        // Inicializar SDK Insta360
        // --------------------------------------------------------

        InstaCameraSDK.init(application) {

            cacheDir =
                externalCacheDir?.absolutePath
        }

        // Media SDK
        // Necesario para InstaCapturePlayerView y renderizado 360°
        InstaMediaSDK.init(application)
        // --------------------------------------------------------
        // Solicitar permisos
        // --------------------------------------------------------

        requestRequiredPermissions()


        // --------------------------------------------------------
        // Interfaz
        // --------------------------------------------------------

        setContent {

            PDIField360Theme {

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {

                    PDIField360Screen()
                }
            }
        }
    }


    // ============================================================
    // PERMISOS
    // ============================================================

    private fun requestRequiredPermissions() {

        val permissions = mutableListOf(

            Manifest.permission.ACCESS_FINE_LOCATION,

            Manifest.permission.ACCESS_COARSE_LOCATION
        )


        if (android.os.Build.VERSION.SDK_INT >= 31) {

            permissions.add(
                Manifest.permission.BLUETOOTH_SCAN
            )

            permissions.add(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }


        if (android.os.Build.VERSION.SDK_INT >= 33) {

            permissions.add(
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        }


        val missingPermissions =

            permissions.filter {

                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }


        if (missingPermissions.isNotEmpty()) {

            permissionLauncher.launch(
                missingPermissions.toTypedArray()
            )
        }
    }


    // ============================================================
    // OBTENER NETWORK ID DEL WIFI
    // ============================================================

    private fun getWifiNetworkId(): Long {

        val connectivityManager =

            getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager


        val networks =
            connectivityManager.allNetworks


        for (network in networks) {

            val linkProperties =

                connectivityManager.getLinkProperties(
                    network
                )


            if (
                linkProperties?.interfaceName == "wlan0"
            ) {

                return network.networkHandle
            }
        }


        return 0L
    }


    // ============================================================
    // CONECTAR INSTA360 X3
    // ============================================================

    private fun connectCamera() {

        lifecycleScope.launch {

            try {

                cameraStatus =
                    "Conectando con Insta360 X3..."

                captureStatus =
                    "Estableciendo comunicación..."


                val networkId =
                    getWifiNetworkId()


                if (networkId == 0L) {

                    cameraStatus =
                        "No se encontró conexión Wi-Fi"

                    captureStatus =
                        "Conecta el teléfono al Wi-Fi de la X3"

                    return@launch
                }


                val camera =

                    CameraDevice.get(
                        ConnectType.WIFI
                    )


                cameraDevice =
                    camera


                camera.connect(networkId)

                    .onSuccess {

                        cameraConnected = true

                        cameraStatus =
                            "Insta360 X3 conectada"

                        captureStatus =
                            "Cámara lista"


                        // =================================================
                        // PRUEBA 2 DEL LIVE VIEW
                        //
                        // Ya comprobamos:
                        //
                        // 1. Conexión X3          -> OK
                        // 2. preview.init()       -> OK
                        //
                        // Ahora comprobamos:
                        //
                        // 3. registerCameraStreamListener()
                        //
                        // TODAVÍA NO EJECUTAMOS startStream()
                        // =================================================

                        try {

                            captureStatus =
                                "PRUEBA 2: inicializando preview..."


                            // ---------------------------------------------
                            // Inicializar módulo Preview
                            // ---------------------------------------------

                            camera.preview.init(
                                application
                            )


                            captureStatus =
                                "PRUEBA 2: registrando listener..."


                            // ---------------------------------------------
                            // Registrar listener
                            // ---------------------------------------------

                            camera.preview
                                .registerCameraStreamListener(
                                    cameraStreamListener
                                )


                            captureStatus =
                                "PRUEBA 2 OK: listener registrado"


                            captureStatus =
                                "PRUEBA 3: iniciando stream..."

                            previewStarted = true

                            camera.preview.startStream()

                            captureStatus =
                                "PRUEBA 3: startStream() ejecutado"


                        } catch (e: Exception) {

                            captureStatus =
                                "ERROR PRUEBA 2: ${e.message}"
                        }
                    }

                    .onFailure { error ->

                        cameraConnected = false

                        cameraStatus =
                            "Error de conexión"

                        captureStatus =
                            error.message
                                ?: "No fue posible conectar con la cámara"
                    }


            } catch (e: Exception) {

                cameraConnected = false

                cameraStatus =
                    "Error de conexión"

                captureStatus =
                    e.message
                        ?: "Error desconocido"
            }
        }
    }


    // ============================================================
    // DESCONECTAR
    // ============================================================

    private fun disconnectCamera() {

        lifecycleScope.launch {

            try {

                // ------------------------------------------------
                // Si está grabando detener primero
                // ------------------------------------------------

                if (isRecording) {

                    try {

                        cameraDevice
                            ?.capture
                            ?.stopCapture()

                    } catch (_: Exception) {
                    }


                    stopRecordingTimer()

                    isRecording = false
                }


                // ------------------------------------------------
                // Liberar listener de Preview
                // ------------------------------------------------

                try {

                    cameraDevice
                        ?.preview
                        ?.unregisterCameraStreamListener(
                            cameraStreamListener
                        )

                } catch (_: Exception) {
                }


                // ------------------------------------------------
                // Detener Live View si estuviera activo
                // ------------------------------------------------

                if (previewStarted) {

                    stopLiveView()
                }


                // ------------------------------------------------
                // Liberar cámara
                // ------------------------------------------------

                cameraDevice?.release()

                cameraDevice = null


                cameraConnected = false

                cameraStatus =
                    "Cámara desconectada"

                captureStatus =
                    "Esperando conexión..."


            } catch (e: Exception) {

                captureStatus =
                    "Error al desconectar: ${e.message}"
            }
        }
    }


    // ============================================================
    // CAMERA STREAM LISTENER
    // ============================================================

    private val cameraStreamListener =

        object : CameraStreamListener {


            // ----------------------------------------------------
            // Stream comenzando a abrir
            // ----------------------------------------------------

            override fun onOpening() {

                runOnUiThread {

                    captureStatus =
                        "Abriendo Live View..."
                }
            }


            // ----------------------------------------------------
            // Stream abierto
            // ----------------------------------------------------

            override fun onOpened() {

                val player =
                    previewPlayerView
                        ?: return


                val camera =
                    cameraDevice
                        ?: return


                runOnUiThread {

                    captureStatus =
                        "Preparando imagen 360°..."
                }


                camera.preview
                    .requestStreamIframe()


                player.post {

                    if (!previewStarted) {

                        return@post
                    }


                    player.destroyRender()


                    prepareAndPlayPreview(
                        player
                    )
                }
            }


            // ----------------------------------------------------
            // Stream detenido
            // ----------------------------------------------------

            override fun onIdle() {

                runOnUiThread {

                    if (
                        cameraConnected &&
                        previewStarted
                    ) {

                        captureStatus =
                            "Live View detenido"
                    }
                }
            }


            // ----------------------------------------------------
            // Parámetros del stream
            // ----------------------------------------------------

            override fun onParamsChanged(
                paramsUpdate: PreviewStreamParamsUpdate
            ) {

                val player =
                    previewPlayerView
                        ?: return


                if (
                    paramsUpdate.previewWidth > 0 &&
                    paramsUpdate.previewHeight > 0 &&
                    paramsUpdate.previewFps > 0
                ) {

                    val resolutionChanged =

                        previewWidth !=
                                paramsUpdate.previewWidth ||

                                previewHeight !=
                                paramsUpdate.previewHeight


                    val fpsChanged =

                        previewFps !=
                                paramsUpdate.previewFps


                    previewWidth =
                        paramsUpdate.previewWidth

                    previewHeight =
                        paramsUpdate.previewHeight

                    previewFps =
                        paramsUpdate.previewFps


                    if (resolutionChanged) {

                        player.setPreviewResolution(
                            previewWidth,
                            previewHeight
                        )
                    }


                    if (fpsChanged) {

                        player.setFps(
                            previewFps
                        )
                    }
                }
            }
        }


    // ============================================================
    // PREPARAR PLAYER
    // ============================================================

    private fun prepareAndPlayPreview(
        player: InstaCapturePlayerView
    ) {

        try {

            player.setListener(
                playerViewListener
            )


            player.prepare(

                PreviewParams(

                    width =
                        previewWidth,

                    height =
                        previewHeight,

                    fps =
                        previewFps,

                    isGestureEnabled =
                        true
                )
            )


            player.play()


        } catch (e: Exception) {

            captureStatus =
                "Error preparando Live View: ${e.message}"
        }
    }


    // ============================================================
    // PLAYER VIEW LISTENER
    // ============================================================

    private val playerViewListener =

        object : PlayerViewListener {


            // ----------------------------------------------------
            // Estado de carga
            // ----------------------------------------------------

            override fun onLoadingStatusChanged(
                isLoading: Boolean
            ) {

                runOnUiThread {

                    if (isLoading) {

                        captureStatus =
                            "Cargando Live View..."
                    }
                }
            }


            // ----------------------------------------------------
            // Player preparado
            // ----------------------------------------------------

            override fun onLoadingFinish() {

                val camera =
                    cameraDevice
                        ?: return


                val player =
                    previewPlayerView
                        ?: return


                val pipeline =
                    player.getPipeline()


                if (pipeline == null) {

                    runOnUiThread {

                        captureStatus =
                            "Pipeline de video no disponible"
                    }

                    return
                }


                // ------------------------------------------------
                // Unir Media SDK con Camera SDK
                // ------------------------------------------------

                camera.preview
                    .setPipeline(
                        pipeline
                    )


                camera.preview
                    .requestStreamIframe()


                runOnUiThread {

                    captureStatus =
                        "Esperando imagen 360°..."
                }
            }


            // ----------------------------------------------------
            // Error
            // ----------------------------------------------------

            override fun onFail(
                exception: InstaException
            ) {

                runOnUiThread {

                    previewFirstFrame = false

                    captureStatus =
                        "Error Live View: ${exception.message}"
                }
            }


            // ----------------------------------------------------
            // Primer frame
            // ----------------------------------------------------

            override fun onFirstFrameRendered() {

                runOnUiThread {

                    previewFirstFrame = true

                    captureStatus =
                        "LIVE VIEW 360°"
                }
            }


            // ----------------------------------------------------
            // Liberar pipeline
            // ----------------------------------------------------

            override fun onReleaseCameraPipeline() {

                cameraDevice
                    ?.preview
                    ?.setPipeline(null)
            }
        }


    // ============================================================
    // INICIAR LIVE VIEW
    //
    // IMPORTANTE:
    // ESTA FUNCIÓN TODAVÍA NO SE LLAMA AUTOMÁTICAMENTE.
    //
    // Primero estamos verificando PRUEBA 2.
    // ============================================================

    private fun startLiveView() {

        val camera =
            cameraDevice
                ?: return


        if (previewStarted) {

            return
        }


        try {

            captureStatus =
                "Iniciando Live View..."

            previewFirstFrame =
                false


            camera.preview.init(
                application
            )


            camera.preview
                .registerCameraStreamListener(
                    cameraStreamListener
                )


            previewStarted =
                true


            camera.preview
                .startStream()


        } catch (e: Exception) {

            previewStarted =
                false

            previewFirstFrame =
                false


            captureStatus =
                "Error Live View: ${e.message}"
        }
    }


    // ============================================================
    // DETENER LIVE VIEW
    // ============================================================

    private fun stopLiveView() {

        val camera =
            cameraDevice


        try {

            if (
                camera != null &&
                previewStarted
            ) {

                camera.preview
                    .unregisterCameraStreamListener(
                        cameraStreamListener
                    )


                camera.preview
                    .setPipeline(null)


                camera.preview
                    .stopStream()
            }


        } catch (_: Exception) {
        }


        previewStarted =
            false

        previewFirstFrame =
            false


        try {

            previewPlayerView
                ?.setListener(null)


            previewPlayerView
                ?.destroy()


        } catch (_: Exception) {
        }


        previewPlayerView =
            null
    }


    // ============================================================
    // TOMAR FOTO 360
    // ============================================================

    private fun capturePhoto() {

        val camera =
            cameraDevice


        if (
            camera == null ||
            !cameraConnected
        ) {

            captureStatus =
                "Conecta primero la Insta360 X3"

            return
        }


        if (isRecording) {

            captureStatus =
                "Detén la grabación antes de tomar una foto"

            return
        }


        lifecycleScope.launch {

            try {

                captureStatus =
                    "Preparando modo fotografía..."


                val capture =
                    camera.capture


                val modeResult =

                    capture
                        .functionMode
                        .setValue(
                            FunctionMode.PHOTO_NORMAL
                        )


                if (modeResult.isFailure) {

                    captureStatus =
                        "No fue posible activar modo foto"

                    return@launch
                }


                delay(300)


                captureStatus =
                    "Capturando fotografía 360°..."


                capture.startCapture()


                captureStatus =
                    "Fotografía 360° capturada"


            } catch (e: Exception) {

                captureStatus =
                    "Error fotografía: ${e.message}"
            }
        }
    }


    // ============================================================
    // INICIAR VIDEO
    // ============================================================

    private fun startVideo() {

        val camera =
            cameraDevice


        if (
            camera == null ||
            !cameraConnected
        ) {

            captureStatus =
                "Conecta primero la Insta360 X3"

            return
        }


        if (isRecording) {

            return
        }


        lifecycleScope.launch {

            try {

                captureStatus =
                    "Preparando modo video..."


                val capture =
                    camera.capture


                val modeResult =

                    capture
                        .functionMode
                        .setValue(
                            FunctionMode.VIDEO_NORMAL
                        )


                if (modeResult.isFailure) {

                    captureStatus =
                        "No fue posible activar modo video"

                    return@launch
                }


                delay(300)


                captureStatus =
                    "Iniciando grabación..."


                capture.startCapture()


                isRecording =
                    true

                recordingSeconds =
                    0


                captureStatus =
                    "Grabando video 360°"


                startRecordingTimer()


            } catch (e: Exception) {

                isRecording =
                    false

                captureStatus =
                    "Error video: ${e.message}"
            }
        }
    }


    // ============================================================
    // DETENER VIDEO
    // ============================================================

    private fun stopVideo() {

        val camera =
            cameraDevice


        if (
            camera == null ||
            !isRecording
        ) {

            return
        }


        lifecycleScope.launch {

            try {

                captureStatus =
                    "Deteniendo grabación..."


                // ------------------------------------------------
                // SDK 2.1.5
                // stopCapture() se ejecuta directamente.
                // ------------------------------------------------

                camera.capture
                    .stopCapture()


                stopRecordingTimer()


                isRecording =
                    false


                captureStatus =
                    "Video guardado en Insta360 X3"


            } catch (e: Exception) {

                captureStatus =
                    "Error deteniendo video: ${e.message}"
            }
        }
    }


    // ============================================================
    // TIMER
    // ============================================================

    private fun startRecordingTimer() {

        recordingJob?.cancel()


        recordingJob =

            lifecycleScope.launch {

                while (
                    isActive &&
                    isRecording
                ) {

                    delay(1000)

                    recordingSeconds++
                }
            }
    }


    private fun stopRecordingTimer() {

        recordingJob?.cancel()

        recordingJob =
            null
    }


    // ============================================================
    // FORMATO TIEMPO
    // ============================================================

    private fun formatRecordingTime(
        seconds: Int
    ): String {

        val hours =
            seconds / 3600


        val minutes =
            (seconds % 3600) / 60


        val secs =
            seconds % 60


        return String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            secs
        )
    }


    // ============================================================
    // INTERFAZ
    // ============================================================

    @Composable
    private fun PDIField360Screen() {

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {


            // ====================================================
            // TÍTULO
            // ====================================================

            Text(

                text =
                    "PDI FIELD 360",

                fontSize =
                    28.sp
            )


            Text(

                text =
                    "Captura e Inspección Industrial 360°",

                fontSize =
                    14.sp,

                color =
                    Color.Gray
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            // ====================================================
            // CÁMARA
            // ====================================================

            Text(

                text =
                    "Insta360 X3",

                fontSize =
                    18.sp
            )


            Text(

                text =
                    cameraStatus,

                color =

                    if (cameraConnected)

                        Color(0xFF2E7D32)

                    else

                        Color.Gray
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            // ====================================================
            // LIVE VIEW
            // ====================================================

            Box(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(
                            Color.Black,
                            RoundedCornerShape(12.dp)
                        ),

                contentAlignment =
                    Alignment.Center
            ) {


                if (cameraConnected) {


                    // ------------------------------------------------
                    // Player Insta360
                    // ------------------------------------------------

                    AndroidView(

                        modifier =
                            Modifier.fillMaxSize(),

                        factory = { ctx ->


                            InstaCapturePlayerView(
                                ctx
                            ).also { player ->


                                previewPlayerView =
                                    player


                                player.setLifecycle(
                                    this@MainActivity.lifecycle
                                )


                                player.setListener(
                                    playerViewListener
                                )
                            }
                        },

                        update = { player ->

                            previewPlayerView =
                                player
                        }
                    )


                    // ------------------------------------------------
                    // Pantalla de estado
                    // ------------------------------------------------

                    if (!previewFirstFrame) {

                        Box(

                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Black.copy(
                                            alpha = 0.65f
                                        )
                                    ),

                            contentAlignment =
                                Alignment.Center
                        ) {

                            Column(

                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {


                                CircularProgressIndicator()


                                Spacer(
                                    modifier =
                                        Modifier.height(12.dp)
                                )


                                Text(

                                    text =
                                        captureStatus,

                                    color =
                                        Color.White
                                )
                            }
                        }
                    }


                    // ------------------------------------------------
                    // LIVE
                    // ------------------------------------------------

                    if (previewFirstFrame) {

                        Text(

                            text =
                                "● LIVE 360°",

                            color =
                                Color.Red,

                            modifier =
                                Modifier
                                    .align(
                                        Alignment.TopStart
                                    )
                                    .padding(12.dp)
                        )
                    }


                    // ------------------------------------------------
                    // REC
                    // ------------------------------------------------

                    if (isRecording) {

                        Text(

                            text =
                                "● REC ${
                                    formatRecordingTime(
                                        recordingSeconds
                                    )
                                }",

                            color =
                                Color.Red,

                            modifier =
                                Modifier
                                    .align(
                                        Alignment.TopEnd
                                    )
                                    .padding(12.dp)
                        )
                    }


                } else {


                    Column(

                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {


                        Text(

                            text =
                                "LIVE VIEW 360°",

                            color =
                                Color.White,

                            fontSize =
                                18.sp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )


                        Text(

                            text =
                                "Conecta la Insta360 X3",

                            color =
                                Color.LightGray
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            // ====================================================
            // ESTADO
            // ====================================================

            Text(

                text =
                    captureStatus,

                fontSize =
                    14.sp
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            // ====================================================
            // CONECTAR
            // ====================================================

            Button(

                onClick = {

                    if (cameraConnected) {

                        disconnectCamera()

                    } else {

                        connectCamera()
                    }
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {


                Text(

                    if (cameraConnected)

                        "DESCONECTAR INSTA360 X3"

                    else

                        "CONECTAR INSTA360 X3"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            // ====================================================
            // FOTO
            // ====================================================

            Button(

                onClick = {

                    capturePhoto()
                },

                enabled =
                    cameraConnected &&
                            !isRecording,

                modifier =
                    Modifier.fillMaxWidth()
            ) {


                Text(
                    "📷 TOMAR FOTO 360°"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            // ====================================================
            // VIDEO
            // ====================================================

            Button(

                onClick = {

                    if (isRecording) {

                        stopVideo()

                    } else {

                        startVideo()
                    }
                },

                enabled =
                    cameraConnected,

                modifier =
                    Modifier.fillMaxWidth()
            ) {


                Text(

                    if (isRecording)

                        "■ DETENER VIDEO"

                    else

                        "● INICIAR VIDEO"
                )
            }


            // ====================================================
            // TIEMPO GRABACIÓN
            // ====================================================

            if (isRecording) {

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                Text(

                    text =
                        "REC  ${
                            formatRecordingTime(
                                recordingSeconds
                            )
                        }",

                    color =
                        Color.Red,

                    fontSize =
                        18.sp
                )
            }


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            HorizontalDivider()


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            // ====================================================
            // INSPECCIÓN
            // ====================================================

            Text(

                text =
                    "INSPECCIÓN DE CAMPO",

                fontSize =
                    16.sp
            )


            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )


            Text(

                text =
                    "Proyecto • Planta • Área • Equipo • TAG",

                color =
                    Color.Gray,

                fontSize =
                    13.sp
            )
        }
    }


    // ============================================================
    // ON DESTROY
    // ============================================================

    override fun onDestroy() {

        stopRecordingTimer()


        try {

            if (previewStarted) {

                stopLiveView()
            }

        } catch (_: Exception) {
        }


        try {

            cameraDevice
                ?.preview
                ?.unregisterCameraStreamListener(
                    cameraStreamListener
                )

        } catch (_: Exception) {
        }


        try {

            cameraDevice?.release()

        } catch (_: Exception) {
        }


        cameraDevice =
            null


        super.onDestroy()
    }
}