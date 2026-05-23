package com.example.vehiclelicensereportapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.example.vehiclelicensereportapp.ui.theme.VehicleLicenseReportAppTheme
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// 앱의 시작점입니다. Compose 화면을 띄우고 전체 화면 흐름을 시작합니다.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VehicleLicenseReportAppTheme {
                PlateReportApp()
            }
        }
    }
}

// 앱의 현재 화면 상태와 입력값을 관리하는 최상위 Compose 화면입니다.
// 별도 Navigation 라이브러리 없이 enum 상태로 화면을 전환합니다.
@Composable
private fun PlateReportApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.Home) }
    var selectedCategory by remember { mutableStateOf<ViolationCategory?>(null) }
    var selectedViolationType by remember { mutableStateOf("") }
    var recognizedPlate by remember { mutableStateOf("") }
    var recognizedOcrText by remember { mutableStateOf("") }
    var plateCropFilePath by remember { mutableStateOf("") }
    var vehiclePhotoFileNames by remember { mutableStateOf("") }
    var reportContent by remember { mutableStateOf("") }
    var phoneNumberDigits by remember { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(currentScreen) {
        if ((currentScreen == AppScreen.PlateCamera || currentScreen == AppScreen.VehicleCamera) && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color.White
        ) {
            when (currentScreen) {
                AppScreen.Home -> HomeScreen(
                    onReportClick = { currentScreen = AppScreen.Category },
                    onHistoryClick = {
                        Toast.makeText(context, "신고내역 화면은 다음 단계에서 추가합니다.", Toast.LENGTH_SHORT).show()
                    },
                    onProfileClick = {
                        Toast.makeText(context, "프로필 화면은 다음 단계에서 추가합니다.", Toast.LENGTH_SHORT).show()
                    }
                )

                AppScreen.Category -> ViolationCategoryScreen(
                    onBackClick = { currentScreen = AppScreen.Home },
                    onCategorySelected = { category ->
                        selectedCategory = category
                        currentScreen = AppScreen.ViolationType
                    }
                )

                AppScreen.ViolationType -> ViolationTypeScreen(
                    category = selectedCategory,
                    onBackClick = { currentScreen = AppScreen.Category },
                    onViolationTypeSelected = { violationType ->
                        selectedViolationType = violationType
                        recognizedPlate = ""
                        recognizedOcrText = ""
                        plateCropFilePath = ""
                        vehiclePhotoFileNames = ""
                        reportContent = ""
                        phoneNumberDigits = ""
                        currentScreen = AppScreen.PlateCamera
                    }
                )

                AppScreen.PlateCamera -> {
                    if (hasCameraPermission) {
                        PlateOcrCameraScreen(
                            categoryName = selectedCategory?.name.orEmpty(),
                            violationType = selectedViolationType,
                            onBackClick = { currentScreen = AppScreen.ViolationType },
                            onPlateRecognized = { ocrText, plate, cropFilePath ->
                                recognizedOcrText = ocrText
                                recognizedPlate = plate.ifBlank { normalizeRecognizedPlateText(ocrText) }
                                plateCropFilePath = cropFilePath
                                currentScreen = AppScreen.PlateConfirm
                            }
                        )
                    } else {
                        PermissionScreen(
                            onBackClick = { currentScreen = AppScreen.ViolationType },
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }

                AppScreen.PlateConfirm -> PlateConfirmScreen(
                    plate = recognizedPlate,
                    ocrText = recognizedOcrText,
                    cropFilePath = plateCropFilePath,
                    onPlateChange = { recognizedPlate = it },
                    onRetakeClick = { currentScreen = AppScreen.PlateCamera },
                    onNextClick = { currentScreen = AppScreen.VehicleCamera }
                )

                AppScreen.VehicleCamera -> {
                    if (hasCameraPermission) {
                        VehiclePhotoCameraScreen(
                            categoryName = selectedCategory?.name.orEmpty(),
                            violationType = selectedViolationType,
                            onBackClick = { currentScreen = AppScreen.PlateConfirm },
                            onVehiclePhotosCaptured = { fileNames ->
                                vehiclePhotoFileNames = fileNames
                                currentScreen = AppScreen.ReportDetail
                            }
                        )
                    } else {
                        PermissionScreen(
                            onBackClick = { currentScreen = AppScreen.PlateConfirm },
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }

                AppScreen.ReportDetail -> ReportDetailScreen(
                    violationType = selectedViolationType,
                    photoFileName = vehiclePhotoFileNames,
                    plate = recognizedPlate,
                    content = reportContent,
                    phoneNumber = phoneNumberDigits,
                    onPlateChange = { recognizedPlate = it },
                    onContentChange = { reportContent = it.take(900) },
                    onPhoneNumberChange = { phoneNumberDigits = it.filter { char -> char.isDigit() }.take(11) },
                    onBackClick = { currentScreen = AppScreen.PlateConfirm },
                    onNextClick = { currentScreen = AppScreen.FalseReportWarning }
                )

                AppScreen.FalseReportWarning -> FalseReportWarningScreen(
                    onBackClick = { currentScreen = AppScreen.ReportDetail },
                    onReportClick = {
                        Toast.makeText(context, "신고 기능은 다음 단계에서 연결합니다.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

// 사용자가 이동할 수 있는 화면 목록입니다.
private enum class AppScreen {
    Home,
    Category,
    ViolationType,
    PlateCamera,
    PlateConfirm,
    VehicleCamera,
    ReportDetail,
    FalseReportWarning
}

// 법령 카테고리와 그 아래에 들어갈 세부 위반유형 목록을 담는 데이터 구조입니다.
private data class ViolationCategory(
    val name: String,
    val description: String,
    val types: List<String>
)

// 신고 유형 선택 화면에서 보여줄 법령 카테고리 데이터입니다.
private val violationCategories = listOf(
    ViolationCategory(
        name = "도로교통법",
        description = "일반 도로의 불법 주정차 신고 유형",
        types = listOf("소화전", "교차로", "버스정류장", "횡단보도", "어린이보호구역", "인도", "기타")
    ),
    ViolationCategory(
        name = "장애인등 편의법",
        description = "장애인 전용 주차구역 위반 신고 유형",
        types = listOf("장애인 전용구역")
    ),
    ViolationCategory(
        name = "소방기본법",
        description = "소방 활동 공간 침해 신고 유형",
        types = listOf("소방차 전용구역")
    ),
    ViolationCategory(
        name = "친환경 자동차법",
        description = "친환경차 충전 방해 신고 유형",
        types = listOf("친환경차 충전구역")
    )
)

// 메인/카테고리 카드 버튼에 사용하는 그라데이션 색상 모음입니다.
private val tileGradients = listOf(
    listOf(Color(0xFF5ED477), Color(0xFF27C4B2)),
    listOf(Color(0xFF31CBB2), Color(0xFF2AC8C8)),
    listOf(Color(0xFF17B7D5), Color(0xFF25C4D8)),
    listOf(Color(0xFF43A7E7), Color(0xFF438FE2)),
    listOf(Color(0xFF4E73E8), Color(0xFF4367DF)),
    listOf(Color(0xFF426FE2), Color(0xFF3F62D8))
)

// 앱 전반에서 반복 사용하는 주요 색상입니다.
private val primaryButtonColor = Color(0xFF456AE3)
private val headingColor = Color(0xFF202638)
private val bodyTextColor = Color(0xFF6D7485)

// 카메라 화면의 번호판 가이드 박스 비율입니다.
// 예시 번호판처럼 가로로 길고 낮은 직사각형이 되도록 약 5:1 비율에 맞췄습니다.
private const val PLATE_GUIDE_WIDTH_RATIO = 0.82f
private const val PLATE_GUIDE_HEIGHT_RATIO = 0.10f
private const val PLATE_GUIDE_TOP_RATIO = 0.42f

// 앱 첫 화면입니다. 신고하기, 신고내역, 프로필, 도움말 버튼을 보여줍니다.
@Composable
private fun HomeScreen(
    onReportClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = "불법 주정차 신고",
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "대한민국의 깨끗한 도로를 위해",
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Spacer(modifier = Modifier.height(34.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GradientMenuCard(
                modifier = Modifier.weight(1f),
                title = "신고하기",
                subtitle = "번호판 촬영",
                gradient = tileGradients[0],
                onClick = onReportClick
            )
            GradientMenuCard(
                modifier = Modifier.weight(1f),
                title = "신고내역",
                subtitle = "기록 확인",
                gradient = tileGradients[1],
                onClick = onHistoryClick
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GradientMenuCard(
                modifier = Modifier.weight(1f),
                title = "프로필",
                subtitle = "내 정보",
                gradient = tileGradients[2],
                onClick = onProfileClick
            )
            GradientMenuCard(
                modifier = Modifier.weight(1f),
                title = "도움말",
                subtitle = "신고 기준",
                gradient = tileGradients[3],
                onClick = {
                    Toast.makeText(context, "도움말 화면은 다음 단계에서 추가합니다.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// 그라데이션이 들어간 큰 메뉴 카드입니다. 메인 메뉴와 카테고리 선택에 함께 사용합니다.
@Composable
private fun GradientMenuCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(132.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp), clip = false),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient))
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}

// 파란색 기본 액션 버튼입니다. 이전/다음/신고하기 같은 주요 버튼에 공통 적용합니다.
@Composable
private fun PrimaryActionButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        modifier = modifier.height(50.dp),
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryButtonColor,
            contentColor = Color.White,
            disabledContainerColor = primaryButtonColor.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.75f)
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// 신고하기를 눌렀을 때 먼저 보이는 법령 카테고리 선택 화면입니다.
@Composable
private fun ViolationCategoryScreen(
    onBackClick: () -> Unit,
    onCategorySelected: (ViolationCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = "불법 주정차 위반유형 선택",
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "먼저 신고 기준이 되는 법령 카테고리를 선택하세요.",
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Spacer(modifier = Modifier.height(14.dp))

        violationCategories.forEachIndexed { index, category ->
            GradientMenuCard(
                title = category.name,
                subtitle = category.description,
                gradient = tileGradients[index % tileGradients.size],
                onClick = { onCategorySelected(category) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = "메인으로 돌아가기",
            onClick = onBackClick
        )
    }
}

// 선택한 법령 카테고리에 맞는 세부 위반유형 목록을 보여주는 화면입니다.
@Composable
private fun ViolationTypeScreen(
    category: ViolationCategory?,
    onBackClick: () -> Unit,
    onViolationTypeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = category?.name ?: "위반유형 선택",
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "세부 위반유형을 선택하면 차량 촬영 화면으로 이동합니다.",
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Spacer(modifier = Modifier.height(14.dp))

        category?.types.orEmpty().forEach { violationType ->
            PrimaryActionButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                text = violationType,
                onClick = { onViolationTypeSelected(violationType) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = "카테고리 선택으로 돌아가기",
            onClick = onBackClick
        )
    }
}

// 카메라 권한이 없을 때 권한 요청 버튼을 보여주는 화면입니다.
@Composable
private fun PermissionScreen(
    onBackClick: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "번호판 인식을 위해 카메라 권한이 필요합니다.",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = headingColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = "카메라 권한 허용",
            onClick = onRequestPermission
        )
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = "위반유형 선택으로 돌아가기",
            onClick = onBackClick
        )
    }
}

// 번호판 OCR용 사진을 1장 촬영하는 카메라 화면입니다.
// 촬영 후 OpenCV가 번호판 후보 영역을 잘라내고, 직접 학습한 OCR 결과를 확인 화면으로 넘깁니다.
@Composable
private fun PlateOcrCameraScreen(
    categoryName: String,
    violationType: String,
    onBackClick: () -> Unit,
    onPlateRecognized: (ocrText: String, plate: String, cropFilePath: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    var isProcessing by remember { mutableStateOf(false) }
    val captureMessage = "번호판이 보이게 차량 사진을 찍어주세요"

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    val previewView = PreviewView(viewContext)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)

                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        },
                        ContextCompat.getMainExecutor(viewContext)
                    )

                    previewView
                }
            )

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }

            CaptureNotice(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp),
                message = captureMessage
            )

            PlateGuideOverlay(modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .size(82.dp)
                    .background(Color.White.copy(alpha = 0.32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                        .clickable(enabled = !isProcessing) {
                            isProcessing = true
                            captureAndRecognizePlate(
                                context = context,
                                imageCapture = imageCapture,
                                executor = cameraExecutor,
                                onResult = { ocrText, plate, cropFilePath ->
                                    isProcessing = false
                                    onPlateRecognized(ocrText, plate, cropFilePath)
                                },
                                onError = { message ->
                                    isProcessing = false
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "선택한 카테고리: $categoryName",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = headingColor
            )
            Text(
                text = "선택한 유형: $violationType",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = headingColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing,
                    text = "이전",
                    onClick = onBackClick
                )
            }
        }
    }
}

// 신고 증빙용 차량 사진을 앞/뒤 2장 촬영하는 카메라 화면입니다.
// 이 단계는 OCR이 아니라 첨부사진 확보가 목적이므로 번호판 테두리 없이 전체 차량을 촬영합니다.
@Composable
private fun VehiclePhotoCameraScreen(
    categoryName: String,
    violationType: String,
    onBackClick: () -> Unit,
    onVehiclePhotosCaptured: (fileNames: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    var isProcessing by remember { mutableStateOf(false) }
    var firstPhotoFileName by remember { mutableStateOf("") }
    val captureMessage = if (firstPhotoFileName.isBlank()) {
        "차량 앞 뒤 총 2장을 찍어주세요"
    } else {
        "한번 더찍어주세요"
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    val previewView = PreviewView(viewContext)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)

                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        },
                        ContextCompat.getMainExecutor(viewContext)
                    )

                    previewView
                }
            )

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }

            CaptureNotice(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp),
                message = captureMessage
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .size(82.dp)
                    .background(Color.White.copy(alpha = 0.32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                        .clickable(enabled = !isProcessing) {
                            isProcessing = true
                            capturePhoto(
                                context = context,
                                imageCapture = imageCapture,
                                executor = cameraExecutor,
                                prefix = "vehicle",
                                onResult = { fileName ->
                                    isProcessing = false
                                    if (firstPhotoFileName.isBlank()) {
                                        firstPhotoFileName = fileName
                                    } else {
                                        onVehiclePhotosCaptured("$firstPhotoFileName, $fileName")
                                    }
                                },
                                onError = { message ->
                                    isProcessing = false
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "선택한 카테고리: $categoryName",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = headingColor
            )
            Text(
                text = "선택한 유형: $violationType",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = headingColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                text = "이전",
                onClick = onBackClick
            )
        }
    }
}

// 카메라 화면 상단에 표시되는 흰색 안내 배너입니다.
@Composable
private fun CaptureNotice(
    modifier: Modifier = Modifier,
    message: String
) {
    Row(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(0xFFFFC107), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Text(
            text = message,
            color = Color.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// 번호판을 맞춰 찍을 수 있도록 카메라 프리뷰 위에 그리는 흰색 가이드 박스입니다.
@Composable
private fun PlateGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val guideWidth = size.width * PLATE_GUIDE_WIDTH_RATIO
        val guideHeight = size.height * PLATE_GUIDE_HEIGHT_RATIO * 1.16f
        val left = (size.width - guideWidth) / 2f
        val top = size.height * PLATE_GUIDE_TOP_RATIO - (size.height * PLATE_GUIDE_HEIGHT_RATIO * 0.16f)
        val right = left + guideWidth
        val bottom = top + guideHeight
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val color = Color.White

        drawLine(color, Offset(left, top), Offset(right, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(right, top), Offset(right, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(right, bottom), Offset(left, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(left, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

// 입력값이 부족하거나 형식이 맞지 않을 때 보여주는 공통 경고 배너입니다.
@Composable
private fun InlineWarningBanner(
    modifier: Modifier = Modifier,
    message: String
) {
    Row(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(28.dp))
            .shadow(4.dp, RoundedCornerShape(28.dp), clip = false)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFFFFC107), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Text(
            text = message,
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// OCR로 읽은 번호판을 사용자가 확인하고 수정하는 화면입니다.
@Composable
private fun PlateConfirmScreen(
    plate: String,
    ocrText: String,
    cropFilePath: String,
    onPlateChange: (String) -> Unit,
    onRetakeClick: () -> Unit,
    onNextClick: () -> Unit
) {
    var warningMessage by remember { mutableStateOf("") }
    val cropBitmap = remember(cropFilePath) {
        cropFilePath.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        if (warningMessage.isNotBlank()) {
            InlineWarningBanner(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                message = warningMessage
            )
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(64.dp))
        }
        Text(
            text = "이 번호가 맞습니까?",
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "다시한번 정확한지 확인해주세요",
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (cropBitmap != null) {
            Text(
                text = "OCR에 사용한 번호판 영역",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = bodyTextColor
            )
            Image(
                bitmap = cropBitmap.asImageBitmap(),
                contentDescription = "OCR에 사용한 번호판 crop 이미지",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .border(1.dp, Color(0xFFD8DDE8), RoundedCornerShape(8.dp))
                    .padding(6.dp),
                contentScale = ContentScale.Fit
            )
        }

        if (ocrText.isNotBlank()) {
            Text(
                text = "OCR 원문: $ocrText",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = bodyTextColor
            )
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = plate,
            onValueChange = onPlateChange,
            label = { Text(text = "번호판 확인/수정") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryActionButton(
                modifier = Modifier.weight(1f),
                text = "다시찍기",
                onClick = onRetakeClick
            )
            PrimaryActionButton(
                modifier = Modifier.weight(1f),
                text = "다음으로",
                onClick = {
                    if (plate.isBlank()) {
                        warningMessage = "번호판을 입력해주세요"
                    } else {
                        warningMessage = ""
                        onNextClick()
                    }
                }
            )
        }
    }
}

// 최종 신고 전에 유형, 첨부사진, 번호판, 내용, 휴대전화를 확인하고 입력하는 화면입니다.
@Composable
private fun ReportDetailScreen(
    violationType: String,
    photoFileName: String,
    plate: String,
    content: String,
    phoneNumber: String,
    onPlateChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit
) {
    var warningMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        if (warningMessage.isNotBlank()) {
            InlineWarningBanner(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                message = warningMessage
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }
        Text(
            text = "신고 정보 확인",
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "제출 전 번호판과 신고 유형을 확인해주세요",
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = violationType,
            onValueChange = {},
            label = { Text(text = "유형") },
            singleLine = true,
            readOnly = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black)
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = photoFileName,
            onValueChange = {},
            label = { Text(text = "첨부사진") },
            singleLine = true,
            readOnly = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black)
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = plate,
            onValueChange = onPlateChange,
            label = { Text(text = "번호판 확인/수정") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black)
        )

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            value = content,
            onValueChange = onContentChange,
            label = {
                Text(
                    text = "내용 (수정 가능, 5~900자)",
                    fontSize = 13.sp
                )
            },
            placeholder = { Text(text = "불법 주정차 위반 사항을 신고해 주세요.") },
            minLines = 5,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black)
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = { Text(text = "휴대전화") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PhoneNumberVisualTransformation,
            placeholder = { Text(text = "010 - 1234 - 5678") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black)
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryActionButton(
                modifier = Modifier.weight(1f),
                text = "이전",
                onClick = onBackClick
            )
            PrimaryActionButton(
                modifier = Modifier.weight(1f),
                text = "다음으로",
                onClick = {
                    if (plate.isBlank() || content.length < 5 || phoneNumber.length != 11) {
                        warningMessage = "알맞은 양식으로 입력해주세요"
                    } else {
                        warningMessage = ""
                        onNextClick()
                    }
                }
            )
        }
    }
}

// 허위 신고에 대한 법적 책임을 한 번 더 안내하는 최종 경고 화면입니다.
@Composable
private fun FalseReportWarningScreen(
    onBackClick: () -> Unit,
    onReportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .background(Color(0xFFFFC107), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "한번 더 확인해주세요",
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "허위 신고시 무고죄 또는 공무집행방해죄로 법적 처벌을 받을 수 있습니다",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryActionButton(
                modifier = Modifier.weight(1f),
                text = "이전",
                onClick = onBackClick
            )
            PrimaryActionButton(
                modifier = Modifier.weight(1f),
                text = "신고하기",
                onClick = onReportClick
            )
        }
    }
}

// CameraX로 사진을 파일에 저장한 뒤 OCR 처리 함수로 넘깁니다.
private fun captureAndRecognizePlate(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onResult: (ocrText: String, plate: String, cropFilePath: String) -> Unit,
    onError: (message: String) -> Unit
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val postResult: (String, String, String) -> Unit = { ocrText, plate, cropFilePath ->
        mainHandler.post { onResult(ocrText, plate, cropFilePath) }
    }
    val postError: (String) -> Unit = { message ->
        mainHandler.post { onError(message) }
    }

    val photoFile = File(
        context.cacheDir,
        "plate_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                recognizePlateFromImage(
                    context = context,
                    uri = uri,
                    fileName = photoFile.name,
                    onResult = postResult,
                    onError = postError
                )
            }

            override fun onError(exception: ImageCaptureException) {
                postError("사진 촬영에 실패했습니다: ${exception.message}")
            }
        }
    )
}

// OCR 없이 첨부용 사진만 저장합니다. 결과는 신고정보 확인 화면의 첨부사진 항목에 표시합니다.
private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    prefix: String,
    onResult: (fileName: String) -> Unit,
    onError: (message: String) -> Unit
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val photoFile = File(
        context.cacheDir,
        "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                mainHandler.post { onResult(photoFile.name) }
            }

            override fun onError(exception: ImageCaptureException) {
                mainHandler.post { onError("사진 촬영에 실패했습니다: ${exception.message}") }
            }
        }
    )
}

// 저장된 사진에서 번호판 가이드 영역만 잘라내고 보정한 뒤 직접 학습한 ONNX OCR을 실행합니다.
private fun recognizePlateFromImage(
    context: Context,
    uri: Uri,
    fileName: String,
    onResult: (ocrText: String, plate: String, cropFilePath: String) -> Unit,
    onError: (message: String) -> Unit
) {
    val plateBitmaps = runCatching {
        val originalBitmap = loadOrientedBitmap(context, uri)
        buildPlateBitmapCandidates(originalBitmap)
    }.getOrElse { exception ->
        onError("번호판 영역 보정에 실패했습니다: ${exception.message}")
        return
    }

    processPlateBitmapCandidates(
        context = context,
        bitmaps = plateBitmaps,
        index = 0,
        fileName = fileName,
        onResult = onResult,
        onError = onError
    )
}

// 여러 crop/전처리 후보를 모두 OCR에 넣고, 번호판 형식에 가장 가까운 결과를 선택합니다.
private fun processPlateBitmapCandidates(
    context: Context,
    bitmaps: List<Bitmap>,
    index: Int,
    fileName: String,
    onResult: (ocrText: String, plate: String, cropFilePath: String) -> Unit,
    onError: (message: String) -> Unit
) {
    if (bitmaps.isEmpty()) {
        onError("번호판 인식에 실패했습니다.")
        return
    }

    var bestBitmap: Bitmap? = null
    var bestOcrText = ""
    var bestPlateText = ""
    var bestScore = Int.MIN_VALUE
    var lastFailure: Throwable? = null
    val candidateResults = mutableListOf<OcrCandidateResult>()

    bitmaps.drop(index).forEach { bitmap ->
        runCatching {
            PlateOcrOnnxRecognizer.recognize(context, bitmap)
        }.onSuccess { ocrText ->
            val plate = findKoreanPlateCandidate(ocrText)
            val normalizedText = normalizeRecognizedPlateText(ocrText)
            val selectedText = plate.ifBlank { normalizedText }
            val score = scoreOcrCandidate(ocrText, normalizedText, plate)

            if (isUsableOcrCandidate(selectedText, plate)) {
                candidateResults += OcrCandidateResult(
                    bitmap = bitmap,
                    ocrText = ocrText,
                    normalizedText = normalizedText,
                    plateText = plate,
                    score = score
                )
            }

            if (isUsableOcrCandidate(selectedText, plate) && score > bestScore) {
                bestBitmap = bitmap
                bestOcrText = ocrText
                bestPlateText = selectedText
                bestScore = score
            }
        }.onFailure { exception ->
            lastFailure = exception
        }
    }

    bitmaps.drop(index).take(12).forEach { bitmap ->
        runCatching {
            recognizePlateByCharacterSegments(context, bitmap)
        }.onSuccess { segmentedPlate ->
            if (segmentedPlate != null) {
                candidateResults += OcrCandidateResult(
                    bitmap = bitmap,
                    ocrText = segmentedPlate,
                    normalizedText = segmentedPlate,
                    plateText = segmentedPlate,
                    score = scoreOcrCandidate(segmentedPlate, segmentedPlate, segmentedPlate) + 70
                )
            }
        }.onFailure { exception ->
            lastFailure = exception
        }
    }

    val consensusPlate = buildPlateConsensus(candidateResults.mapNotNull { result ->
        result.plateText.takeIf { it.isNotBlank() }
    })
    if (consensusPlate != null) {
        val selectedCandidate = candidateResults
            .filter { it.plateText == consensusPlate }
            .maxByOrNull { it.score }
            ?: candidateResults.maxByOrNull { it.score }

        if (selectedCandidate != null) {
            val cropFilePath = saveDebugPlateCrop(context, selectedCandidate.bitmap, fileName)
            onResult(selectedCandidate.ocrText, consensusPlate, cropFilePath)
            return
        }
    }

    val selectedBitmap = bestBitmap
    if (selectedBitmap != null) {
        val cropFilePath = saveDebugPlateCrop(context, selectedBitmap, fileName)
        onResult(bestOcrText, bestPlateText, cropFilePath)
    } else {
        onError("번호판 인식에 실패했습니다: ${lastFailure?.message ?: "인식된 문자가 없습니다."}")
    }
}

// 번호판 정규식에 실패한 OCR 결과 중에서도 사용자가 수정하기 쉬운 후보를 고릅니다.
private fun scoreOcrFallback(text: String): Int {
    if (text.isBlank()) return Int.MIN_VALUE

    var score = 0
    if (text.any { it in '가'..'힣' }) score += 40
    if (text.length in 7..8) score += 30
    score += text.length.coerceAtMost(8)
    score -= kotlin.math.abs(8 - text.length) * 2
    return score
}

// 촬영 이미지에 저장된 EXIF 회전 정보를 읽어 실제 방향에 맞는 Bitmap으로 돌려줍니다.
private data class OcrCandidateResult(
    val bitmap: Bitmap,
    val ocrText: String,
    val normalizedText: String,
    val plateText: String,
    val score: Int
)

private fun scoreOcrCandidate(rawText: String, normalizedText: String, plateText: String): Int {
    val text = plateText.ifBlank { normalizedText }
    if (text.isBlank()) return Int.MIN_VALUE

    var score = 0
    if (plateText.isNotBlank()) score += 200
    if (text.any { isKoreanPlateChar(it) }) score += 40
    if (text.length == 8) score += 36
    if (text.length == 7) score += 28
    score += text.length.coerceAtMost(8)
    score -= kotlin.math.abs(8 - text.length) * 2
    score -= rawText.count { !it.isDigit() && !isKoreanPlateChar(it) } * 3
    return score
}

private fun isUsableOcrCandidate(text: String, plateText: String): Boolean {
    if (text.isBlank()) return false
    if (plateText.isNotBlank()) return true
    return text.count { it.isDigit() } >= 2
}

private fun buildPlateConsensus(plates: List<String>): String? {
    if (plates.isEmpty()) return null

    val targetLength = plates
        .groupingBy { it.length }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        .first()
        .key

    val sameLengthPlates = plates.filter { it.length == targetLength }
    if (sameLengthPlates.isEmpty()) return null

    val result = StringBuilder()
    for (index in 0 until targetLength) {
        val candidates = sameLengthPlates.mapNotNull { plate ->
            plate.getOrNull(index)?.takeIf { char ->
                if (isKoreanPlatePosition(targetLength, index)) {
                    isKoreanPlateChar(char)
                } else {
                    char.isDigit()
                }
            }
        }

        val selected = candidates
            .groupingBy { it }
            .eachCount()
            .entries
            .maxWithOrNull(compareBy<Map.Entry<Char, Int>> { it.value }.thenBy { it.key })
            ?.key
            ?: return null

        result.append(selected)
    }

    return result.toString().takeIf { findKoreanPlateCandidate(it) == it }
}

private fun isKoreanPlatePosition(length: Int, index: Int): Boolean {
    return (length == 7 && index == 2) || (length == 8 && index == 3)
}

private fun isKoreanPlateChar(char: Char): Boolean {
    return char in '가'..'힣'
}

private fun recognizePlateByCharacterSegments(context: Context, bitmap: Bitmap): String? {
    val characterBitmaps = extractCharacterBitmaps(bitmap)
    if (characterBitmaps.size !in 7..8) return null

    val result = StringBuilder()
    characterBitmaps.forEachIndexed { index, characterBitmap ->
        val expectedKorean = isKoreanPlatePosition(characterBitmaps.size, index)
        val selectedChar = CharOcrOnnxRecognizer.recognize(
            context = context,
            bitmap = characterBitmap,
            expectedKorean = expectedKorean
        ) ?: return null

        result.append(selectedChar)
    }

    val plate = result.toString()
    return plate.takeIf { findKoreanPlateCandidate(it) == it }
}

private fun extractCharacterBitmaps(bitmap: Bitmap): List<Bitmap> {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val blurred = Mat()
    val binary = Mat()

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, CvSize(3.0, 3.0), 0.0)
        Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        val runs = findCharacterColumnRuns(binary)
        if (runs.size !in 7..8) return emptyList()

        runs.map { run ->
            val bounds = findCharacterBounds(binary, run.first, run.last)
            Bitmap.createBitmap(
                bitmap,
                bounds.left,
                bounds.top,
                bounds.width,
                bounds.height
            )
        }
    } finally {
        rgba.release()
        gray.release()
        blurred.release()
        binary.release()
    }
}

private data class CharacterBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

private fun findCharacterColumnRuns(binary: Mat): List<IntRange> {
    val width = binary.cols()
    val height = binary.rows()
    val threshold = (height * 0.10f).toInt().coerceAtLeast(2)
    val columnCounts = IntArray(width)

    for (x in 0 until width) {
        var count = 0
        for (y in 0 until height) {
            if (binary.get(y, x)[0] > 0.0) count++
        }
        columnCounts[x] = count
    }

    val rawRuns = mutableListOf<IntRange>()
    var start = -1
    for (x in 0 until width) {
        if (columnCounts[x] >= threshold) {
            if (start == -1) start = x
        } else if (start != -1) {
            rawRuns += start until x
            start = -1
        }
    }
    if (start != -1) rawRuns += start until width

    val mergedRuns = mergeCloseRuns(rawRuns, (width * 0.018f).toInt().coerceAtLeast(2))
    val minWidth = (width * 0.025f).toInt().coerceAtLeast(3)
    val maxWidth = (width * 0.24f).toInt().coerceAtLeast(minWidth + 1)

    return mergedRuns
        .filter { run -> run.count() in minWidth..maxWidth }
        .filter { run -> runDarkPixelCount(binary, run) > height * run.count() * 0.08f }
        .let { runs ->
            if (runs.size <= 8) runs else runs.sortedByDescending { runDarkPixelCount(binary, it) }
                .take(8)
                .sortedBy { it.first }
        }
}

private fun mergeCloseRuns(runs: List<IntRange>, maxGap: Int): List<IntRange> {
    if (runs.isEmpty()) return emptyList()
    val merged = mutableListOf<IntRange>()
    var current = runs.first()

    runs.drop(1).forEach { run ->
        if (run.first - current.last <= maxGap) {
            current = current.first..run.last
        } else {
            merged += current
            current = run
        }
    }
    merged += current
    return merged
}

private fun runDarkPixelCount(binary: Mat, run: IntRange): Int {
    var count = 0
    for (x in run) {
        for (y in 0 until binary.rows()) {
            if (binary.get(y, x)[0] > 0.0) count++
        }
    }
    return count
}

private fun findCharacterBounds(binary: Mat, leftRun: Int, rightRun: Int): CharacterBounds {
    val width = binary.cols()
    val height = binary.rows()
    val expandedLeft = (leftRun - (width * 0.015f).toInt()).coerceAtLeast(0)
    val expandedRight = (rightRun + (width * 0.015f).toInt()).coerceAtMost(width - 1)
    var top = height - 1
    var bottom = 0

    for (x in expandedLeft..expandedRight) {
        for (y in 0 until height) {
            if (binary.get(y, x)[0] > 0.0) {
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
        }
    }

    if (bottom <= top) {
        top = 0
        bottom = height - 1
    }

    val marginY = (height * 0.08f).toInt().coerceAtLeast(2)
    val safeTop = (top - marginY).coerceAtLeast(0)
    val safeBottom = (bottom + marginY).coerceAtMost(height - 1)
    return CharacterBounds(
        left = expandedLeft,
        top = safeTop,
        width = (expandedRight - expandedLeft + 1).coerceAtLeast(1),
        height = (safeBottom - safeTop + 1).coerceAtLeast(1)
    )
}

private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap {
    val bitmap = context.contentResolver.openInputStream(uri).use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
    } ?: error("이미지를 읽을 수 없습니다.")

    val rotationDegrees = context.contentResolver.openInputStream(uri).use { inputStream ->
        val exifInputStream = inputStream ?: return@use 0f
        when (ExifInterface(exifInputStream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    if (rotationDegrees == 0f) return bitmap

    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// 화면의 번호판 가이드 박스와 같은 비율로 원본 사진의 중앙 영역을 잘라냅니다.
private fun cropPlateGuideArea(bitmap: Bitmap): Bitmap {
    val cropWidth = (bitmap.width * PLATE_GUIDE_WIDTH_RATIO).toInt().coerceAtLeast(1)
    val cropHeight = (bitmap.height * PLATE_GUIDE_HEIGHT_RATIO).toInt().coerceAtLeast(1)
    val left = ((bitmap.width - cropWidth) / 2).coerceIn(0, bitmap.width - 1)
    val baseTop = (bitmap.height * PLATE_GUIDE_TOP_RATIO).toInt()
    val topExtension = (cropHeight * 0.22f).toInt()
    val top = (baseTop - topExtension).coerceIn(0, bitmap.height - 1)
    val safeWidth = cropWidth.coerceAtMost(bitmap.width - left)
    val bottom = (baseTop + cropHeight).coerceAtMost(bitmap.height)
    val safeHeight = (bottom - top).coerceAtLeast(1)

    return Bitmap.createBitmap(bitmap, left, top, safeWidth, safeHeight)
}

// 전체 차량 사진에서 OpenCV로 번호판처럼 보이는 가로형 후보 영역을 찾아 OCR 후보로 만듭니다.
private fun buildPlateBitmapCandidates(bitmap: Bitmap): List<Bitmap> {
    val guideCandidate = cropPlateGuideArea(bitmap)
    val guideDetectedCandidates = detectPlateCandidates(guideCandidate)
    val candidates = (guideDetectedCandidates + guideCandidate).flatMap { candidate ->
        listOf(candidate, cropLeftDecorationIfPresent(candidate))
    }.distinctBy { candidate ->
        "${candidate.width}x${candidate.height}-${candidate.hashCode()}"
    }

    return candidates.flatMap { candidate ->
        listOf(
            candidate,
            smoothPlateBitmap(candidate),
            enhancePlateBitmap(candidate),
            binarizePlateBitmap(candidate),
            adaptiveBinarizePlateBitmap(candidate)
        )
    }
}

private fun cropLeftDecorationIfPresent(bitmap: Bitmap): Bitmap {
    val sampleWidth = (bitmap.width * 0.18f).toInt().coerceAtLeast(1)
    val centerStart = (bitmap.width * 0.35f).toInt().coerceIn(0, bitmap.width - 1)
    val centerEnd = (bitmap.width * 0.65f).toInt().coerceIn(centerStart + 1, bitmap.width)
    var leftDark = 0
    var centerDark = 0
    var leftTotal = 0
    var centerTotal = 0

    for (y in 0 until bitmap.height) {
        for (x in 0 until sampleWidth) {
            if (pixelLuma(bitmap.getPixel(x, y)) < 95) leftDark++
            leftTotal++
        }
        for (x in centerStart until centerEnd) {
            if (pixelLuma(bitmap.getPixel(x, y)) < 95) centerDark++
            centerTotal++
        }
    }

    val leftDarkRatio = leftDark.toFloat() / leftTotal.coerceAtLeast(1)
    val centerDarkRatio = centerDark.toFloat() / centerTotal.coerceAtLeast(1)
    if (leftDarkRatio < 0.28f || leftDarkRatio < centerDarkRatio * 1.6f) {
        return bitmap
    }

    val cropLeft = (bitmap.width * 0.16f).toInt().coerceIn(1, bitmap.width - 1)
    return Bitmap.createBitmap(bitmap, cropLeft, 0, bitmap.width - cropLeft, bitmap.height)
}

private fun pixelLuma(pixel: Int): Int {
    val red = android.graphics.Color.red(pixel)
    val green = android.graphics.Color.green(pixel)
    val blue = android.graphics.Color.blue(pixel)
    return (red * 0.299f + green * 0.587f + blue * 0.114f).toInt()
}

// Canny edge와 contour를 이용해 번호판 비율에 가까운 사각형 영역을 찾습니다.
private fun detectPlateCandidates(bitmap: Bitmap): List<Bitmap> {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val blurred = Mat()
    val edges = Mat()
    val closed = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(17.0, 5.0))
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, CvSize(5.0, 5.0), 0.0)
        Imgproc.Canny(blurred, edges, 60.0, 180.0)
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        contours
            .map { Imgproc.boundingRect(it) }
            .filter { rect ->
                val aspectRatio = rect.width.toFloat() / rect.height.coerceAtLeast(1)
                aspectRatio in 2.6f..8.4f &&
                    rect.width > bitmap.width * 0.10f &&
                    rect.height > bitmap.height * 0.015f &&
                    rect.height < bitmap.height * 0.28f
            }
            .sortedByDescending { rect ->
                val aspectRatio = rect.width.toFloat() / rect.height.coerceAtLeast(1)
                val aspectScore = 1000 - (kotlin.math.abs(aspectRatio - 5.2f) * 100).toInt()
                rect.width * rect.height + aspectScore
            }
            .take(10)
            .map { rect ->
                val marginX = (rect.width * 0.08f).toInt()
                val topMarginY = (rect.height * 0.42f).toInt()
                val bottomMarginY = (rect.height * 0.16f).toInt()
                val left = (rect.x - marginX).coerceAtLeast(0)
                val top = (rect.y - topMarginY).coerceAtLeast(0)
                val right = (rect.x + rect.width + marginX).coerceAtMost(bitmap.width)
                val bottom = (rect.y + rect.height + bottomMarginY).coerceAtMost(bitmap.height)
                Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            }
    } finally {
        rgba.release()
        gray.release()
        blurred.release()
        edges.release()
        closed.release()
        kernel.release()
        hierarchy.release()
        contours.forEach { it.release() }
    }
}

// 실제 OCR에 넣은 번호판 crop 이미지를 캐시에 저장해 확인 화면에서 보여줍니다.
private fun saveDebugPlateCrop(context: Context, bitmap: Bitmap, sourceFileName: String): String {
    val debugFile = File(
        context.cacheDir,
        "crop_${sourceFileName.substringBeforeLast(".")}_${SimpleDateFormat("HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    )
    debugFile.outputStream().use { outputStream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
    }
    return debugFile.absolutePath
}

// 화면 재촬영에서 생기는 촘촘한 줄무늬를 약하게 만들기 위해 살짝 흐림 처리합니다.
private fun smoothPlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val blurred = Mat()

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, CvSize(3.0, 3.0), 0.0)

        Bitmap.createBitmap(blurred.cols(), blurred.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(blurred, result)
        }
    } finally {
        rgba.release()
        gray.release()
        blurred.release()
    }
}

// 잘라낸 번호판 이미지를 OCR이 읽기 쉽게 OpenCV로 전처리합니다.
// 확대 -> 흑백 변환 -> 명암 보정 -> 선명화 순서로 처리합니다.
private fun enhancePlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, true)
    val rgba = Mat()
    val gray = Mat()
    val denoised = Mat()
    val sharpened = Mat()

    return try {
        Utils.bitmapToMat(scaledBitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.equalizeHist(gray, gray)
        Imgproc.bilateralFilter(gray, denoised, 5, 35.0, 35.0)
        Core.addWeighted(gray, 1.25, denoised, -0.25, 0.0, sharpened)

        Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(sharpened, result)
        }
    } finally {
        rgba.release()
        gray.release()
        denoised.release()
        sharpened.release()
    }
}

