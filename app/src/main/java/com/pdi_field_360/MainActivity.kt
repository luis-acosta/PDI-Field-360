package com.pdi_field_360

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import com.arashivision.sdk.camera.InstaCameraSDK
import com.arashivision.sdk.camera.api.CameraDevice
import com.arashivision.sdk.camera.core.model.ConnectType
import com.arashivision.sdk.camera.core.model.FunctionMode
import com.pdi_field_360.ui.theme.PDIField360Theme

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    // =========================================================================
    // INSTA360
    // =========================================================================

    private var cameraDevice: CameraDevice? = null

    // =========================================================================
    // ESTADOS DE LA INTERFAZ
    // =========================================================================

    private var cameraConnected by mutableStateOf(false)

    private var cameraStatus by mutableStateOf(
        "Cámara desconectada"
    )

    private var captureStatus by mutableStateOf(
        "Esperando conexión"
    )

    private var isRecording by mutableStateOf(false)

    private var recordingSeconds by mutableStateOf(0)

    private var recordingJob: Job? = null


    // =========================================================================
    // PERMISOS
    // =========================================================================

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            initializeInsta360()
        }


    // =========================================================================
    // ON CREATE
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {

            PDIField360Theme {

                PDIField360Screen(

                    cameraConnected = cameraConnected,

                    cameraStatus = cameraStatus,

                    captureStatus = captureStatus,

                    isRecording = isRecording,

                    recordingSeconds = recordingSeconds,

                    onConnect = {
                        connectCamera()
                    },

                    onDisconnect = {
                        disconnectCamera()
                    },

                    onPhoto = {
                        capturePhoto()
                    },

                    onStartVideo = {
                        startVideo()
                    },

                    onStopVideo = {
                        stopVideo()
                    }
                )
            }
        }
    }


    // =========================================================================
    // SOLICITUD DE PERMISOS
    // =========================================================================

    private fun requestPermissions() {

        val permissions = mutableListOf(

            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )


        // Android 13 o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            permissions.add(
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        }


        // Android 12 o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            permissions.add(
                Manifest.permission.BLUETOOTH_SCAN
            )

            permissions.add(
                Manifest.permission.BLUETOOTH_CONNECT
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

        } else {

            initializeInsta360()
        }
    }


    // =========================================================================
    // INICIALIZACIÓN DEL SDK INSTA360
    // =========================================================================

    private fun initializeInsta360() {

        try {

            InstaCameraSDK.init(application) {

                cacheDir =
                    externalCacheDir?.absolutePath
            }

            cameraStatus =
                "SDK Insta360 inicializado"

        } catch (e: Exception) {

            cameraStatus =
                "Error SDK: ${e.message}"
        }
    }


    // =========================================================================
    // OBTENER NETWORK ID DEL WIFI wlan0
    //
    // Este procedimiento está basado en el Demo oficial del SDK 2.1.5.
    // =========================================================================

    private fun getWifiNetworkId(): Long {

        return try {

            val connectivityManager =
                getSystemService(
                    Context.CONNECTIVITY_SERVICE
                ) as ConnectivityManager


            connectivityManager
                .allNetworks
                .firstOrNull { network ->

                    val capabilities =
                        connectivityManager
                            .getNetworkCapabilities(network)
                            ?: return@firstOrNull false


                    // Debe ser una conexión Wi-Fi
                    if (
                        !capabilities.hasTransport(
                            NetworkCapabilities.TRANSPORT_WIFI
                        )
                    ) {

                        return@firstOrNull false
                    }


                    val linkProperties: LinkProperties =
                        connectivityManager
                            .getLinkProperties(network)
                            ?: return@firstOrNull false


                    // Interfaz Wi-Fi principal del teléfono
                    linkProperties.interfaceName == "wlan0"
                }
                ?.networkHandle
                ?: -1L

        } catch (e: Exception) {

            -1L
        }
    }


    // =========================================================================
    // CONECTAR CON INSTA360 X3
    // =========================================================================

    private fun connectCamera() {

        if (cameraConnected) {
            return
        }


        cameraStatus =
            "Conectando con Insta360 X3..."


        lifecycleScope.launch {

            try {

                // Crear dispositivo Insta360 utilizando Wi-Fi
                val camera =
                    CameraDevice.get(
                        ConnectType.WIFI
                    )


                cameraDevice = camera


                // Obtener identificador de la red Wi-Fi
                val networkId =
                    getWifiNetworkId()


                if (networkId == -1L) {

                    cameraConnected = false

                    cameraStatus =
                        "No se detectó Wi-Fi"

                    captureStatus =
                        "Conecta primero el teléfono al Wi-Fi de la X3"

                    return@launch
                }


                // -------------------------------------------------------------
                // CONEXIÓN REAL CON LA CÁMARA
                // -------------------------------------------------------------

                camera
                    .connect(networkId)
                    .onSuccess {

                        cameraConnected = true

                        cameraStatus =
                            "Insta360 X3 conectada"

                        captureStatus =
                            "Cámara lista"
                    }
                    .onFailure { error ->

                        cameraConnected = false

                        cameraStatus =
                            "Error de conexión"

                        captureStatus =
                            error.message ?: "No fue posible conectar"
                    }


            } catch (e: Exception) {

                cameraConnected = false

                cameraStatus =
                    "Error de conexión"

                captureStatus =
                    e.message ?: "Error desconocido"
            }
        }
    }


    // =========================================================================
    // TOMAR FOTOGRAFÍA
    // =========================================================================

    private fun capturePhoto() {

        val camera = cameraDevice


        // Comprobar conexión
        if (
            camera == null ||
            !cameraConnected
        ) {

            captureStatus =
                "Cámara no conectada"

            return
        }


        // No permitimos fotografía mientras grabamos
        if (isRecording) {

            captureStatus =
                "Detén primero la grabación de video"

            return
        }


        lifecycleScope.launch {

            try {

                val capture =
                    camera.capture


                captureStatus =
                    "Configurando modo fotografía..."


                // -------------------------------------------------------------
                // IMPORTANTE
                //
                // Forzamos PHOTO_NORMAL.
                // Esto evita el problema anterior donde startCapture()
                // iniciaba video porque la X3 permanecía en modo VIDEO.
                // -------------------------------------------------------------

                val modeResult =
                    capture.functionMode
                        .setValue(
                            FunctionMode.PHOTO_NORMAL
                        )


                if (modeResult.isFailure) {

                    captureStatus =
                        "No fue posible activar modo fotografía"

                    return@launch
                }


                // Pequeña espera para permitir que la cámara
                // termine el cambio de modo.
                delay(300)


                captureStatus =
                    "Tomando fotografía..."


                // -------------------------------------------------------------
                // DISPARO REAL
                // -------------------------------------------------------------

                capture.startCapture()


                captureStatus =
                    "Fotografía capturada"


            } catch (e: Exception) {

                captureStatus =
                    "Error de fotografía: ${e.message}"
            }
        }
    }


    // =========================================================================
    // INICIAR GRABACIÓN DE VIDEO
    // =========================================================================

    private fun startVideo() {

        val camera = cameraDevice


        if (
            camera == null ||
            !cameraConnected
        ) {

            captureStatus =
                "Cámara no conectada"

            return
        }


        // Evitar dos órdenes de grabación
        if (isRecording) {

            return
        }


        lifecycleScope.launch {

            try {

                val capture =
                    camera.capture


                captureStatus =
                    "Configurando modo video..."


                // -------------------------------------------------------------
                // FORZAR VIDEO NORMAL
                // -------------------------------------------------------------

                val modeResult =
                    capture.functionMode
                        .setValue(
                            FunctionMode.VIDEO_NORMAL
                        )


                if (modeResult.isFailure) {

                    captureStatus =
                        "No fue posible activar modo video"

                    return@launch
                }


                // Esperamos que la X3 termine el cambio de modo
                delay(300)


                captureStatus =
                    "Iniciando grabación..."


                // -------------------------------------------------------------
                // INICIAR GRABACIÓN REAL
                // -------------------------------------------------------------

                capture.startCapture()


                // -------------------------------------------------------------
                // ACTUALIZAR ESTADO
                // -------------------------------------------------------------

                isRecording = true

                recordingSeconds = 0


                // Iniciar cronómetro
                startRecordingTimer()


            } catch (e: Exception) {

                isRecording = false

                captureStatus =
                    "Error de video: ${e.message}"
            }
        }
    }


    // =========================================================================
    // DETENER GRABACIÓN
    // =========================================================================

    private fun stopVideo() {

        val camera = cameraDevice


        if (
            camera == null ||
            !cameraConnected
        ) {

            captureStatus =
                "Cámara no conectada"

            return
        }


        if (!isRecording) {

            captureStatus =
                "No hay una grabación activa"

            return
        }


        lifecycleScope.launch {

            try {

                captureStatus =
                    "Deteniendo grabación..."


                // -------------------------------------------------------------
                // DETENER GRABACIÓN REAL EN LA X3
                // -------------------------------------------------------------

                camera.capture.stopCapture()


                // Detener cronómetro
                recordingJob?.cancel()

                recordingJob = null


                isRecording = false


                val minutes =
                    recordingSeconds / 60

                val seconds =
                    recordingSeconds % 60


                captureStatus =
                    "Video guardado • %02d:%02d"
                        .format(
                            minutes,
                            seconds
                        )


            } catch (e: Exception) {

                captureStatus =
                    "Error al detener video: ${e.message}"
            }
        }
    }


    // =========================================================================
    // CRONÓMETRO DE GRABACIÓN
    // =========================================================================

    private fun startRecordingTimer() {

        recordingJob?.cancel()


        recordingJob =
            lifecycleScope.launch {

                while (isRecording) {

                    val minutes =
                        recordingSeconds / 60

                    val seconds =
                        recordingSeconds % 60


                    captureStatus =
                        "🔴 GRABANDO  %02d:%02d"
                            .format(
                                minutes,
                                seconds
                            )


                    delay(1000)


                    recordingSeconds++
                }
            }
    }


    // =========================================================================
    // DESCONECTAR CÁMARA
    // =========================================================================

    private fun disconnectCamera() {

        lifecycleScope.launch {

            try {

                // Si existe una grabación activa,
                // intentamos detenerla antes de desconectar.
                if (isRecording) {

                    try {

                        cameraDevice
                            ?.capture
                            ?.stopCapture()

                    } catch (_: Exception) {

                    }
                }


                recordingJob?.cancel()

                recordingJob = null

                isRecording = false


                // Liberar conexión SDK
                cameraDevice?.release()


            } catch (_: Exception) {

            }


            cameraDevice = null

            cameraConnected = false

            recordingSeconds = 0


            cameraStatus =
                "Cámara desconectada"

            captureStatus =
                "Esperando conexión"
        }
    }


    // =========================================================================
    // CIERRE DE LA ACTIVIDAD
    // =========================================================================

    override fun onDestroy() {

        recordingJob?.cancel()

        recordingJob = null

        cameraDevice = null

        super.onDestroy()
    }
}