// 글자는 검정, 배경은 흰색에 가깝게 분리해 줄무늬 배경 영향을 줄이는 후보를 만듭니다.
private fun binarizePlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val blurred = Mat()
    val binary = Mat()
    val cleaned = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(2.0, 2.0))

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, CvSize(3.0, 3.0), 0.0)
        Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_OPEN, kernel)

        Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(cleaned, result)
        }
    } finally {
        rgba.release()
        gray.release()
        blurred.release()
        binary.release()
        cleaned.release()
        kernel.release()
    }
}

// OCR 전체 결과에서 한국 번호판 형식에 맞는 문자열만 골라냅니다.
// assets/plate_ocr.onnx와 assets/charset.txt를 사용해 직접 학습한 번호판 OCR을 실행합니다.
private fun adaptiveBinarizePlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 珥덇린?붿뿉 ?ㅽ뙣?덉뒿?덈떎.")
    }

    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, true)
    val rgba = Mat()
    val gray = Mat()
    val denoised = Mat()
    val binary = Mat()

    return try {
        Utils.bitmapToMat(scaledBitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.bilateralFilter(gray, denoised, 5, 45.0, 45.0)
        Imgproc.adaptiveThreshold(
            denoised,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            31,
            7.0
        )

        Bitmap.createBitmap(binary.cols(), binary.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(binary, result)
        }
    } finally {
        rgba.release()
        gray.release()
        denoised.release()
        binary.release()
    }
}