// =============================================================================
// INTERFAZ PDI FIELD 360
// =============================================================================

@Composable
fun PDIField360Screen(

    cameraConnected: Boolean,

    cameraStatus: String,

    captureStatus: String,

    isRecording: Boolean,

    recordingSeconds: Int,

    onConnect: () -> Unit,

    onDisconnect: () -> Unit,

    onPhoto: () -> Unit,

    onStartVideo: () -> Unit,

    onStopVideo: () -> Unit
) {

    Column(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 45.dp,
                    bottom = 20.dp
                ),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {


        // =====================================================================
        // ENCABEZADO
        // =====================================================================

        Text(
            text = "PDI FIELD 360",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )


        Text(
            text = "Captura e Inspección Industrial 360°",
            fontSize = 14.sp,
            color = Color.Gray
        )


        Spacer(
            modifier =
                Modifier.height(25.dp)
        )


        // =====================================================================
        // CÁMARA
        // =====================================================================

        Text(
            text = "INSTA360 X3",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(

            text =
                "● $cameraStatus",

            color =
                if (cameraConnected)
                    Color(0xFF198754)
                else
                    Color(0xFFD32F2F),

            fontWeight =
                FontWeight.Medium
        )


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        // =====================================================================
        // PANEL DE ESTADO
        // =====================================================================

        Card(

            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(16.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color(0xFF151A1F)
                )
        ) {

            Column(

                modifier =
                    Modifier.padding(25.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {


                Text(

                    text =
                        if (isRecording)
                            "GRABACIÓN 360°"
                        else
                            "INSTA360 CAMERA",

                    color = Color.White,

                    fontSize = 20.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                if (isRecording) {

                    val minutes =
                        recordingSeconds / 60

                    val seconds =
                        recordingSeconds % 60


                    Text(

                        text =
                            "● REC  %02d:%02d"
                                .format(
                                    minutes,
                                    seconds
                                ),

                        color =
                            Color(0xFFFF5252),

                        fontSize =
                            25.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                } else {

                    Text(

                        text =
                            captureStatus,

                        color =
                            Color.LightGray
                    )
                }
            }
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        // =====================================================================
        // CONECTAR / DESCONECTAR
        // =====================================================================

        Button(

            onClick = {

                if (cameraConnected)
                    onDisconnect()
                else
                    onConnect()
            },

            enabled =
                !isRecording,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
        ) {

            Text(

                text =
                    if (cameraConnected)
                        "DESCONECTAR X3"
                    else
                        "CONECTAR X3",

                fontWeight =
                    FontWeight.Bold
            )
        }


        Spacer(
            modifier =
                Modifier.height(15.dp)
        )


        // =====================================================================
        // FOTOGRAFÍA
        // =====================================================================

        Button(

            onClick =
                onPhoto,

            enabled =
                cameraConnected &&
                        !isRecording,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
        ) {

            Text(
                text =
                    "📷  TOMAR FOTO 360°",

                fontSize =
                    16.sp,

                fontWeight =
                    FontWeight.Bold
            )
        }


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        // =====================================================================
        // VIDEO
        // =====================================================================

        Button(

            onClick = {

                if (isRecording)
                    onStopVideo()
                else
                    onStartVideo()
            },

            enabled =
                cameraConnected,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
        ) {

            Text(

                text =
                    if (isRecording)
                        "■  DETENER VIDEO"
                    else
                        "●  INICIAR VIDEO",

                fontSize =
                    16.sp,

                fontWeight =
                    FontWeight.Bold
            )
        }


        Spacer(
            modifier =
                Modifier.height(22.dp)
        )


        HorizontalDivider()


        Spacer(
            modifier =
                Modifier.height(18.dp)
        )


        // =====================================================================
        // INFORMACIÓN DEL SISTEMA
        // =====================================================================

        Card(

            modifier =
                Modifier.fillMaxWidth(),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color(0xFFF3F4F6)
                )
        ) {

            Column(

                modifier =
                    Modifier.padding(16.dp)
            ) {


                Text(
                    text =
                        "PDI Advanced",

                    fontWeight =
                        FontWeight.Bold
                )


                Text(
                    text =
                        "Sistema de captura e inspección industrial 360°",

                    color =
                        Color.DarkGray
                )


                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )


                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text(
                        text =
                            "Cámara",

                        fontSize =
                            12.sp,

                        color =
                            Color.Gray
                    )


                    Text(
                        text =
                            if (cameraConnected)
                                "X3 • ONLINE"
                            else
                                "OFFLINE",

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            if (cameraConnected)
                                Color(0xFF198754)
                            else
                                Color.Gray
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(5.dp)
                )


                Text(
                    text =
                        "Insta360 Android SDK 2.1.5",

                    fontSize =
                        12.sp,

                    color =
                        Color.Gray
                )
            }
        }
    }
}