private object PlateOcrOnnxRecognizer {
    private const val MODEL_ASSET_NAME = "plate_ocr.onnx"
    private const val CHARSET_ASSET_NAME = "charset.txt"
    private const val INPUT_WIDTH = 192
    private const val INPUT_HEIGHT = 48
    private const val BLANK_INDEX = 0

    @Volatile
    private var environment: OrtEnvironment? = null

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var charset: List<String>? = null

    fun recognize(context: Context, bitmap: Bitmap): String {
        val appContext = context.applicationContext
        val env = environment ?: synchronized(this) {
            environment ?: OrtEnvironment.getEnvironment().also { environment = it }
        }
        val activeSession = session ?: synchronized(this) {
            session ?: env.createSession(
                appContext.assets.open(MODEL_ASSET_NAME).use { it.readBytes() },
                OrtSession.SessionOptions()
            ).also { session = it }
        }
        val activeCharset = charset ?: synchronized(this) {
            charset ?: appContext.assets.open(CHARSET_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
                    .trim()
                    .removePrefix("<blank>")
                    .map { it.toString() }
            }.also { charset = it }
        }

        val input = bitmapToModelInput(bitmap)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 1, INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong())).use { tensor ->
            activeSession.run(mapOf(activeSession.inputNames.first() to tensor)).use { result ->
                val logits = result[0].value as Array<Array<FloatArray>>
                return decodeCtcGreedy(logits, activeCharset)
            }
        }
    }

    // 학습 코드와 같은 방식으로 비율 유지 resize, 흰색 padding, [-1, 1] 정규화를 적용합니다.
    private fun bitmapToModelInput(bitmap: Bitmap): FloatArray {
        val source = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val scale = minOf(INPUT_WIDTH.toFloat() / source.width, INPUT_HEIGHT.toFloat() / source.height)
        val resizedWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val resizedHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true)
        val canvasBitmap = Bitmap.createBitmap(INPUT_WIDTH, INPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(canvasBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(
            resized,
            ((INPUT_WIDTH - resizedWidth) / 2).toFloat(),
            ((INPUT_HEIGHT - resizedHeight) / 2).toFloat(),
            null
        )

        val input = FloatArray(INPUT_WIDTH * INPUT_HEIGHT)
        var offset = 0
        for (y in 0 until INPUT_HEIGHT) {
            for (x in 0 until INPUT_WIDTH) {
                val pixel = canvasBitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                val gray = (red * 0.299f + green * 0.587f + blue * 0.114f) / 255f
                input[offset++] = (gray - 0.5f) / 0.5f
            }
        }
        return input
    }

    // CTC 출력에서 blank와 반복 문자를 제거해 최종 번호판 문자열을 만듭니다.
    private fun decodeCtcGreedy(logits: Array<Array<FloatArray>>, charset: List<String>): String {
        val result = StringBuilder()
        var previous = BLANK_INDEX
        for (timeStep in logits.indices) {
            val classScores = logits[timeStep][0]
            var bestIndex = 0
            var bestScore = classScores[0]
            for (index in 1 until classScores.size) {
                if (classScores[index] > bestScore) {
                    bestScore = classScores[index]
                    bestIndex = index
                }
            }
            if (bestIndex != BLANK_INDEX && bestIndex != previous) {
                charset.getOrNull(bestIndex - 1)?.let(result::append)
            }
            previous = bestIndex
        }
        return result.toString()
    }
}

// 한 글자씩 분리된 이미지에는 전체 번호판 CTC 모델 대신 단일 글자 분류 모델을 사용합니다.
// 자리 정보를 이용해 숫자 자리에서는 숫자만, 한글 자리에서는 번호판 한글만 후보로 고릅니다.
private object CharOcrOnnxRecognizer {
    private const val MODEL_ASSET_NAME = "char_ocr.onnx"
    private const val CHARSET_ASSET_NAME = "char_charset.txt"
    private const val INPUT_SIZE = 48

    @Volatile
    private var environment: OrtEnvironment? = null

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var charset: List<Char>? = null

    fun recognize(context: Context, bitmap: Bitmap, expectedKorean: Boolean): Char? {
        val appContext = context.applicationContext
        val env = environment ?: synchronized(this) {
            environment ?: OrtEnvironment.getEnvironment().also { environment = it }
        }
        val activeSession = session ?: synchronized(this) {
            session ?: env.createSession(
                appContext.assets.open(MODEL_ASSET_NAME).use { it.readBytes() },
                OrtSession.SessionOptions()
            ).also { session = it }
        }
        val activeCharset = charset ?: synchronized(this) {
            charset ?: appContext.assets.open(CHARSET_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText().trim().map { it }
            }.also { charset = it }
        }

        val input = bitmapToModelInput(bitmap)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 1, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())).use { tensor ->
            activeSession.run(mapOf(activeSession.inputNames.first() to tensor)).use { result ->
                val scores = readScores(result[0].value)
                return selectBestCharacter(scores, activeCharset, expectedKorean)
            }
        }
    }

    // 학습용 단독 글자 이미지와 같은 48x48 흰 배경, 비율 유지, [-1, 1] 정규화 입력을 만듭니다.
    private fun bitmapToModelInput(bitmap: Bitmap): FloatArray {
        val source = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val scale = minOf(INPUT_SIZE.toFloat() / source.width, INPUT_SIZE.toFloat() / source.height)
        val resizedWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val resizedHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true)
        val canvasBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(canvasBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(
            resized,
            ((INPUT_SIZE - resizedWidth) / 2).toFloat(),
            ((INPUT_SIZE - resizedHeight) / 2).toFloat(),
            null
        )

        val input = FloatArray(INPUT_SIZE * INPUT_SIZE)
        var offset = 0
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = canvasBitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                val gray = (red * 0.299f + green * 0.587f + blue * 0.114f) / 255f
                input[offset++] = (gray - 0.5f) / 0.5f
            }
        }
        return input
    }

    @Suppress("UNCHECKED_CAST")
    private fun readScores(value: Any): FloatArray {
        return when (value) {
            is Array<*> -> value.firstOrNull() as? FloatArray ?: FloatArray(0)
            is FloatArray -> value
            else -> FloatArray(0)
        }
    }

    private fun selectBestCharacter(scores: FloatArray, charset: List<Char>, expectedKorean: Boolean): Char? {
        var bestChar: Char? = null
        var bestScore = Float.NEGATIVE_INFINITY
        scores.forEachIndexed { index, score ->
            val candidate = charset.getOrNull(index) ?: return@forEachIndexed
            val allowed = if (expectedKorean) {
                isKoreanPlateChar(candidate)
            } else {
                candidate.isDigit()
            }
            if (allowed && score > bestScore) {
                bestScore = score
                bestChar = candidate
            }
        }
        return bestChar
    }
}

private fun findKoreanPlateCandidate(text: String): String {
    val normalized = cleanRecognizedPlateText(text)
    val plateRegex = Regex("[0-9]{2,3}[가-힣][0-9]{4}")
    return plateRegex.find(normalized)?.value.orEmpty()
}

private fun normalizeRecognizedPlateText(text: String): String {
    val cleaned = cleanRecognizedPlateText(text)
    findKoreanPlateCandidate(cleaned).takeIf { it.isNotBlank() }?.let { return it }
    if (cleaned.length <= 8) return cleaned

    return cleaned
        .windowed(8, 1)
        .maxByOrNull { window ->
            var score = 0
            if (window.count { it.isDigit() } >= 6) score += 20
            if (window.any { isKoreanPlateChar(it) }) score += 10
            if (window.take(3).all { it.isDigit() }) score += 6
            if (window.takeLast(4).all { it.isDigit() }) score += 6
            score
        }
        ?: cleaned.take(8)
}

private fun cleanRecognizedPlateText(text: String): String {
    return text.replace(Regex("[^0-9가-힣]"), "")
}

// 휴대전화는 내부적으로 숫자 11자리만 저장하고, 화면에만 010 - 1234 - 5678 형태로 보여줍니다.
private object PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(11)
        val formatted = when {
            digits.length <= 3 -> digits
            digits.length <= 7 -> "${digits.take(3)} - ${digits.drop(3)}"
            else -> "${digits.take(3)} - ${digits.drop(3).take(4)} - ${digits.drop(7)}"
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, digits.length)
                return when {
                    safeOffset <= 3 -> safeOffset
                    safeOffset <= 7 -> safeOffset + 3
                    else -> safeOffset + 6
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, formatted.length)
                return when {
                    safeOffset <= 3 -> safeOffset
                    safeOffset <= 6 -> 3
                    safeOffset <= 10 -> safeOffset - 3
                    safeOffset <= 13 -> 7
                    else -> safeOffset - 6
                }.coerceIn(0, digits.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
