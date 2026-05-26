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
import android.util.Rational
import android.util.Size
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
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Android 진입점에서는 Compose 루트만 연결하고, 실제 화면 흐름은 PlateReportApp에서 관리합니다.
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

// 신고 접수 플로우 전체의 임시 입력값과 화면 이동 상태를 한곳에서 관리합니다.
// 별도 Navigation 라이브러리 없이 enum으로 화면을 전환합니다.
@Composable
private fun PlateReportApp() {
    val context = LocalContext.current
    val firebaseAuth = remember { getConfiguredFirebaseAuth(context) }
    val firestore = remember { getConfiguredFirebaseFirestore(context) }
    val storage = remember { getConfiguredFirebaseStorage(context) }
    var signedInEmail by remember { mutableStateOf(firebaseAuth?.currentUser?.email) }
    var userProfile by remember {
        mutableStateOf(signedInEmail?.let { loadUserProfile(context, it) })
    }
    var isGuestMode by remember { mutableStateOf(false) }
    var currentScreen by remember {
        mutableStateOf(
            if (signedInEmail != null || isGuestMode) AppScreen.Home else AppScreen.Login
        )
    }
    var selectedCategory by remember { mutableStateOf<ViolationCategory?>(null) }
    var selectedViolationType by remember { mutableStateOf("") }
    var recognizedPlate by remember { mutableStateOf("") }
    var recognizedOcrText by remember { mutableStateOf("") }
    var plateCropFilePath by remember { mutableStateOf("") }
    var vehiclePhotoFileNames by remember { mutableStateOf("") }
    var reportContent by remember { mutableStateOf("") }
    var phoneNumberDigits by remember { mutableStateOf("") }
    // 신고 완료 후 신고내역 화면에 바로 반영하고, 앱을 다시 켜도 기록을 유지합니다.
    var reportHistory by remember { mutableStateOf(loadReportHistory(context)) }
    var selectedHistoryReport by remember { mutableStateOf<ReportHistoryItem?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // 신고 완료 후 홈으로 돌아가면 이전 접수 정보가 다음 신고에 섞이지 않도록 초기화합니다.
    val resetReportDraft = {
        selectedCategory = null
        selectedViolationType = ""
        recognizedPlate = ""
        recognizedOcrText = ""
        plateCropFilePath = ""
        vehiclePhotoFileNames = ""
        reportContent = ""
        phoneNumberDigits = ""
    }
    val signOut = {
        firebaseAuth?.signOut()
        signedInEmail = null
        userProfile = null
        isGuestMode = false
        resetReportDraft()
        currentScreen = AppScreen.Login
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

    LaunchedEffect(firebaseAuth?.currentUser?.uid, isGuestMode) {
        val uid = firebaseAuth?.currentUser?.uid
        if (!isGuestMode && uid != null) {
            loadReportsFromFirestore(firestore, uid) { serverReports ->
                reportHistory = serverReports
                saveReportHistory(context, serverReports)
            }
        } else {
            reportHistory = loadReportHistory(context)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = appBackgroundColor
        ) {
            when (currentScreen) {
                AppScreen.Login -> LoginScreen(
                    firebaseConfigured = firebaseAuth != null,
                    onGuestClick = {
                        isGuestMode = true
                        currentScreen = AppScreen.Home
                    },
                    onSignInClick = { userId, password, onComplete ->
                        signInWithUserId(firebaseAuth, userId, password) { success, message ->
                            if (success) {
                                val accountEmail = firebaseAuth?.currentUser?.email ?: authEmailFromUserId(userId)
                                signedInEmail = accountEmail
                                userProfile = loadUserProfile(context, accountEmail) ?: UserProfile(
                                    userId = userId.trim(),
                                    authEmail = accountEmail
                                )
                                loadUserProfileFromFirestore(firestore, userId.trim()) { serverProfile ->
                                    if (serverProfile != null) {
                                        saveUserProfile(context, serverProfile)
                                        userProfile = serverProfile
                                    }
                                }
                                isGuestMode = false
                                currentScreen = AppScreen.Home
                            }
                            onComplete(message)
                        }
                    },
                    onSignUpClick = { email, password, onComplete ->
                        currentScreen = AppScreen.SignUp
                        onComplete(null)
                    }
                )

                AppScreen.SignUp -> SignUpScreenV2(
                    firebaseConfigured = firebaseAuth != null,
                    onBackClick = { currentScreen = AppScreen.Login },
                    onSignUpClick = { profile, password, onComplete ->
                        val authEmail = authEmailFromUserId(profile.userId)
                        createAccountWithEmail(firebaseAuth, authEmail, password) { success, message ->
                            if (success) {
                                val savedProfile = profile.copy(authEmail = firebaseAuth?.currentUser?.email ?: authEmail)
                                saveUserProfile(context, savedProfile)
                                saveUserProfileToFirestore(firestore, firebaseAuth?.currentUser?.uid, savedProfile) {}
                                signedInEmail = savedProfile.authEmail
                                userProfile = savedProfile
                                isGuestMode = false
                                currentScreen = AppScreen.Home
                            }
                            onComplete(message)
                        }
                    }
                )

                AppScreen.Home -> HomeScreen(
                    reports = reportHistory,
                    profileImagePath = userProfile?.profileImagePath.orEmpty(),
                    onReportClick = { currentScreen = AppScreen.Category },
                    onHistoryClick = {
                        firebaseAuth?.currentUser?.uid?.let { uid ->
                            loadReportsFromFirestore(firestore, uid) { serverReports ->
                                reportHistory = serverReports
                                saveReportHistory(context, serverReports)
                            }
                        }
                        currentScreen = AppScreen.ReportHistory
                    },
                    onReportSelected = { report ->
                        selectedHistoryReport = report
                        currentScreen = AppScreen.ReportHistoryDetail
                    },
                    onProfileClick = {
                        if (!isGuestMode && signedInEmail == null) {
                            signOut()
                            return@HomeScreen
                        }
                        currentScreen = AppScreen.Profile
                    }
                )

                AppScreen.Profile -> {
                    if (isGuestMode) {
                        GuestProfileScreen(
                            onBackClick = { currentScreen = AppScreen.Home },
                            onLoginClick = {
                                isGuestMode = false
                                resetReportDraft()
                                currentScreen = AppScreen.Login
                            }
                        )
                    } else {
                        ProfileScreenV2(
                            profile = userProfile ?: UserProfile(authEmail = signedInEmail.orEmpty()),
                            joinedAtText = formatProfileDate(firebaseAuth?.currentUser?.metadata?.creationTimestamp),
                            firebaseConfigured = firebaseAuth != null,
                            onBackClick = { currentScreen = AppScreen.Home },
                            onSaveClick = { updatedProfile, onComplete ->
                                val profileToSave = updatedProfile.copy(
                                    authEmail = updatedProfile.authEmail.ifBlank { signedInEmail.orEmpty() }
                                )
                                saveUserProfile(context, profileToSave)
                                saveUserProfileToFirestore(firestore, firebaseAuth?.currentUser?.uid, profileToSave) {}
                                userProfile = profileToSave
                                signedInEmail = profileToSave.authEmail
                                onComplete("프로필이 저장되었습니다.")
                            },
                            onPasswordChangeClick = { newPassword, onComplete ->
                                updateFirebasePassword(firebaseAuth, newPassword, onComplete)
                            },
                            onLogoutClick = signOut
                        )
                    }
                }

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
                        val reportedAtMillis = System.currentTimeMillis()
                        val newReport = ReportHistoryItem(
                            id = reportedAtMillis.toString(),
                            date = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(reportedAtMillis),
                            time = SimpleDateFormat("HH:mm", Locale.KOREA).format(reportedAtMillis),
                            year = SimpleDateFormat("yyyy", Locale.KOREA).format(reportedAtMillis).toInt(),
                            month = SimpleDateFormat("MM", Locale.KOREA).format(reportedAtMillis).toInt(),
                            reportedAtMillis = reportedAtMillis,
                            categoryName = selectedCategory?.name.orEmpty(),
                            violationType = selectedViolationType.ifBlank { "불법 주정차" },
                            plate = recognizedPlate.ifBlank { "번호판 미입력" },
                            content = reportContent,
                            phoneNumber = phoneNumberDigits,
                            photoFileNames = vehiclePhotoFileNames,
                            status = "접수 완료"
                        )
                        val uid = firebaseAuth?.currentUser?.uid
                        if (!isGuestMode && uid != null) {
                            saveReportToFirestore(context, firestore, storage, uid, newReport) { savedReport, error ->
                                val updatedHistory = listOf(savedReport) + reportHistory.filterNot { it.id == savedReport.id }
                                reportHistory = updatedHistory
                                saveReportHistory(context, updatedHistory)
                                if (error != null) {
                                    Toast.makeText(context, "서버 저장 실패: $error", Toast.LENGTH_SHORT).show()
                                }
                                currentScreen = AppScreen.ReportComplete
                            }
                        } else {
                            val updatedHistory = listOf(newReport) + reportHistory
                            reportHistory = updatedHistory
                            saveReportHistory(context, updatedHistory)
                            currentScreen = AppScreen.ReportComplete
                        }
                    }
                )

                AppScreen.ReportComplete -> ReportCompleteScreen(
                    onHomeClick = {
                        resetReportDraft()
                        currentScreen = AppScreen.Home
                    }
                )

                AppScreen.ReportHistory -> ReportHistoryScreen(
                    reports = reportHistory,
                    onReportSelected = { report ->
                        selectedHistoryReport = report
                        currentScreen = AppScreen.ReportHistoryDetail
                    },
                    onBackClick = { currentScreen = AppScreen.Home }
                )

                AppScreen.ReportHistoryDetail -> selectedHistoryReport?.let { report ->
                    ReportHistoryDetailScreen(
                        report = report,
                        onBackClick = { currentScreen = AppScreen.ReportHistory }
                    )
                } ?: run {
                    currentScreen = AppScreen.ReportHistory
                }
            }
        }
    }
}

// 앱의 주요 화면 단계입니다. 신고 과정은 위에서 아래 순서로 진행하고, 일부 화면은 바로 이동할 수 있습니다.
private enum class AppScreen {
    Login,
    SignUp,
    Home,
    Profile,
    Category,
    ViolationType,
    PlateCamera,
    PlateConfirm,
    VehicleCamera,
    ReportDetail,
    FalseReportWarning,
    ReportComplete,
    ReportHistory,
    ReportHistoryDetail
}

// 법령 카테고리별로 선택 가능한 세부 위반 유형을 묶어 이후 신고 흐름에 전달합니다.
private data class ViolationCategory(
    val name: String,
    val description: String,
    val types: List<String>
)

// 신고 완료 시점의 스냅샷입니다. 상세 화면 복원을 위해 사용자가 입력/촬영한 값을 함께 저장합니다.
private data class UserProfile(
    val userId: String = "",
    val authEmail: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val birthDate: String = "",
    val profileImagePath: String = ""
)

private data class ReportHistoryItem(
    val id: String,
    val date: String,
    val time: String,
    val year: Int,
    val month: Int,
    val reportedAtMillis: Long,
    val categoryName: String,
    val violationType: String,
    val plate: String,
    val content: String,
    val phoneNumber: String,
    val photoFileNames: String,
    val photoUrls: String = "",
    val status: String
)

// 신고내역은 서버 연동 전에도 로컬 SharedPreferences에 JSON으로 보관합니다.
// 사진은 파일명만 저장하고, 실제 이미지는 촬영 때 cacheDir에 저장된 파일을 상세 화면에서 다시 읽습니다.
private fun profileStorageKey(identifier: String): String =
    USER_PROFILE_PREFIX + identifier.trim().lowercase(Locale.US)

private fun authEmailFromUserId(userId: String): String =
    "${userId.trim().lowercase(Locale.US)}@ai-vehicle.local"

private fun loadUserProfile(context: Context, email: String): UserProfile? {
    if (email.isBlank()) return null
    val raw = context
        .getSharedPreferences(USER_PROFILE_PREFS, Context.MODE_PRIVATE)
        .getString(profileStorageKey(email), null)
        ?: return null

    return runCatching {
        val item = JSONObject(raw)
        UserProfile(
            userId = item.optString("userId"),
            authEmail = item.optString("authEmail", email),
            email = item.optString("email"),
            phoneNumber = item.optString("phoneNumber"),
            birthDate = item.optString("birthDate"),
            profileImagePath = item.optString("profileImagePath")
        )
    }.getOrNull()
}

private fun saveUserProfile(context: Context, profile: UserProfile) {
    val key = profile.authEmail.ifBlank { authEmailFromUserId(profile.userId) }
    if (key.isBlank()) return
    val item = JSONObject()
        .put("userId", profile.userId)
        .put("authEmail", key)
        .put("email", profile.email.trim())
        .put("phoneNumber", profile.phoneNumber)
        .put("birthDate", profile.birthDate)
        .put("profileImagePath", profile.profileImagePath)

    context
        .getSharedPreferences(USER_PROFILE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(profileStorageKey(key), item.toString())
        .apply()
}

private fun saveProfileImage(context: Context, uri: Uri, email: String): String? {
    if (email.isBlank()) return null
    val directory = File(context.filesDir, "profile_images").apply { mkdirs() }
    val file = File(directory, profileStorageKey(email) + ".jpg")
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file.absolutePath
    }.getOrNull()
}

private fun formatProfileDate(millis: Long?): String {
    if (millis == null || millis <= 0L) return "정보 없음"
    return SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(millis)
}

private fun loadUserProfileFromFirestore(
    firestore: FirebaseFirestore?,
    userId: String,
    onResult: (UserProfile?) -> Unit
) {
    val trimmedUserId = userId.trim()
    if (firestore == null || trimmedUserId.isBlank()) {
        onResult(null)
        return
    }

    firestore.collection("user_profiles")
        .document(trimmedUserId)
        .get()
        .addOnSuccessListener { document ->
            if (!document.exists()) {
                onResult(null)
            } else {
                onResult(
                    UserProfile(
                        userId = document.getString("userId").orEmpty(),
                        authEmail = document.getString("authEmail").orEmpty(),
                        email = document.getString("email").orEmpty(),
                        phoneNumber = document.getString("phoneNumber").orEmpty(),
                        birthDate = document.getString("birthDate").orEmpty(),
                        profileImagePath = document.getString("profileImagePath").orEmpty()
                    )
                )
            }
        }
        .addOnFailureListener { onResult(null) }
}

private fun saveUserProfileToFirestore(
    firestore: FirebaseFirestore?,
    uid: String?,
    profile: UserProfile,
    onComplete: (String?) -> Unit
) {
    if (firestore == null || uid.isNullOrBlank() || profile.userId.isBlank()) {
        onComplete(null)
        return
    }
    val authEmail = profile.authEmail.ifBlank { authEmailFromUserId(profile.userId) }
    val data = mapOf(
        "uid" to uid,
        "userId" to profile.userId,
        "authEmail" to authEmail,
        "email" to profile.email,
        "phoneNumber" to profile.phoneNumber,
        "birthDate" to profile.birthDate,
        "profileImagePath" to profile.profileImagePath,
        "updatedAt" to System.currentTimeMillis()
    )
    firestore.collection("user_profiles")
        .document(profile.userId)
        .set(data)
        .addOnSuccessListener { onComplete(null) }
        .addOnFailureListener { error -> onComplete(error.localizedMessage) }
}

private fun reportToMap(report: ReportHistoryItem, uid: String): Map<String, Any> {
    return mapOf(
        "id" to report.id,
        "uid" to uid,
        "date" to report.date,
        "time" to report.time,
        "year" to report.year,
        "month" to report.month,
        "reportedAtMillis" to report.reportedAtMillis,
        "categoryName" to report.categoryName,
        "violationType" to report.violationType,
        "plate" to report.plate,
        "content" to report.content,
        "phoneNumber" to report.phoneNumber,
        "photoFileNames" to report.photoFileNames,
        "photoUrls" to report.photoUrls,
        "status" to report.status
    )
}

private fun reportFromMap(id: String, data: Map<String, Any>): ReportHistoryItem {
    return ReportHistoryItem(
        id = (data["id"] as? String).orEmpty().ifBlank { id },
        date = (data["date"] as? String).orEmpty(),
        time = (data["time"] as? String).orEmpty(),
        year = (data["year"] as? Number)?.toInt() ?: 0,
        month = (data["month"] as? Number)?.toInt() ?: 0,
        reportedAtMillis = (data["reportedAtMillis"] as? Number)?.toLong() ?: 0L,
        categoryName = (data["categoryName"] as? String).orEmpty(),
        violationType = (data["violationType"] as? String).orEmpty(),
        plate = (data["plate"] as? String).orEmpty(),
        content = (data["content"] as? String).orEmpty(),
        phoneNumber = (data["phoneNumber"] as? String).orEmpty(),
        photoFileNames = (data["photoFileNames"] as? String).orEmpty(),
        photoUrls = (data["photoUrls"] as? String).orEmpty(),
        status = (data["status"] as? String).orEmpty()
    )
}

private fun loadReportsFromFirestore(
    firestore: FirebaseFirestore?,
    uid: String,
    onResult: (List<ReportHistoryItem>) -> Unit
) {
    if (firestore == null || uid.isBlank()) {
        onResult(emptyList())
        return
    }
    firestore.collection("users")
        .document(uid)
        .collection("reports")
        .orderBy("reportedAtMillis", Query.Direction.DESCENDING)
        .get()
        .addOnSuccessListener { snapshot ->
            onResult(snapshot.documents.map { reportFromMap(it.id, it.data.orEmpty()) })
        }
        .addOnFailureListener { onResult(emptyList()) }
}

private fun saveReportToFirestore(
    context: Context,
    firestore: FirebaseFirestore?,
    storage: FirebaseStorage?,
    uid: String?,
    report: ReportHistoryItem,
    onComplete: (ReportHistoryItem, String?) -> Unit
) {
    if (firestore == null || uid.isNullOrBlank()) {
        onComplete(report, "서버 저장을 위해 로그인이 필요합니다.")
        return
    }

    uploadReportPhotos(context, storage, uid, report) { photoUrls ->
        val serverReport = report.copy(photoUrls = photoUrls.joinToString(","))
        firestore.collection("users")
            .document(uid)
            .collection("reports")
            .document(serverReport.id)
            .set(reportToMap(serverReport, uid))
            .addOnSuccessListener { onComplete(serverReport, null) }
            .addOnFailureListener { error -> onComplete(serverReport, error.localizedMessage) }
    }
}

private fun uploadReportPhotos(
    context: Context,
    storage: FirebaseStorage?,
    uid: String,
    report: ReportHistoryItem,
    onComplete: (List<String>) -> Unit
) {
    val fileNames = report.photoFileNames
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (storage == null || fileNames.isEmpty()) {
        onComplete(emptyList())
        return
    }

    val urls = mutableListOf<String>()
    fun uploadAt(index: Int) {
        if (index >= fileNames.size) {
            onComplete(urls)
            return
        }
        val file = File(context.cacheDir, fileNames[index])
        if (!file.exists()) {
            uploadAt(index + 1)
            return
        }
        val ref = storage.reference
            .child("reports")
            .child(uid)
            .child(report.id)
            .child(file.name)
        ref.putFile(Uri.fromFile(file))
            .continueWithTask { ref.downloadUrl }
            .addOnSuccessListener { uri ->
                urls.add(uri.toString())
                uploadAt(index + 1)
            }
            .addOnFailureListener {
                uploadAt(index + 1)
            }
    }
    uploadAt(0)
}

private fun loadReportHistory(context: Context): List<ReportHistoryItem> {
    val raw = context
        .getSharedPreferences(REPORT_HISTORY_PREFS, Context.MODE_PRIVATE)
        .getString(REPORT_HISTORY_ITEMS, "[]")
        .orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            ReportHistoryItem(
                id = item.optString("id"),
                date = item.optString("date"),
                time = item.optString("time"),
                year = item.optInt("year"),
                month = item.optInt("month"),
                reportedAtMillis = item.optLong("reportedAtMillis"),
                categoryName = item.optString("categoryName"),
                violationType = item.optString("violationType"),
                plate = item.optString("plate"),
                content = item.optString("content"),
                phoneNumber = item.optString("phoneNumber"),
                photoFileNames = item.optString("photoFileNames"),
                photoUrls = item.optString("photoUrls"),
                status = item.optString("status")
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveReportHistory(context: Context, reports: List<ReportHistoryItem>) {
    val array = JSONArray()
    reports.forEach { report ->
        array.put(
            JSONObject()
                .put("id", report.id)
                .put("date", report.date)
                .put("time", report.time)
                .put("year", report.year)
                .put("month", report.month)
                .put("reportedAtMillis", report.reportedAtMillis)
                .put("categoryName", report.categoryName)
                .put("violationType", report.violationType)
                .put("plate", report.plate)
                .put("content", report.content)
                .put("phoneNumber", report.phoneNumber)
                .put("photoFileNames", report.photoFileNames)
                .put("photoUrls", report.photoUrls)
                .put("status", report.status)
        )
    }
    context
        .getSharedPreferences(REPORT_HISTORY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(REPORT_HISTORY_ITEMS, array.toString())
        .apply()
}

// 사용자가 먼저 법령 기준을 고르고, 다음 화면에서 해당 법령의 위반 유형만 보게 합니다.
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
        name = "친환경자동차법",
        description = "친환경차 충전 방해 신고 유형",
        types = listOf("친환경차 충전구역")
    )
)

// 홈 메뉴와 법령 카테고리 카드가 같은 시각 언어를 갖도록 공통 그라데이션 팔레트를 사용합니다.
private val tileGradients = listOf(
    listOf(Color(0xFF3B82F6), Color(0xFF2563EB)),
    listOf(Color(0xFF10B981), Color(0xFF0D9488)),
    listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)),
    listOf(Color(0xFFF59E0B), Color(0xFFF97316)),
    listOf(Color(0xFFEC4899), Color(0xFFDB2777)),
    listOf(Color(0xFF06B6D4), Color(0xFF0891B2))
)

// 주요 버튼, 제목, 본문에 반복되는 색상입니다. 화면마다 색이 흔들리지 않도록 한곳에서 관리합니다.
private val appBackgroundColor = Color(0xFFF8FAFC)
private val appCardColor = Color.White
private val appMutedColor = Color(0xFFF1F5F9)
private val appBorderColor = Color(0xFFE2E8F0)
private val appAccentColor = Color(0xFFDBEAFE)
private val primaryButtonColor = Color(0xFF3B82F6)
private val headingColor = Color(0xFF0F172A)
private val bodyTextColor = Color(0xFF64748B)
private const val REPORT_HISTORY_PREFS = "report_history_prefs"
private const val REPORT_HISTORY_ITEMS = "report_history_items"
private const val REPORT_HISTORY_PAGE_SIZE = 7
private const val USER_PROFILE_PREFS = "user_profile_prefs"
private const val USER_PROFILE_PREFIX = "user_profile_"

// 촬영 가이드는 실제 crop 계산과 같은 비율을 쓰도록 기준값을 공유합니다.
// 국내 번호판의 긴 가로형 비율을 기준으로 하되, 실제 사진 왜곡을 고려해 후보 탐색 범위를 넓게 둡니다.
private const val PLATE_GUIDE_WIDTH_RATIO = 0.82f
private const val PLATE_GUIDE_HEIGHT_RATIO = 0.14f
private const val PLATE_GUIDE_TOP_RATIO = 0.38f
private const val OFFICIAL_PLATE_WIDTH_MM = 520f
private const val OFFICIAL_PLATE_HEIGHT_MM = 110f
private const val OFFICIAL_PLATE_ASPECT_RATIO = OFFICIAL_PLATE_WIDTH_MM / OFFICIAL_PLATE_HEIGHT_MM
private const val OFFICIAL_PLATE_ASPECT_MIN = 2.75f
private const val OFFICIAL_PLATE_ASPECT_MAX = 7.35f
private val CAMERA_CAPTURE_RESOLUTION = Size(1920, 1080)

private fun getConfiguredFirebaseAuth(context: Context): FirebaseAuth? {
    return runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            null
        } else {
            FirebaseAuth.getInstance()
        }
    }.getOrNull()
}

private fun getConfiguredFirebaseFirestore(context: Context): FirebaseFirestore? {
    return runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseFirestore.getInstance()
    }.getOrNull()
}

private fun getConfiguredFirebaseStorage(context: Context): FirebaseStorage? {
    return runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseStorage.getInstance()
    }.getOrNull()
}

private fun signInWithUserId(
    auth: FirebaseAuth?,
    userId: String,
    password: String,
    onResult: (Boolean, String?) -> Unit
) {
    val trimmedUserId = userId.trim()
    if (trimmedUserId.isBlank()) {
        onResult(false, "아이디를 입력해주세요.")
        return
    }
    signInWithEmail(auth, authEmailFromUserId(trimmedUserId), password, onResult)
}

private fun signInWithEmail(
    auth: FirebaseAuth?,
    email: String,
    password: String,
    onResult: (Boolean, String?) -> Unit
) {
    val trimmedEmail = email.trim()
    if (auth == null) {
        onResult(false, "Firebase 설정 파일이 없습니다. app/google-services.json을 추가해주세요.")
        return
    }
    if (trimmedEmail.isBlank() || password.isBlank()) {
        onResult(false, "아이디와 비밀번호를 입력해주세요.")
        return
    }

    auth.signInWithEmailAndPassword(trimmedEmail, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(true, null)
            } else {
                onResult(false, task.exception?.localizedMessage ?: "로그인에 실패했습니다.")
            }
        }
}

private fun createAccountWithEmail(
    auth: FirebaseAuth?,
    email: String,
    password: String,
    onResult: (Boolean, String?) -> Unit
) {
    val trimmedEmail = email.trim()
    if (auth == null) {
        onResult(false, "Firebase 설정 파일이 없습니다. app/google-services.json을 추가해주세요.")
        return
    }
    if (trimmedEmail.isBlank() || password.length < 6) {
        onResult(false, "아이디와 6자리 이상의 비밀번호를 입력해주세요.")
        return
    }

    auth.createUserWithEmailAndPassword(trimmedEmail, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(true, null)
            } else {
                onResult(false, task.exception?.localizedMessage ?: "회원가입에 실패했습니다.")
            }
        }
}

private fun updateFirebasePassword(
    auth: FirebaseAuth?,
    password: String,
    onResult: (String?) -> Unit
) {
    val user = auth?.currentUser
    if (user == null) {
        onResult("로그인된 계정이 없습니다.")
        return
    }
    if (password.length !in 6..12) {
        onResult("새 비밀번호는 6~12자리로 입력해주세요.")
        return
    }

    user.updatePassword(password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult("비밀번호가 변경되었습니다.")
            } else {
                onResult(task.exception?.localizedMessage ?: "비밀번호 변경에 실패했습니다. 다시 로그인한 뒤 시도해주세요.")
            }
        }
}

@Composable
private fun LoginScreen(
    firebaseConfigured: Boolean,
    onGuestClick: () -> Unit,
    onSignInClick: (String, String, (String?) -> Unit) -> Unit,
    onSignUpClick: (String, String, (String?) -> Unit) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(54.dp))
        Text(
            text = "로그인",
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "신고 내역과 사진 업로드를 사용자 계정에 연결합니다.",
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        if (!firebaseConfigured) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E5))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Firebase 설정 필요",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = headingColor
                    )
                    Text(
                        text = "Firebase Console에서 Android 앱을 등록하고 app/google-services.json 파일을 추가하면 실제 로그인이 활성화됩니다.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = bodyTextColor
                    )
                }
            }
        }

        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it.trim().take(12) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("가입한 아이디를 입력해주세요") },
            label = { Text("아이디") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("비밀번호") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        message?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(0xFFB3261E)
            )
        }

        Button(
            onClick = {
                isLoading = true
                message = null
                onSignInClick(userId, password) { resultMessage ->
                    isLoading = false
                    message = resultMessage
                }
            },
            enabled = firebaseConfigured && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(
                    text = "로그인",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Button(
            onClick = {
                isLoading = true
                message = null
                onSignUpClick(userId, password) { resultMessage ->
                    isLoading = false
                    message = resultMessage
                }
            },
            enabled = firebaseConfigured && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF3FF))
        ) {
            Text(
                text = "회원가입",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = primaryButtonColor
            )
        }

        TextButton(
            onClick = onGuestClick,
            enabled = !isLoading,
            modifier = Modifier
        ) {
            Text("비회원으로 계속", color = bodyTextColor, fontWeight = FontWeight.Bold)
        }
    }
}

// 홈에서는 신고 시작 동선을 크게 열고, 최근 3개월 기록을 함께 보여주어 접수 결과로 자연스럽게 돌아오게 합니다.
@Composable
private fun GuestProfileScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        ProfileHomeButton(onClick = onBackClick)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "프로필",
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "계정 정보와 프로필 이미지를 관리합니다.",
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(appMutedColor)
                    .border(3.dp, appBorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(82.dp)) {
                    val strokeWidth = 7.dp.toPx()
                    drawCircle(
                        color = headingColor,
                        radius = size.minDimension * 0.22f,
                        center = Offset(size.width / 2f, size.height * 0.32f),
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        color = headingColor,
                        startAngle = 205f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.14f, size.height * 0.44f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.52f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "비회원입니다.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "비회원은 일부 기능이 제한됩니다.\n로그인을 하여 앱을 사용해주세요.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
        ) {
            Text(
                text = "로그인",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileScreenV2(
    profile: UserProfile,
    joinedAtText: String,
    firebaseConfigured: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: (UserProfile, (String?) -> Unit) -> Unit,
    onPasswordChangeClick: (String, (String?) -> Unit) -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    var userId by remember(profile.email, profile.userId) { mutableStateOf(profile.userId) }
    var phoneNumber by remember(profile.email, profile.phoneNumber) { mutableStateOf(profile.phoneNumber) }
    var birthDate by remember(profile.email, profile.birthDate) { mutableStateOf(profile.birthDate) }
    var profileImagePath by remember(profile.email, profile.profileImagePath) {
        mutableStateOf(profile.profileImagePath)
    }
    var isEditing by remember { mutableStateOf(false) }
    var showPasswordChange by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirm by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isChangingPassword by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val profileBitmap = remember(profileImagePath) {
        profileImagePath.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val savedPath = saveProfileImage(context, uri, profile.authEmail.ifBlank { profile.email })
            if (savedPath != null) {
                profileImagePath = savedPath
                message = "이미지를 선택했습니다. 저장하기를 누르면 반영됩니다."
                isEditing = true
            } else {
                message = "이미지를 불러오지 못했습니다."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        ProfileHomeButton(onClick = onBackClick)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "프로필",
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "계정 정보와 프로필 이미지를 관리합니다.",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(appMutedColor)
                    .border(2.dp, appBorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap.asImageBitmap(),
                        contentDescription = "프로필 이미지",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Canvas(modifier = Modifier.size(58.dp)) {
                        val strokeWidth = 5.dp.toPx()
                        drawCircle(
                            color = headingColor,
                            radius = size.minDimension * 0.22f,
                            center = Offset(size.width / 2f, size.height * 0.32f),
                            style = Stroke(width = strokeWidth)
                        )
                        drawArc(
                            color = headingColor,
                            startAngle = 205f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.14f, size.height * 0.44f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.52f),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("이미지 선택", color = headingColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    profileImagePath = ""
                    isEditing = true
                    message = "프로필 이미지를 제거했습니다. 저장하기를 누르면 반영됩니다."
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF))
            ) {
                Text("이미지 제거", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ProfileInfoRow("아이디", userId.ifBlank { "미입력" })
            ProfileInfoRow("이메일", profile.email.ifBlank { "미입력" })
            ProfileInfoRow("가입일", joinedAtText)
            ProfileInfoRow("전화번호", phoneNumber.ifBlank { "미입력" })
            ProfileInfoRow("생년월일", birthDate.ifBlank { "미입력" })
        }

        message?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = if (it.contains("저장") || it.contains("변경")) primaryButtonColor else Color(0xFFB3261E)
            )
        }

        Button(
            onClick = {
                isEditing = !isEditing
                showPasswordChange = false
                message = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
        ) {
            Text(if (isEditing) "수정 닫기" else "정보 수정", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        if (isEditing) {
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it.trim().take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("아이디", color = Color.Black) },
                placeholder = { Text("5~12자리 글자를 입력해주세요") },
                singleLine = true,
                enabled = !isSaving,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                colors = loginTextFieldColors()
            )
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter { char -> char.isDigit() }.take(11) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("전화번호", color = Color.Black) },
                placeholder = { Text("10~11자리 숫자를 입력해주세요") },
                singleLine = true,
                enabled = !isSaving,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                colors = loginTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = PhoneNumberVisualTransformation
            )
            OutlinedTextField(
                value = birthDate,
                onValueChange = { birthDate = it.filter { char -> char.isDigit() }.take(8) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("생년월일", color = Color.Black) },
                placeholder = { Text("8자리 숫자를 입력해주세요") },
                singleLine = true,
                enabled = !isSaving,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                colors = loginTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = {
                    when {
                        userId.length !in 5..12 -> message = "아이디는 5자리에서 12자리로 입력해주세요."
                        phoneNumber.length < 10 -> message = "전화번호를 10~11자리로 입력해주세요."
                        birthDate.length != 8 -> message = "생년월일 8자리를 입력해주세요."
                        else -> {
                            isSaving = true
                            onSaveClick(
                                profile.copy(
                                    userId = userId,
                                    phoneNumber = phoneNumber,
                                    birthDate = birthDate,
                                    profileImagePath = profileImagePath
                                )
                            ) { resultMessage ->
                                isSaving = false
                                isEditing = false
                                message = resultMessage
                            }
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("저장하기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Button(
            onClick = {
                showPasswordChange = !showPasswordChange
                isEditing = false
                message = null
            },
            enabled = firebaseConfigured && !isChangingPassword,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = appAccentColor)
        ) {
            Text("비밀번호 변경", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryButtonColor)
        }

        if (showPasswordChange) {
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("새 비밀번호", color = Color.Black) },
                placeholder = { Text("6~12자리 글자를 입력해주세요") },
                singleLine = true,
                enabled = !isChangingPassword,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                colors = loginTextFieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedTextField(
                value = newPasswordConfirm,
                onValueChange = { newPasswordConfirm = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("새 비밀번호 확인", color = Color.Black) },
                placeholder = { Text("6~12자리 글자를 다시 입력해주세요") },
                singleLine = true,
                enabled = !isChangingPassword,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                colors = loginTextFieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                onClick = {
                    when {
                        newPassword.length !in 6..12 -> message = "새 비밀번호는 6자리에서 12자리로 입력해주세요."
                        newPassword != newPasswordConfirm -> message = "새 비밀번호가 서로 다릅니다."
                        else -> {
                            isChangingPassword = true
                            onPasswordChangeClick(newPassword) { resultMessage ->
                                isChangingPassword = false
                                message = resultMessage
                                if (resultMessage != null) {
                                    newPassword = ""
                                    newPasswordConfirm = ""
                                    showPasswordChange = false
                                }
                            }
                        }
                    }
                },
                enabled = !isChangingPassword,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
            ) {
                if (isChangingPassword) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("변경하기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Button(
            onClick = onLogoutClick,
            enabled = !isSaving && !isChangingPassword,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = appMutedColor)
        ) {
            Text("로그아웃", color = bodyTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileHomeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(23.dp)) {
            val strokeWidth = 3.dp.toPx()
            val color = headingColor
            drawLine(
                color = color,
                start = Offset(size.width * 0.10f, size.height * 0.48f),
                end = Offset(size.width * 0.50f, size.height * 0.12f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.50f, size.height * 0.12f),
                end = Offset(size.width * 0.90f, size.height * 0.48f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.22f, size.height * 0.44f),
                end = Offset(size.width * 0.22f, size.height * 0.88f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.78f, size.height * 0.44f),
                end = Offset(size.width * 0.78f, size.height * 0.88f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.22f, size.height * 0.88f),
                end = Offset(size.width * 0.38f, size.height * 0.88f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.62f, size.height * 0.88f),
                end = Offset(size.width * 0.78f, size.height * 0.88f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.38f, size.height * 0.88f),
                end = Offset(size.width * 0.38f, size.height * 0.66f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.62f, size.height * 0.66f),
                end = Offset(size.width * 0.62f, size.height * 0.88f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.38f, size.height * 0.66f),
                end = Offset(size.width * 0.62f, size.height * 0.66f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(0.34f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = primaryButtonColor
            )
            Text(
                text = value,
                modifier = Modifier.weight(0.66f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = headingColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(appBorderColor)
        )
    }
}

@Composable
private fun ProfileScreen(
    profile: UserProfile,
    firebaseConfigured: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: (UserProfile, (String?) -> Unit) -> Unit,
    onPasswordChangeClick: (String, (String?) -> Unit) -> Unit
) {
    var userId by remember(profile.email) { mutableStateOf(profile.userId) }
    var phoneNumber by remember(profile.email) { mutableStateOf(profile.phoneNumber) }
    var birthDate by remember(profile.email) { mutableStateOf(profile.birthDate) }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirm by remember { mutableStateOf("") }
    var showPasswordChange by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isChangingPassword by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(42.dp))
        Text(
            text = "프로필",
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "가입할 때 입력한 정보를 확인하고 수정할 수 있습니다.",
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it.trim().take(12) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("아이디", color = Color.Black) },
            placeholder = { Text("5~12자리 글자를 입력해주세요") },
            singleLine = true,
            enabled = !isSaving,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors()
        )
        OutlinedTextField(
            value = profile.email,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("이메일", color = Color.Black) },
            singleLine = true,
            readOnly = true,
            enabled = !isSaving,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors()
        )
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it.filter { char -> char.isDigit() }.take(11) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("전화번호", color = Color.Black) },
            placeholder = { Text("10~11자리 숫자를 입력해주세요") },
            singleLine = true,
            enabled = !isSaving,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = PhoneNumberVisualTransformation
        )
        OutlinedTextField(
            value = birthDate,
            onValueChange = { birthDate = it.filter { char -> char.isDigit() }.take(8) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("생년월일", color = Color.Black) },
            placeholder = { Text("8자리 숫자를 입력해주세요") },
            singleLine = true,
            enabled = !isSaving,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        message?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = if (it.contains("되었습니다")) primaryButtonColor else Color(0xFFB3261E)
            )
        }

        Button(
            onClick = {
                when {
                    userId.length !in 5..12 -> message = "아이디는 5자리에서 12자리로 입력해주세요."
                    phoneNumber.length < 10 -> message = "전화번호를 10~11자리로 입력해주세요."
                    birthDate.length != 8 -> message = "생년월일 8자리를 입력해주세요."
                    else -> {
                        isSaving = true
                        onSaveClick(
                            profile.copy(
                                userId = userId,
                                phoneNumber = phoneNumber,
                                birthDate = birthDate
                            )
                        ) { resultMessage ->
                            isSaving = false
                            message = resultMessage
                        }
                    }
                }
            },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
        ) {
            Text("저장하기", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Button(
            onClick = {
                showPasswordChange = !showPasswordChange
                message = null
            },
            enabled = firebaseConfigured && !isChangingPassword,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = appAccentColor)
        ) {
            Text("비밀번호 변경", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryButtonColor)
        }

        if (showPasswordChange) {
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("새 비밀번호", color = Color.Black) },
                placeholder = { Text("6~12자리 글자를 입력해주세요") },
                singleLine = true,
                enabled = !isChangingPassword,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                colors = loginTextFieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedTextField(
                value = newPasswordConfirm,
                onValueChange = { newPasswordConfirm = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("새 비밀번호 확인", color = Color.Black) },
                placeholder = { Text("6~12자리 글자를 다시 입력해주세요") },
                singleLine = true,
                enabled = !isChangingPassword,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                colors = loginTextFieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                onClick = {
                    when {
                        newPassword.length !in 6..12 -> message = "새 비밀번호는 6자리에서 12자리로 입력해주세요."
                        newPassword != newPasswordConfirm -> message = "새 비밀번호가 서로 다릅니다."
                        else -> {
                            isChangingPassword = true
                            onPasswordChangeClick(newPassword) { resultMessage ->
                                isChangingPassword = false
                                message = resultMessage
                                if (resultMessage?.contains("되었습니다") == true) {
                                    newPassword = ""
                                    newPasswordConfirm = ""
                                    showPasswordChange = false
                                }
                            }
                        }
                    }
                },
                enabled = !isChangingPassword,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
            ) {
                if (isChangingPassword) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("변경하기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        TextButton(
            onClick = onBackClick,
            enabled = !isSaving && !isChangingPassword,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("홈으로 돌아가기", color = bodyTextColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SignUpScreenV2(
    firebaseConfigured: Boolean,
    onBackClick: () -> Unit,
    onSignUpClick: (UserProfile, String, (String?) -> Unit) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(42.dp))
        Text(
            text = "회원가입",
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "형식에 맞게 알맞은 정보를 입력해주세요.",
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it.trim().take(12) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("아이디", color = Color.Black) },
            placeholder = { Text("5~12자리 글자를 입력해주세요") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(12) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("비밀번호", color = Color.Black) },
            placeholder = { Text("6~12자리 글자를 입력해주세요") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = passwordConfirm,
            onValueChange = { passwordConfirm = it.take(12) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("비밀번호 확인", color = Color.Black) },
            placeholder = { Text("6~12자리 글자를 다시 입력해주세요") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(30) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("이메일", color = Color.Black) },
            placeholder = { Text("30자리 이내 이메일을 입력해주세요") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it.filter { char -> char.isDigit() }.take(11) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("전화번호", color = Color.Black) },
            placeholder = { Text("10~11자리 숫자를 입력해주세요") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = PhoneNumberVisualTransformation
        )
        OutlinedTextField(
            value = birthDate,
            onValueChange = { birthDate = it.filter { char -> char.isDigit() }.take(8) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("생년월일", color = Color.Black) },
            placeholder = { Text("8자리 숫자를 입력해주세요") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        message?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(0xFFB3261E)
            )
        }

        Button(
            onClick = {
                when {
                    userId.length !in 5..12 -> message = "아이디는 5자리에서 12자리로 입력해주세요."
                    password.length !in 6..12 -> message = "비밀번호는 6자리에서 12자리로 입력해주세요."
                    passwordConfirm.length !in 6..12 -> message = "비밀번호 확인은 6자리에서 12자리로 입력해주세요."
                    password != passwordConfirm -> message = "비밀번호가 서로 다릅니다."
                    email.isBlank() || email.length > 30 || !email.contains("@") -> message = "이메일은 30자리 이내의 올바른 형식으로 입력해주세요."
                    phoneNumber.length < 10 -> message = "전화번호를 입력해주세요."
                    birthDate.length != 8 -> message = "생년월일 8자리를 입력해주세요."
                    else -> {
                        isLoading = true
                        message = null
                        val profile = UserProfile(
                            userId = userId,
                            email = email.trim(),
                            phoneNumber = phoneNumber,
                            birthDate = birthDate
                        )
                        onSignUpClick(profile, password) { resultMessage ->
                            isLoading = false
                            message = resultMessage
                        }
                    }
                }
            },
            enabled = firebaseConfigured && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(
                    text = "가입하기",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        TextButton(
            onClick = onBackClick,
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("로그인으로 돌아가기", color = bodyTextColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SignUpScreen(
    firebaseConfigured: Boolean,
    onBackClick: () -> Unit,
    onSignUpClick: (String, String, (String?) -> Unit) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(42.dp))
        Text(
            text = "회원가입",
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "신고 계정에 사용할 정보를 입력해주세요.",
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it.trim().take(24) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("아이디") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("비밀번호") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = passwordConfirm,
            onValueChange = { passwordConfirm = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("비밀번호 확인") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("이메일") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it.filter { char -> char.isDigit() }.take(11) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("전화번호") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = PhoneNumberVisualTransformation
        )
        OutlinedTextField(
            value = birthDate,
            onValueChange = { birthDate = it.filter { char -> char.isDigit() }.take(8) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("생년월일") },
            placeholder = { Text("YYYYMMDD") },
            singleLine = true,
            enabled = !isLoading && firebaseConfigured,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = loginTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        message?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(0xFFB3261E)
            )
        }

        Button(
            onClick = {
                when {
                    userId.isBlank() -> message = "아이디를 입력해주세요."
                    password.length < 6 -> message = "비밀번호는 6자리 이상이어야 합니다."
                    password != passwordConfirm -> message = "비밀번호가 서로 다릅니다."
                    email.isBlank() -> message = "이메일을 입력해주세요."
                    phoneNumber.length < 10 -> message = "전화번호를 입력해주세요."
                    birthDate.length != 8 -> message = "생년월일 8자리를 입력해주세요."
                    else -> {
                        isLoading = true
                        message = null
                        onSignUpClick(email, password) { resultMessage ->
                            isLoading = false
                            message = resultMessage
                        }
                    }
                }
            },
            enabled = firebaseConfigured && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(
                    text = "가입하기",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        TextButton(
            onClick = onBackClick,
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("로그인으로 돌아가기", color = bodyTextColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun loginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    disabledTextColor = Color(0xFF94A3B8),
    cursorColor = primaryButtonColor,
    focusedBorderColor = primaryButtonColor,
    unfocusedBorderColor = appBorderColor,
    focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color.Black,
    focusedPlaceholderColor = Color(0xFF94A3B8),
    unfocusedPlaceholderColor = Color(0xFF94A3B8)
)

@Composable
private fun HomeScreen(
    reports: List<ReportHistoryItem>,
    profileImagePath: String,
    onReportClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onReportSelected: (ReportHistoryItem) -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    val threeMonthsAgo = Calendar.getInstance(Locale.KOREA).apply {
        add(Calendar.MONTH, -3)
    }.timeInMillis
    val recentThreeMonthReports = reports
        .filter { report -> report.reportedAtMillis >= threeMonthsAgo }
        .sortedByDescending { it.reportedAtMillis }
    val recentThreeMonthSlots = recentThreeMonthReports
        .take(3)
        .map<ReportHistoryItem, ReportHistoryItem?> { it } +
        List((3 - recentThreeMonthReports.size).coerceAtLeast(0)) { null }
    val profileBitmap = remember(profileImagePath) {
        profileImagePath.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "불법 주정차 신고",
                modifier = Modifier.weight(1f),
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = headingColor
            )
            HomeProfileAvatar(
                bitmap = profileBitmap,
                onClick = onProfileClick
            )
        }
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
                title = "마이페이지",
                subtitle = "신고 기준",
                gradient = tileGradients[3],
                onClick = {
                    Toast.makeText(context, "도움말 화면은 다음 단계에서 추가됩니다.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        RecentThreeMonthHistorySection(
            reports = recentThreeMonthSlots,
            onReportSelected = onReportSelected
        )
    }
}

@Composable
private fun HomeProfileAvatar(bitmap: Bitmap?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(appMutedColor)
            .border(2.dp, appBorderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "프로필 이미지",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Canvas(modifier = Modifier.size(30.dp)) {
                val strokeWidth = 3.dp.toPx()
                drawCircle(
                    color = headingColor,
                    radius = size.minDimension * 0.22f,
                    center = Offset(size.width / 2f, size.height * 0.32f),
                    style = Stroke(width = strokeWidth)
                )
                drawArc(
                    color = headingColor,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.14f, size.height * 0.44f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.52f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}

// 홈 하단 요약 카드입니다. 기록이 적어도 화면 높이가 갑자기 변하지 않도록 빈 행을 채웁니다.
@Composable
private fun RecentThreeMonthHistorySection(
    reports: List<ReportHistoryItem?>,
    onReportSelected: (ReportHistoryItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = appMutedColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "최근 3개월간 신고내역",
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = headingColor
                    )
                    Text(
                        text = "최근 접수 기준 3건",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = bodyTextColor
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "3개월",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryButtonColor
                    )
                }
            }

            reports.forEach { report ->
                RecentHistoryRow(
                    report = report,
                    onClick = { selectedReport -> onReportSelected(selectedReport) }
                )
            }
        }
    }
}

// 신고내역 전체 조회 화면입니다. 기간 필터, 정렬, 페이지 크기를 조합해 많은 기록도 화면 단위로 확인합니다.
@Composable
private fun ReportHistoryScreen(
    reports: List<ReportHistoryItem>,
    onReportSelected: (ReportHistoryItem) -> Unit,
    onBackClick: () -> Unit
) {
    val currentYear = SimpleDateFormat("yyyy", Locale.KOREA).format(System.currentTimeMillis()).toInt()
    val currentMonth = SimpleDateFormat("MM", Locale.KOREA).format(System.currentTimeMillis()).toInt()
    var selectedYear by remember { mutableStateOf(currentYear) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }
    var newestFirst by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(0) }
    val monthRangeOptions = (currentYear downTo currentYear - 1).flatMap { year ->
        (12 downTo 1).map { month -> year to month }
    }
    val filteredReports = reports.filter { report ->
        report.year == selectedYear && report.month == selectedMonth
    }
    val sortedFilteredReports = if (newestFirst) {
        filteredReports.sortedByDescending { it.reportedAtMillis }
    } else {
        filteredReports.sortedBy { it.reportedAtMillis }
    }
    // 필터나 정렬이 바뀌어 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정합니다.
    val totalPages = ((sortedFilteredReports.size + REPORT_HISTORY_PAGE_SIZE - 1) / REPORT_HISTORY_PAGE_SIZE)
        .coerceAtLeast(1)
    if (currentPage >= totalPages) {
        currentPage = totalPages - 1
    }
    val pagedReports = sortedFilteredReports
        .drop(currentPage * REPORT_HISTORY_PAGE_SIZE)
        .take(REPORT_HISTORY_PAGE_SIZE)
    val hasPreviousPage = currentPage > 0
    val hasNextPage = currentPage < totalPages - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = appMutedColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "상세 내역",
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = headingColor
                    )
                    Text(
                        text = "기간별 신고 상태 확인",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = bodyTextColor
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DateRangeFilterButton(
                        modifier = Modifier.weight(1.2f),
                        label = monthRangeLabel(selectedYear, selectedMonth),
                        options = monthRangeOptions,
                        visibleMenuItems = 4,
                        onOptionSelected = { year, month ->
                            selectedYear = year
                            selectedMonth = month
                            currentPage = 0
                        }
                    )
                    Row(
                        modifier = Modifier.weight(1.05f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SortChip(
                            text = "최신순",
                            selected = newestFirst,
                            onClick = {
                                newestFirst = true
                                currentPage = 0
                            }
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        SortChip(
                            text = "오래된순",
                            selected = !newestFirst,
                            onClick = {
                                newestFirst = false
                                currentPage = 0
                            }
                        )
                    }
                }

                if (sortedFilteredReports.isEmpty()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 34.dp),
                        text = "선택한 기간의 신고 내역이 없습니다",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = bodyTextColor,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pagedReports.forEach { report ->
                            ReportHistoryRow(
                                report = report,
                                onClick = { selectedReport -> onReportSelected(selectedReport) }
                            )
                        }
                    }
                }

                if (sortedFilteredReports.size > REPORT_HISTORY_PAGE_SIZE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HistoryPageControls(
                        hasPreviousPage = hasPreviousPage,
                        hasNextPage = hasNextPage,
                        onPreviousClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                        onNextClick = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) }
                    )
                }
            }
        }

        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = "홈으로 돌아가기",
            onClick = onBackClick
        )
    }
}

@Composable
private fun HistoryPageControls(
    hasPreviousPage: Boolean,
    hasNextPage: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hasPreviousPage) {
            Button(
                modifier = Modifier.weight(1f).height(44.dp),
                onClick = onPreviousClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryButtonColor,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "이전 페이지",
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (hasNextPage) {
            Button(
                modifier = Modifier.weight(1f).height(44.dp),
                onClick = onNextClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryButtonColor,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "다음 페이지",
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RecentHistoryRow(
    report: ReportHistoryItem?,
    onClick: (ReportHistoryItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .then(if (report == null) Modifier else Modifier.clickable { onClick(report) }),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (report == null) {
                Text(
                    text = "신고 기록 없음",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = bodyTextColor.copy(alpha = 0.72f)
                )
                Text(
                    text = "--------",
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = bodyTextColor.copy(alpha = 0.72f)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = report.violationType,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = headingColor
                    )
                    Text(
                        text = report.date,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = bodyTextColor
                    )
                }
                Box(
                    modifier = Modifier
                        .background(appAccentColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = report.status,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryButtonColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) primaryButtonColor else Color.White,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else bodyTextColor
        )
    }
}

@Composable
private fun DateRangeFilterButton(
    modifier: Modifier = Modifier,
    label: String,
    options: List<Pair<Int, Int>>,
    visibleMenuItems: Int,
    onOptionSelected: (year: Int, month: Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = headingColor
            )
        ) {
            Text(
                text = "$label >",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.height((visibleMenuItems * 48 + 16).dp)
        ) {
            options.forEach { (year, month) ->
                DropdownMenuItem(
                    text = { Text(text = monthRangeLabel(year, month)) },
                    onClick = {
                        expanded = false
                        onOptionSelected(year, month)
                    }
                )
            }
        }
    }
}

private fun monthRangeLabel(year: Int, month: Int): String {
    val calendar = Calendar.getInstance(Locale.KOREA)
    calendar.set(Calendar.YEAR, year)
    calendar.set(Calendar.MONTH, month - 1)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return String.format(
        Locale.KOREA,
        "%02d.%02d.01~%02d.%02d.%02d",
        year % 100,
        month,
        year % 100,
        month,
        lastDay
    )
}

@Composable
private fun HistoryFilterButton(
    modifier: Modifier = Modifier,
    label: String,
    options: List<String>,
    visibleMenuItems: Int? = null,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val menuModifier = visibleMenuItems?.let { count ->
        Modifier.height((count * 48 + 16).dp)
    } ?: Modifier

    Box(modifier = modifier) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = headingColor
            )
        ) {
            Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = menuModifier
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun ReportHistoryRow(
    report: ReportHistoryItem?,
    onClick: (ReportHistoryItem) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (report == null) Modifier else Modifier.clickable { onClick(report) }),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = report?.date ?: "-",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = bodyTextColor
                )
                Text(
                    text = if (report == null) "-" else "${report.violationType} 쨌 ${report.plate}",
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (report == null) bodyTextColor else headingColor
                )
            }
            Text(
                text = report?.status ?: "-",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (report == null) bodyTextColor else primaryButtonColor
            )
        }
    }
}

@Composable
private fun ReportHistoryDetailScreen(
    report: ReportHistoryItem,
    onBackClick: () -> Unit
) {
    // 차량 사진은 여러 장을 "file1, file2" 형태로 저장하므로 상세 화면에서 파일명 배열로 다시 분리합니다.
    val photoFileNames = report.photoFileNames
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "신고 상세 내역",
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )
        Text(
            text = "${report.date} ${report.time} 접수",
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = appMutedColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "위반 내역",
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = headingColor
                )
                ReportInfoRow(label = "처리 상태", value = report.status)
                ReportInfoRow(label = "법령", value = report.categoryName.ifBlank { "미입력" })
                ReportInfoRow(label = "위반 유형", value = report.violationType)
                ReportInfoRow(label = "번호판", value = report.plate)
                ReportInfoRow(label = "연락처", value = formatPhoneNumber(report.phoneNumber))
                ReportInfoRow(label = "신고 내용", value = report.content.ifBlank { "입력한 내용 없음" })
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = appMutedColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "저장된 차량 사진",
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = headingColor
                )
                if (photoFileNames.isEmpty()) {
                    EmptyPhotoBox(message = "저장된 사진 파일이 없습니다")
                } else {
                    photoFileNames.forEachIndexed { index, fileName ->
                        SavedVehiclePhotoPreview(
                            title = "차량 사진 ${index + 1}",
                            fileName = fileName
                        )
                    }
                }
            }
        }

        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = "신고내역으로 돌아가기",
            onClick = onBackClick
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ReportInfoRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = bodyTextColor
        )
        Text(
            text = value,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            color = headingColor
        )
    }
}

@Composable
private fun SavedVehiclePhotoPreview(
    title: String,
    fileName: String
) {
    val context = LocalContext.current
    // 신고 기록에는 파일명만 남기고, 실제 이미지는 촬영 당시 cacheDir에 저장된 파일을 읽어 표시합니다.
    val bitmap = remember(fileName) {
        BitmapFactory.decodeFile(File(context.cacheDir, fileName).absolutePath)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$title 쨌 $fileName",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
            color = bodyTextColor
        )
        if (bitmap == null) {
            EmptyPhotoBox(message = "사진 파일을 찾을 수 없습니다")
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun EmptyPhotoBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatPhoneNumber(phoneNumber: String): String {
    return if (phoneNumber.length == 11) {
        "${phoneNumber.substring(0, 3)}-${phoneNumber.substring(3, 7)}-${phoneNumber.substring(7)}"
    } else {
        phoneNumber.ifBlank { "미입력" }
    }
}

// 메인 메뉴는 법령 선택처럼 같은 카드 패턴으로 보여주어 사용자가 선택 가능한 항목을 한눈에 파악하게 합니다.
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

// 주요 행동 버튼의 크기와 색을 통일해 이전, 다음, 완료 같은 이동 버튼을 같은 우선순위로 보이게 합니다.
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

// 법령 카테고리를 먼저 고르게 해서 이후 세부 위반 유형 목록이 불필요하게 길어지지 않게 합니다.
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
            text = "먼저 신고 기준이 되는 법령 카테고리를 선택하세요",
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

// 사용자가 선택한 법령 안에서만 세부 위반 유형을 고르게 해 잘못된 조합으로 신고하지 않도록 합니다.
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
            text = "위반 유형을 선택하면 차량 촬영 화면으로 이동합니다.",
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

// 카메라 권한이 거부된 상태에서 촬영 화면으로 넘어가지 않도록 권한 요청 동선을 분리합니다.
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

// 번호판 인식을 위한 촬영 단계입니다. 화면 가이드 영역을 우선 crop하고 OCR 후보를 만든 뒤 사용자가 다음 화면에서 수정할 수 있게 합니다.
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
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
            .setTargetResolution(CAMERA_CAPTURE_RESOLUTION)
            .build()
    }

    var isProcessing by remember { mutableStateOf(false) }
    val captureMessage = "테두리 안에 번호판을 넣어 찍어주세요"

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
                    val previewView = PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)

                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .setTargetResolution(CAMERA_CAPTURE_RESOLUTION)
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            previewView.post {
                                val viewPort = ViewPort.Builder(
                                    Rational(previewView.width.coerceAtLeast(1), previewView.height.coerceAtLeast(1)),
                                    previewView.display.rotation
                                )
                                    .setScaleType(ViewPort.FILL_CENTER)
                                    .build()
                                val useCaseGroup = UseCaseGroup.Builder()
                                    .setViewPort(viewPort)
                                    .addUseCase(preview)
                                    .addUseCase(imageCapture)
                                    .build()

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    useCaseGroup
                                )
                            }
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

// 신고 증빙용 차량 사진은 OCR이 필요 없으므로 파일 저장과 첨부 기록만 처리합니다.
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
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
            .setTargetResolution(CAMERA_CAPTURE_RESOLUTION)
            .build()
    }

    var isProcessing by remember { mutableStateOf(false) }
    var firstPhotoFileName by remember { mutableStateOf("") }
    val captureMessage = if (firstPhotoFileName.isBlank()) {
        "차량 사진 총 2장을 찍어주세요"
    } else {
        "한 번 더 촬영해주세요"
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
                    val previewView = PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)

                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .setTargetResolution(CAMERA_CAPTURE_RESOLUTION)
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            previewView.post {
                                val viewPort = ViewPort.Builder(
                                    Rational(previewView.width.coerceAtLeast(1), previewView.height.coerceAtLeast(1)),
                                    previewView.display.rotation
                                )
                                    .setScaleType(ViewPort.FILL_CENTER)
                                    .build()
                                val useCaseGroup = UseCaseGroup.Builder()
                                    .setViewPort(viewPort)
                                    .addUseCase(preview)
                                    .addUseCase(imageCapture)
                                    .build()

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    useCaseGroup
                                )
                            }
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

// 촬영 중 사용자가 지금 해야 할 행동을 짧게 안내하는 공통 배너입니다.
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

// 화면 가이드 비율과 실제 crop 비율을 맞춰 OCR 대상 영역이 사용자의 기대와 어긋나지 않게 합니다.
@Composable
private fun PlateGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val guideWidth = size.width * PLATE_GUIDE_WIDTH_RATIO
        val guideHeight = size.height * PLATE_GUIDE_HEIGHT_RATIO
        val left = (size.width - guideWidth) / 2f
        val top = size.height * PLATE_GUIDE_TOP_RATIO
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

// 다음 단계로 넘어가기 전에 빈 입력이나 형식 오류를 화면 상단에서 즉시 알려줍니다.
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

// OCR 결과를 바로 제출하지 않고, 번호판 crop과 원문 OCR을 보여준 뒤 사용자가 직접 수정할 수 있게 합니다.
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
            text = "다시 한번 정확한지 확인해주세요",
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
                    .border(1.dp, appBorderColor, RoundedCornerShape(8.dp))
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

// 제출 직전 단계입니다. 촬영한 첨부 파일명과 OCR 번호판을 확인하고 신고 내용과 연락처를 함께 수집합니다.
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
    val inputColors = reportDetailTextFieldColors()

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
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = inputColors
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = photoFileName,
            onValueChange = {},
            label = { Text(text = "첨부 사진") },
            singleLine = true,
            readOnly = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = inputColors
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = plate,
            onValueChange = onPlateChange,
            label = { Text(text = "번호판 확인/수정") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = inputColors
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
            placeholder = { Text(text = "불법 주정차 위반 사항을 신고해주세요") },
            minLines = 5,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
            colors = inputColors
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = { Text(text = "내 전화") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PhoneNumberVisualTransformation,
            placeholder = { Text(text = "010 - 1234 - 5678") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            colors = inputColors
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
                        warningMessage = "알맞은 형식으로 입력해주세요"
                    } else {
                        warningMessage = ""
                        onNextClick()
                    }
                }
            )
        }
    }
}

@Composable
private fun reportDetailTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedLabelColor = primaryButtonColor,
    unfocusedLabelColor = bodyTextColor,
    focusedPlaceholderColor = Color(0xFF94A3B8),
    unfocusedPlaceholderColor = Color(0xFF94A3B8),
    focusedBorderColor = primaryButtonColor,
    unfocusedBorderColor = appBorderColor,
    cursorColor = primaryButtonColor,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
)

// 실제 접수 기록을 만들기 전에 허위 신고 책임을 마지막으로 확인시키는 단계입니다.
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
            text = "한 번 더 확인해주세요",
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "허위 신고는 무고죄 또는 공무집행방해죄로 법적 처벌을 받을 수 있습니다",
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

// 접수 완료 후 사용자가 다음에 어디서 확인하면 되는지 안내하고 홈으로 돌아가도록 마무리합니다.
@Composable
private fun ReportCompleteScreen(
    onHomeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(112.dp)) {
            val strokeWidth = 9.dp.toPx()
            drawCircle(
                color = Color(0xFF21A8F3),
                style = Stroke(width = strokeWidth)
            )
            drawLine(
                color = Color(0xFF21A8F3),
                start = Offset(size.width * 0.28f, size.height * 0.54f),
                end = Offset(size.width * 0.43f, size.height * 0.70f),
                strokeWidth = 13.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF21A8F3),
                start = Offset(size.width * 0.43f, size.height * 0.70f),
                end = Offset(size.width * 0.76f, size.height * 0.30f),
                strokeWidth = 13.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "신고가 완료되었습니다",
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = headingColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "최종 처리는 7일에서 14일 정도 소요됩니다.\n신고 내역은 홈 > 신고내역 페이지에서 확인할 수 있습니다",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            color = bodyTextColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            text = "홈으로 돌아가기",
            onClick = onHomeClick
        )
    }
}

// 번호판 OCR은 원본 사진을 cacheDir에 저장하고, 저장된 파일을 기준으로 crop/OCR 파이프라인을 시작합니다.
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

// 차량 증빙 사진은 OCR이 필요 없으므로 파일 저장 후 파일명만 신고 입력 상태에 반영합니다.
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

// 저장된 사진을 실제 방향으로 보정한 뒤 가이드 crop과 OpenCV 후보 crop을 모두 OCR 후보로 평가합니다.
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

// crop 방식과 전처리 방식마다 OCR 결과가 달라질 수 있어 여러 후보를 평가하고 가장 번호판다운 결과를 고릅니다.
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
            val score = scoreOcrCandidate(ocrText, normalizedText, plate) - estimatePreprocessingCandidatePenalty(bitmap)

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
                    score = scoreOcrCandidate(segmentedPlate, segmentedPlate, segmentedPlate) + 70 -
                        estimatePreprocessingCandidatePenalty(bitmap)
                )
            }
        }.onFailure { exception ->
            lastFailure = exception
        }
    }

    // 정규 번호판 형식이 하나라도 있으면 숫자만 많은 fallback보다 우선합니다.
    val strictCandidates = candidateResults.filter { result ->
        isStrictKoreanPlate(result.plateText)
    }
    val consensusPlate = buildPlateConsensus(strictCandidates)
    if (consensusPlate != null) {
        val selectedCandidate = strictCandidates
            .filter { it.plateText == consensusPlate }
            .maxByOrNull { it.score }
            ?: strictCandidates.maxByOrNull { it.score }

        if (selectedCandidate != null) {
            val cropFilePath = saveDebugPlateCrop(context, selectedCandidate.bitmap, fileName)
            onResult(selectedCandidate.ocrText, consensusPlate, cropFilePath)
            return
        }
    }

    strictCandidates.maxByOrNull { it.score }?.let { selectedCandidate ->
        val cropFilePath = saveDebugPlateCrop(context, selectedCandidate.bitmap, fileName)
        onResult(selectedCandidate.ocrText, selectedCandidate.plateText, cropFilePath)
        return
    }

    onError("번호판 형식으로 인식하지 못했습니다. 번호판이 선명하게 보이도록 다시 촬영해주세요.")
}

// 정규식에 맞지 않아도 사용자가 확인 화면에서 고치기 쉬운 후보를 남기기 위한 점수입니다.
private fun scoreOcrFallback(text: String): Int {
    if (text.isBlank()) return Int.MIN_VALUE

    var score = 0
    if (text.any { it in '가'..'힣' }) score += 40
    if (text.length in 7..8) score += 30
    score += text.length.coerceAtMost(8)
    score -= kotlin.math.abs(8 - text.length) * 2
    return score
}

// 여러 OCR 후보를 비교할 수 있도록 crop 이미지, 원문, 정규화 결과, 점수를 함께 담습니다.
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
    if (isStrictKoreanPlate(plateText)) score += 1000
    if (plateText.isNotBlank()) score += 220
    if (text.any { isKoreanPlateChar(it) }) score += 40
    if (text.length == 8) score += 36
    if (text.length == 7) score += 28
    if (text.length in 7..8 && text.takeLast(4).all { it.isDigit() }) score += 45
    if (text.length == 7 && text.take(2).all { it.isDigit() } && isKoreanPlateChar(text[2])) score += 70
    if (text.length == 8 && text.take(3).all { it.isDigit() } && isKoreanPlateChar(text[3])) score += 70
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

private fun isStrictKoreanPlate(text: String): Boolean {
    return Regex("^[0-9]{2,3}[가-힣][0-9]{4}$").matches(text)
}

// 너무 어둡거나 가장자리에 장식/노이즈가 많은 crop은 번호판 후보 점수에서 감점합니다.
private fun estimatePreprocessingCandidatePenalty(bitmap: Bitmap): Int {
    return estimateBinaryCandidatePenalty(bitmap) +
        estimateSideArtifactPenalty(bitmap) +
        estimateLeftDecorationPenalty(bitmap)
}

private fun estimateBinaryCandidatePenalty(bitmap: Bitmap): Int {
    val stepX = (bitmap.width / 96).coerceAtLeast(1)
    val stepY = (bitmap.height / 32).coerceAtLeast(1)
    var total = 0
    var extreme = 0
    var dark = 0

    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val luma = pixelLuma(bitmap.getPixel(x, y))
            if (luma < 28 || luma > 227) extreme++
            if (luma < 70) dark++
            total++
        }
    }

    if (total == 0) return 0
    val extremeRatio = extreme.toFloat() / total
    val darkRatio = dark.toFloat() / total
    return when {
        darkRatio > 0.46f -> 90
        darkRatio > 0.36f -> 40
        extremeRatio > 0.96f && darkRatio < 0.04f -> 35
        else -> 0
    }
}

private fun estimateSideArtifactPenalty(bitmap: Bitmap): Int {
    val edgeWidth = (bitmap.width * 0.08f).toInt().coerceAtLeast(1)
    val leftDarkRatio = estimateDarkRatio(bitmap, 0, edgeWidth)
    val rightDarkRatio = estimateDarkRatio(bitmap, bitmap.width - edgeWidth, bitmap.width)
    val sideDarkRatio = maxOf(leftDarkRatio, rightDarkRatio)

    return when {
        sideDarkRatio > 0.72f -> 120
        sideDarkRatio > 0.58f -> 70
        sideDarkRatio > 0.44f -> 35
        else -> 0
    }
}

private fun estimateLeftDecorationPenalty(bitmap: Bitmap): Int {
    val leftWidth = (bitmap.width * 0.18f).toInt().coerceAtLeast(1)
    val centerStart = (bitmap.width * 0.36f).toInt().coerceIn(0, bitmap.width - 1)
    val centerEnd = (bitmap.width * 0.64f).toInt().coerceIn(centerStart + 1, bitmap.width)
    val leftDarkRatio = estimateDarkRatio(bitmap, 0, leftWidth)
    val centerDarkRatio = estimateDarkRatio(bitmap, centerStart, centerEnd)

    return when {
        leftDarkRatio > 0.34f && leftDarkRatio > centerDarkRatio * 1.45f -> 130
        leftDarkRatio > 0.25f && leftDarkRatio > centerDarkRatio * 1.25f -> 75
        leftDarkRatio > 0.18f && leftDarkRatio > centerDarkRatio * 1.10f -> 35
        else -> 0
    }
}

private fun estimateDarkRatio(bitmap: Bitmap, startX: Int, endX: Int): Float {
    val safeStartX = startX.coerceIn(0, bitmap.width - 1)
    val safeEndX = endX.coerceIn(safeStartX + 1, bitmap.width)
    val stepX = ((safeEndX - safeStartX) / 16).coerceAtLeast(1)
    val stepY = (bitmap.height / 32).coerceAtLeast(1)
    var dark = 0
    var total = 0

    for (y in 0 until bitmap.height step stepY) {
        for (x in safeStartX until safeEndX step stepX) {
            if (pixelLuma(bitmap.getPixel(x, y)) < 78) dark++
            total++
        }
    }

    return dark.toFloat() / total.coerceAtLeast(1)
}

// 여러 전처리 후보가 같은 번호판을 가리킬 때 점수와 빈도를 함께 봐서 최종 문자열을 안정화합니다.
private fun buildPlateConsensus(results: List<OcrCandidateResult>): String? {
    if (results.isEmpty()) return null

    val minScore = results.minOf { it.score }
    val targetLength = results
        .groupBy { it.plateText.length }
        .maxByOrNull { (_, sameLengthResults) ->
            sameLengthResults.sumOf { result -> plateVoteWeight(result, minScore) }
        }
        ?.key
        ?: return null

    val sameLengthResults = results.filter { it.plateText.length == targetLength }
    if (sameLengthResults.isEmpty()) return null

    val groupedResults = sameLengthResults.groupBy { it.plateText }
    val plateWeights = groupedResults.mapValues { (_, group) ->
        group.size * 3 + group.maxOf { result -> plateVoteWeight(result, minScore) }
    }

    val result = StringBuilder()
    for (index in 0 until targetLength) {
        val selected = plateWeights
            .asSequence()
            .mapNotNull { (plate, weight) ->
                val char = plate.getOrNull(index) ?: return@mapNotNull null
                val allowed = if (isKoreanPlatePosition(targetLength, index)) {
                    isKoreanPlateChar(char)
                } else {
                    char.isDigit()
                }
                if (allowed) char to weight else null
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, weights) -> weights.sum() }
            .maxWithOrNull(compareBy<Map.Entry<Char, Int>> { it.value }.thenBy { it.key })
            ?.key
            ?: return null

        result.append(selected)
    }

    val votedPlate = applyLastFourDigitVote(
        basePlate = result.toString(),
        plateWeights = plateWeights
    )

    return votedPlate.takeIf { findKoreanPlateCandidate(it) == it }
}

private fun plateVoteWeight(result: OcrCandidateResult, minScore: Int): Int {
    val normalizedScore = (result.score - minScore).coerceAtLeast(0)
    return (normalizedScore / 45 + 1).coerceIn(1, 18)
}

private fun applyLastFourDigitVote(
    basePlate: String,
    plateWeights: Map<String, Int>
): String {
    if (!isStrictKoreanPlate(basePlate)) return basePlate

    val suffixWeights = plateWeights
        .filterKeys { plate -> plate.length == basePlate.length && isStrictKoreanPlate(plate) }
        .entries
        .groupBy({ entry -> entry.key.takeLast(4) }, { entry -> entry.value })
        .mapValues { (_, weights) -> weights.sum() }

    val bestSuffix = suffixWeights.maxWithOrNull(
        compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
    )?.key ?: return basePlate

    return basePlate.dropLast(4) + bestSuffix
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

// 번호판의 글자 덩어리별로 어두운 묶음을 찾아 단일 글자 OCR 입력으로 사용할 작은 이미지를 만듭니다.
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

// 사용자가 화면에서 맞춘 흰색 가이드 위치와 같은 중앙 영역을 원본 사진에서 잘라냅니다.
private fun cropPlateGuideArea(bitmap: Bitmap): Bitmap {
    val cropWidth = (bitmap.width * PLATE_GUIDE_WIDTH_RATIO).toInt().coerceAtLeast(1)
    val cropHeight = (bitmap.height * PLATE_GUIDE_HEIGHT_RATIO).toInt().coerceAtLeast(1)
    val left = ((bitmap.width - cropWidth) / 2).coerceIn(0, bitmap.width - 1)
    val top = (bitmap.height * PLATE_GUIDE_TOP_RATIO).toInt().coerceIn(0, bitmap.height - 1)
    val safeWidth = cropWidth.coerceAtMost(bitmap.width - left)
    val safeHeight = cropHeight.coerceAtMost(bitmap.height - top).coerceAtLeast(1)

    return Bitmap.createBitmap(bitmap, left, top, safeWidth, safeHeight)
}

// 가이드 crop이 빗나간 경우를 보완하기 위해 사진 전체에서 번호판 비율에 가까운 사각 후보를 추가로 찾습니다.
private fun cropBrightPlateAreaInsideGuide(bitmap: Bitmap): Bitmap {
    if (bitmap.width <= 2 || bitmap.height <= 2) return bitmap

    val brightThreshold = 135
    val rowThreshold = (bitmap.width * 0.18f).toInt().coerceAtLeast(4)
    val rowRuns = mutableListOf<IntRange>()
    var rowStart = -1

    for (y in 0 until bitmap.height) {
        var brightCount = 0
        for (x in 0 until bitmap.width) {
            if (pixelLuma(bitmap.getPixel(x, y)) > brightThreshold) brightCount++
        }

        if (brightCount >= rowThreshold && rowStart == -1) {
            rowStart = y
        } else if (brightCount < rowThreshold && rowStart != -1) {
            rowRuns += rowStart until y
            rowStart = -1
        }
    }
    if (rowStart != -1) rowRuns += rowStart until bitmap.height

    val plateRowRun = rowRuns
        .filter { run -> run.count() >= bitmap.height * 0.18f }
        .maxByOrNull { run -> run.count() }
        ?: return bitmap

    val yMargin = (plateRowRun.count() * 0.12f).toInt().coerceAtLeast(2)
    val top = (plateRowRun.first - yMargin).coerceAtLeast(0)
    val bottom = (plateRowRun.last + yMargin).coerceAtMost(bitmap.height - 1)
    val bandHeight = (bottom - top + 1).coerceAtLeast(1)
    val columnThreshold = (bandHeight * 0.18f).toInt().coerceAtLeast(3)
    val rawColumnRuns = mutableListOf<IntRange>()
    var columnStart = -1

    for (x in 0 until bitmap.width) {
        var brightCount = 0
        for (y in top..bottom) {
            if (pixelLuma(bitmap.getPixel(x, y)) > brightThreshold) brightCount++
        }

        if (brightCount >= columnThreshold && columnStart == -1) {
            columnStart = x
        } else if (brightCount < columnThreshold && columnStart != -1) {
            rawColumnRuns += columnStart until x
            columnStart = -1
        }
    }
    if (columnStart != -1) rawColumnRuns += columnStart until bitmap.width

    val mergedColumnRuns = mergeCloseRuns(rawColumnRuns, (bitmap.width * 0.025f).toInt().coerceAtLeast(3))
    val plateColumnRun = mergedColumnRuns
        .filter { run -> run.count() >= bitmap.width * 0.28f }
        .maxByOrNull { run -> run.count() }
        ?: return bitmap

    val xMargin = (plateColumnRun.count() * 0.035f).toInt().coerceAtLeast(3)
    val left = (plateColumnRun.first - xMargin).coerceAtLeast(0)
    val right = (plateColumnRun.last + xMargin).coerceAtMost(bitmap.width - 1)
    val cropWidth = right - left + 1
    val cropHeight = bottom - top + 1
    val aspectRatio = cropWidth.toFloat() / cropHeight.coerceAtLeast(1)

    if (aspectRatio !in OFFICIAL_PLATE_ASPECT_MIN..OFFICIAL_PLATE_ASPECT_MAX) return bitmap
    return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
}

private fun detectPlateAreaCandidatesInsideGuide(bitmap: Bitmap): List<Bitmap> {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val contrast = Mat()
    val blurred = Mat()
    val binary = Mat()
    val closed = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(23.0, 5.0))
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        gray.convertTo(contrast, -1, 1.7, -38.0)
        Imgproc.GaussianBlur(contrast, blurred, CvSize(3.0, 3.0), 0.0)
        Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        Imgproc.morphologyEx(binary, closed, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        contours
            .map { Imgproc.boundingRect(it) }
            .filter { rect ->
                val aspectRatio = rect.width.toFloat() / rect.height.coerceAtLeast(1)
                val widthRatio = rect.width.toFloat() / bitmap.width.coerceAtLeast(1)
                val heightRatio = rect.height.toFloat() / bitmap.height.coerceAtLeast(1)
                aspectRatio in OFFICIAL_PLATE_ASPECT_MIN..OFFICIAL_PLATE_ASPECT_MAX &&
                    widthRatio in 0.34f..0.98f &&
                    heightRatio in 0.22f..0.95f
            }
            .sortedByDescending { rect ->
                val aspectRatio = rect.width.toFloat() / rect.height.coerceAtLeast(1)
                val centerX = rect.x + rect.width / 2f
                val centerY = rect.y + rect.height / 2f
                val centerPenalty = kotlin.math.abs(centerX - bitmap.width / 2f) +
                    kotlin.math.abs(centerY - bitmap.height / 2f) * 0.6f
                rect.width * rect.height -
                    officialPlateAspectPenalty(aspectRatio, 210f) -
                    centerPenalty.toInt()
            }
            .take(4)
            .mapNotNull { rect ->
                cropExpandedRect(bitmap, rect, 0.035f, 0.18f)
            }
    } finally {
        rgba.release()
        gray.release()
        contrast.release()
        blurred.release()
        binary.release()
        closed.release()
        kernel.release()
        hierarchy.release()
        contours.forEach { it.release() }
    }
}

private fun cropExpandedRect(bitmap: Bitmap, rect: Rect, marginXRatio: Float, marginYRatio: Float): Bitmap? {
    val marginX = (rect.width * marginXRatio).toInt().coerceAtLeast(2)
    val marginY = (rect.height * marginYRatio).toInt().coerceAtLeast(2)
    val left = (rect.x - marginX).coerceAtLeast(0)
    val top = (rect.y - marginY).coerceAtLeast(0)
    val right = (rect.x + rect.width + marginX).coerceAtMost(bitmap.width)
    val bottom = (rect.y + rect.height + marginY).coerceAtMost(bitmap.height)
    val width = right - left
    val height = bottom - top
    if (width <= 1 || height <= 1) return null
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

private fun officialPlateAspectPenalty(aspectRatio: Float, scale: Float): Int {
    return (kotlin.math.abs(aspectRatio - OFFICIAL_PLATE_ASPECT_RATIO) * scale).toInt()
}

private fun buildPlateBitmapCandidates(bitmap: Bitmap): List<Bitmap> {
    val guideCandidate = cropPlateGuideArea(bitmap)
    val characterGroupCandidates = detectPlateCandidatesByCharacterGroups(guideCandidate)
    val detectedPlateCandidates = detectPlateAreaCandidatesInsideGuide(guideCandidate)
    val brightPlateCandidate = cropBrightPlateAreaInsideGuide(guideCandidate)
    val baseCandidates = (characterGroupCandidates + detectedPlateCandidates + brightPlateCandidate + guideCandidate)
        .distinctBy { candidate ->
            "${candidate.width}x${candidate.height}-${candidate.hashCode()}"
        }
    val candidates = baseCandidates.flatMap { candidate ->
        buildPlateCropVariants(candidate)
    }.distinctBy { candidate ->
        "${candidate.width}x${candidate.height}-${candidate.hashCode()}"
    }

    return candidates.map { candidate ->
        normalizePlateResolution(candidate)
    }.flatMap { candidate ->
        listOf(
            candidate,
            strongBinarizePlateBitmap(candidate),
            boldBinarizePlateBitmap(candidate, 1),
            boldBinarizePlateBitmap(candidate, 2),
            binarizePlateBitmap(candidate),
            adaptiveBinarizePlateBitmap(candidate)
        )
    }.distinctBy { candidate ->
        "${candidate.width}x${candidate.height}-${candidate.hashCode()}"
    }
}

private const val NORMALIZED_PLATE_WIDTH = 768
private const val NORMALIZED_PLATE_HEIGHT = 192

// 후보 이미지 크기를 통일해 전처리 강도와 ONNX 입력 스케일이 후보마다 달라지지 않게 합니다.
private fun normalizePlateResolution(bitmap: Bitmap): Bitmap {
    val source = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    val scale = minOf(
        NORMALIZED_PLATE_WIDTH.toFloat() / source.width.coerceAtLeast(1),
        NORMALIZED_PLATE_HEIGHT.toFloat() / source.height.coerceAtLeast(1)
    )
    val resizedWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val resizedHeight = (source.height * scale).toInt().coerceAtLeast(1)
    val resized = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true)
    val canvasBitmap = Bitmap.createBitmap(
        NORMALIZED_PLATE_WIDTH,
        NORMALIZED_PLATE_HEIGHT,
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(canvasBitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(
        resized,
        ((NORMALIZED_PLATE_WIDTH - resizedWidth) / 2).toFloat(),
        ((NORMALIZED_PLATE_HEIGHT - resizedHeight) / 2).toFloat(),
        null
    )
    return canvasBitmap
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

private fun buildPlateCropVariants(bitmap: Bitmap): List<Bitmap> {
    val sideTrimmed = trimPlateSideBars(bitmap)
    val variants = mutableListOf<Bitmap>()

    variants += bitmap
    variants += sideTrimmed
    variants += cropLeftDecorationIfPresent(bitmap)
    variants += cropLeftDecorationIfPresent(sideTrimmed)

    return variants.filter { candidate ->
        candidate.width > candidate.height * 2 && candidate.width > 80 && candidate.height > 20
    }.distinctBy { candidate ->
        "${candidate.width}x${candidate.height}-${candidate.hashCode()}"
    }
}

private fun cropLeftRatio(bitmap: Bitmap, ratio: Float): Bitmap {
    if (bitmap.width <= 2) return bitmap

    val cropLeft = (bitmap.width * ratio).toInt().coerceIn(1, bitmap.width - 1)
    val cropWidth = bitmap.width - cropLeft
    if (cropWidth < bitmap.height * 2) return bitmap

    return Bitmap.createBitmap(bitmap, cropLeft, 0, cropWidth, bitmap.height)
}

private fun trimPlateSideBars(bitmap: Bitmap): Bitmap {
    if (bitmap.width <= 2 || bitmap.height <= 2) return bitmap

    val maxTrim = (bitmap.width * 0.24f).toInt().coerceAtLeast(1)
    var left = 0
    while (left < maxTrim && estimateColumnDarkRatio(bitmap, left) > 0.68f) {
        left++
    }

    var right = bitmap.width - 1
    while (bitmap.width - 1 - right < maxTrim && estimateColumnDarkRatio(bitmap, right) > 0.68f) {
        right--
    }

    val cropWidth = right - left + 1
    if (left == 0 && right == bitmap.width - 1) return bitmap
    if (cropWidth < bitmap.width * 0.56f || cropWidth < bitmap.height * 2) return bitmap

    return Bitmap.createBitmap(bitmap, left, 0, cropWidth, bitmap.height)
}

private fun estimateColumnDarkRatio(bitmap: Bitmap, x: Int): Float {
    val safeX = x.coerceIn(0, bitmap.width - 1)
    val stepY = (bitmap.height / 48).coerceAtLeast(1)
    var dark = 0
    var total = 0

    for (y in 0 until bitmap.height step stepY) {
        if (pixelLuma(bitmap.getPixel(safeX, y)) < 80) dark++
        total++
    }

    return dark.toFloat() / total.coerceAtLeast(1)
}

private fun pixelLuma(pixel: Int): Int {
    val red = android.graphics.Color.red(pixel)
    val green = android.graphics.Color.green(pixel)
    val blue = android.graphics.Color.blue(pixel)
    return (red * 0.299f + green * 0.587f + blue * 0.114f).toInt()
}

private data class CharacterContour(
    val rect: Rect,
    val centerX: Double,
    val centerY: Double,
    val area: Double
)

private data class CharacterGroup(
    val contours: List<CharacterContour>,
    val bounds: Rect,
    val angleDegrees: Double,
    val score: Double
)

// 번호판 테두리 검출이 흔들릴 때 글자 contour 묶음을 기준으로 crop 영역을 더 정확하게 보정합니다.
private fun detectPlateCandidatesByCharacterGroups(bitmap: Bitmap): List<Bitmap> {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val blurred = Mat()
    val binary = Mat()
    val cleaned = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(2.0, 2.0))
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, CvSize(3.0, 3.0), 0.0)
        Imgproc.adaptiveThreshold(
            blurred,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            31,
            9.0
        )
        Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_OPEN, kernel)
        Imgproc.findContours(cleaned, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val characterContours = contours
            .map { Imgproc.boundingRect(it) }
            .mapNotNull { rect -> rect.toCharacterContour(bitmap.width, bitmap.height) }
            .sortedBy { it.centerX }

        buildCharacterGroups(characterContours, bitmap.width, bitmap.height)
            .take(5)
            .flatMap { group ->
                val officialCrop = cropOfficialPlateRatioCharacterGroupCandidate(bitmap, group, includeLeftDecoration = false)
                val officialLeftCrop = cropOfficialPlateRatioCharacterGroupCandidate(bitmap, group, includeLeftDecoration = true)
                val refinedCrop = cropCharacterGroupCandidate(bitmap, group)
                val deskewedCrop = cropDeskewedCharacterGroupCandidate(bitmap, group)
                listOfNotNull(officialCrop, officialLeftCrop, deskewedCrop, refinedCrop)
            }
    } finally {
        rgba.release()
        gray.release()
        blurred.release()
        binary.release()
        cleaned.release()
        kernel.release()
        hierarchy.release()
        contours.forEach { it.release() }
    }
}

private fun Rect.toCharacterContour(imageWidth: Int, imageHeight: Int): CharacterContour? {
    if (width <= 0 || height <= 0) return null

    val aspectRatio = width.toFloat() / height
    val area = width.toDouble() * height.toDouble()
    val imageArea = imageWidth.toDouble() * imageHeight.toDouble()
    val heightRatio = height.toFloat() / imageHeight.coerceAtLeast(1)
    val widthRatio = width.toFloat() / imageWidth.coerceAtLeast(1)

    if (heightRatio !in 0.16f..0.88f) return null
    if (widthRatio !in 0.006f..0.22f) return null
    if (aspectRatio !in 0.08f..1.35f) return null
    if (area < imageArea * 0.0004 || area > imageArea * 0.12) return null

    return CharacterContour(
        rect = this,
        centerX = x + width / 2.0,
        centerY = y + height / 2.0,
        area = area
    )
}

private fun buildCharacterGroups(
    contours: List<CharacterContour>,
    imageWidth: Int,
    imageHeight: Int
): List<CharacterGroup> {
    if (contours.size < 6) return emptyList()

    val groups = mutableListOf<CharacterGroup>()
    contours.forEach { base ->
        val matched = contours
            .filter { candidate ->
                val heightDiff = kotlin.math.abs(candidate.rect.height - base.rect.height).toDouble() /
                    maxOf(candidate.rect.height, base.rect.height).coerceAtLeast(1)
                val centerYDiff = kotlin.math.abs(candidate.centerY - base.centerY)
                val areaDiff = kotlin.math.abs(candidate.area - base.area) / maxOf(candidate.area, base.area)

                heightDiff <= 0.55 &&
                    centerYDiff <= maxOf(candidate.rect.height, base.rect.height) * 0.75 &&
                    areaDiff <= 0.82
            }
            .sortedBy { it.centerX }

        splitCharacterRuns(matched, imageWidth).forEach { run ->
            buildCharacterGroup(run, imageWidth, imageHeight)?.let(groups::add)
        }
    }

    return groups
        .distinctBy { group ->
            "${group.bounds.x / 8}-${group.bounds.y / 8}-${group.bounds.width / 8}-${group.bounds.height / 8}"
        }
        .sortedByDescending { it.score }
}

private fun splitCharacterRuns(contours: List<CharacterContour>, imageWidth: Int): List<List<CharacterContour>> {
    if (contours.isEmpty()) return emptyList()

    val runs = mutableListOf<MutableList<CharacterContour>>()
    var current = mutableListOf(contours.first())

    contours.drop(1).forEach { contour ->
        val previous = current.last()
        val gap = contour.rect.x - (previous.rect.x + previous.rect.width)
        val averageHeight = current.map { it.rect.height }.average().takeIf { !it.isNaN() } ?: previous.rect.height.toDouble()
        val maxGap = maxOf(8.0, averageHeight * 1.45, imageWidth * 0.035)

        if (gap <= maxGap) {
            current += contour
        } else {
            runs += current
            current = mutableListOf(contour)
        }
    }
    runs += current

    return runs
}

private fun buildCharacterGroup(
    contours: List<CharacterContour>,
    imageWidth: Int,
    imageHeight: Int
): CharacterGroup? {
    if (contours.size !in 6..9) return null

    val left = contours.minOf { it.rect.x }
    val top = contours.minOf { it.rect.y }
    val right = contours.maxOf { it.rect.x + it.rect.width }
    val bottom = contours.maxOf { it.rect.y + it.rect.height }
    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)
    val aspectRatio = width.toFloat() / height
    if (aspectRatio !in 2.2f..7.4f) return null
    if (width < imageWidth * 0.28f || height < imageHeight * 0.12f) return null

    val sorted = contours.sortedBy { it.centerX }
    val angle = estimateCharacterGroupAngle(sorted)
    if (kotlin.math.abs(angle) > 18.0) return null

    val averageHeight = sorted.map { it.rect.height }.average()
    val ySpread = sorted.maxOf { it.centerY } - sorted.minOf { it.centerY }
    val countScore = when (sorted.size) {
        7, 8 -> 180.0
        6, 9 -> 55.0
        else -> 0.0
    }
    val aspectScore = 120.0 - kotlin.math.abs(aspectRatio - OFFICIAL_PLATE_ASPECT_RATIO) * 18.0
    val alignmentScore = 70.0 - (ySpread / averageHeight.coerceAtLeast(1.0)) * 35.0
    val sizeScore = (width.toDouble() * height.toDouble()) / (imageWidth.toDouble() * imageHeight.toDouble()) * 180.0

    return CharacterGroup(
        contours = sorted,
        bounds = Rect(left, top, width, height),
        angleDegrees = angle,
        score = countScore + aspectScore + alignmentScore + sizeScore
    )
}

private fun estimateCharacterGroupAngle(contours: List<CharacterContour>): Double {
    if (contours.size < 2) return 0.0
    val first = contours.first()
    val last = contours.last()
    val dx = (last.centerX - first.centerX).takeIf { kotlin.math.abs(it) > 0.001 } ?: return 0.0
    val dy = last.centerY - first.centerY
    return Math.toDegrees(kotlin.math.atan2(dy, dx))
}

private fun cropCharacterGroupCandidate(bitmap: Bitmap, group: CharacterGroup): Bitmap? {
    val rect = expandCharacterGroupBounds(group.bounds, bitmap.width, bitmap.height)
    if (rect.width <= 1 || rect.height <= 1) return null
    return Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
}

private fun cropOfficialPlateRatioCharacterGroupCandidate(
    bitmap: Bitmap,
    group: CharacterGroup,
    includeLeftDecoration: Boolean
): Bitmap? {
    val rect = expandCharacterGroupBoundsToOfficialPlateRatio(
        bounds = group.bounds,
        imageWidth = bitmap.width,
        imageHeight = bitmap.height,
        includeLeftDecoration = includeLeftDecoration
    )
    if (rect.width <= 1 || rect.height <= 1) return null
    return Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
}

private fun cropDeskewedCharacterGroupCandidate(bitmap: Bitmap, group: CharacterGroup): Bitmap? {
    if (kotlin.math.abs(group.angleDegrees) < 1.0) return null

    val rgba = Mat()
    val rotated = Mat()
    return try {
        Utils.bitmapToMat(bitmap, rgba)
        val center = Point(group.bounds.x + group.bounds.width / 2.0, group.bounds.y + group.bounds.height / 2.0)
        val rotationMatrix = Imgproc.getRotationMatrix2D(center, group.angleDegrees, 1.0)
        Imgproc.warpAffine(rgba, rotated, rotationMatrix, rgba.size())

        val rect = expandCharacterGroupBounds(group.bounds, bitmap.width, bitmap.height)
        val safeRect = Rect(
            rect.x.coerceIn(0, rotated.cols() - 1),
            rect.y.coerceIn(0, rotated.rows() - 1),
            rect.width.coerceAtMost(rotated.cols() - rect.x).coerceAtLeast(1),
            rect.height.coerceAtMost(rotated.rows() - rect.y).coerceAtLeast(1)
        )
        val cropMat = Mat(rotated, safeRect)
        Bitmap.createBitmap(cropMat.cols(), cropMat.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(cropMat, result)
            cropMat.release()
        }
    } catch (_: Throwable) {
        null
    } finally {
        rgba.release()
        rotated.release()
    }
}

private fun expandCharacterGroupBounds(bounds: Rect, imageWidth: Int, imageHeight: Int): Rect {
    val marginX = (bounds.width * 0.18f).toInt().coerceAtLeast(8)
    val topMargin = (bounds.height * 0.45f).toInt().coerceAtLeast(8)
    val bottomMargin = (bounds.height * 0.28f).toInt().coerceAtLeast(6)
    val left = (bounds.x - marginX).coerceAtLeast(0)
    val top = (bounds.y - topMargin).coerceAtLeast(0)
    val right = (bounds.x + bounds.width + marginX).coerceAtMost(imageWidth)
    val bottom = (bounds.y + bounds.height + bottomMargin).coerceAtMost(imageHeight)
    return Rect(left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
}

// 글자 묶음 주변을 실제 번호판 비율로 다시 확장해 좌우가 잘린 crop을 줄입니다.
private fun expandCharacterGroupBoundsToOfficialPlateRatio(
    bounds: Rect,
    imageWidth: Int,
    imageHeight: Int,
    includeLeftDecoration: Boolean
): Rect {
    val minimumWidthFromChars = bounds.width * if (includeLeftDecoration) 1.22f else 1.14f
    val minimumHeightFromChars = bounds.height * 1.24f
    var targetWidth = maxOf(
        minimumWidthFromChars,
        (bounds.height / 0.74f) * OFFICIAL_PLATE_ASPECT_RATIO
    )
    var targetHeight = targetWidth / OFFICIAL_PLATE_ASPECT_RATIO

    if (targetHeight < minimumHeightFromChars) {
        targetHeight = minimumHeightFromChars
        targetWidth = targetHeight * OFFICIAL_PLATE_ASPECT_RATIO
    }

    val centerXOffset = if (includeLeftDecoration) -targetWidth * 0.035f else 0f
    val centerX = bounds.x + bounds.width / 2f + centerXOffset
    val centerY = bounds.y + bounds.height / 2f
    val targetWidthInt = targetWidth.toInt().coerceAtLeast(bounds.width + 2).coerceAtMost(imageWidth)
    val targetHeightInt = targetHeight.toInt().coerceAtLeast(bounds.height + 2).coerceAtMost(imageHeight)
    val left = (centerX - targetWidthInt / 2f).toInt().coerceIn(0, (imageWidth - targetWidthInt).coerceAtLeast(0))
    val top = (centerY - targetHeightInt / 2f).toInt().coerceIn(0, (imageHeight - targetHeightInt).coerceAtLeast(0))

    return Rect(left, top, targetWidthInt, targetHeightInt)
}

// 글자 contour 보정이 실패할 때를 대비해 edge 기반의 넓은 사각 후보를 fallback으로 사용합니다.
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
                aspectRatio in OFFICIAL_PLATE_ASPECT_MIN..OFFICIAL_PLATE_ASPECT_MAX &&
                    rect.width > bitmap.width * 0.10f &&
                    rect.height > bitmap.height * 0.015f &&
                    rect.height < bitmap.height * 0.28f
            }
            .sortedByDescending { rect ->
                val aspectRatio = rect.width.toFloat() / rect.height.coerceAtLeast(1)
                val aspectScore = 1000 - officialPlateAspectPenalty(aspectRatio, 120f)
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

// 사용자가 OCR 결과를 신뢰할 수 있도록 실제 OCR에 사용한 crop 이미지를 확인 화면에 보여줍니다.
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

// 모니터 촬영에서 생기는 촘촘한 줄무늬가 OCR 획으로 인식되지 않도록 약하게 완화합니다.
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

// 획 손상을 줄이기 위해 강한 이진화 대신 대비와 노이즈를 선명하게 보정한 후보를 먼저 만듭니다.
private fun strongBinarizePlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val contrast = Mat()
    val blurred = Mat()
    val sharpened = Mat()
    val binary = Mat()
    val cleaned = Mat()
    val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(2.0, 2.0))
    val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(1.0, 1.0))

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        gray.convertTo(contrast, -1, 2.15, -82.0)
        Imgproc.GaussianBlur(contrast, blurred, CvSize(0.0, 0.0), 0.8)
        Core.addWeighted(contrast, 1.7, blurred, -0.7, 0.0, sharpened)
        Imgproc.threshold(sharpened, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_CLOSE, closeKernel)
        Imgproc.morphologyEx(cleaned, cleaned, Imgproc.MORPH_OPEN, openKernel)

        Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(cleaned, result)
        }
    } finally {
        rgba.release()
        gray.release()
        contrast.release()
        blurred.release()
        sharpened.release()
        binary.release()
        cleaned.release()
        closeKernel.release()
        openKernel.release()
    }
}

// 얇게 잡힌 글자는 숫자 특징이 약해져서, 검은 획만 조금 두껍게 만든 후보도 OCR에 넣습니다.
private fun boldBinarizePlateBitmap(bitmap: Bitmap, iterations: Int): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val contrast = Mat()
    val blurred = Mat()
    val sharpened = Mat()
    val binary = Mat()
    val thickened = Mat()
    val cleaned = Mat()
    val strokeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(2.0, 2.0))
    val cleanKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(1.0, 1.0))

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        gray.convertTo(contrast, -1, 2.35, -92.0)
        Imgproc.GaussianBlur(contrast, blurred, CvSize(0.0, 0.0), 0.65)
        Core.addWeighted(contrast, 1.85, blurred, -0.85, 0.0, sharpened)
        Imgproc.threshold(sharpened, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        // 흰 배경과 검은 글자 영역에서만 팽창을 적용해 배경 노이즈가 글자로 번지는 것을 줄입니다.
        Imgproc.erode(
            binary,
            thickened,
            strokeKernel,
            Point(-1.0, -1.0),
            iterations.coerceIn(1, 2)
        )
        Imgproc.morphologyEx(thickened, cleaned, Imgproc.MORPH_OPEN, cleanKernel)

        Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(cleaned, result)
        }
    } finally {
        rgba.release()
        gray.release()
        contrast.release()
        blurred.release()
        sharpened.release()
        binary.release()
        thickened.release()
        cleaned.release()
        strokeKernel.release()
        cleanKernel.release()
    }
}

private fun enhanceReadablePlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val contrast = Mat()
    val denoised = Mat()
    val blurred = Mat()
    val sharpened = Mat()

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        gray.convertTo(contrast, -1, 1.38, 8.0)
        Imgproc.bilateralFilter(contrast, denoised, 5, 42.0, 42.0)
        Imgproc.GaussianBlur(denoised, blurred, CvSize(0.0, 0.0), 1.0)
        Core.addWeighted(denoised, 1.55, blurred, -0.55, 0.0, sharpened)

        Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(sharpened, result)
        }
    } finally {
        rgba.release()
        gray.release()
        contrast.release()
        denoised.release()
        blurred.release()
        sharpened.release()
    }
}

// crop의 번호판을 정렬하고 명암을 보정해 ONNX 모델이 학습 때와 비슷한 입력을 받게 합니다.
private fun enhancePlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val contrast = Mat()
    val denoised = Mat()
    val blurred = Mat()
    val sharpened = Mat()

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.equalizeHist(gray, contrast)
        Imgproc.bilateralFilter(contrast, denoised, 5, 35.0, 35.0)
        Imgproc.GaussianBlur(denoised, blurred, CvSize(0.0, 0.0), 0.8)
        Core.addWeighted(denoised, 1.45, blurred, -0.45, 0.0, sharpened)

        Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(sharpened, result)
        }
    } finally {
        rgba.release()
        gray.release()
        contrast.release()
        denoised.release()
        blurred.release()
        sharpened.release()
    }
}

// 배경 줄무늬나 그림자를 줄이기 위해 글자와 배경을 강하게 분리한 후보를 준비합니다.
private fun binarizePlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val blurred = Mat()
    val binary = Mat()
    val cleaned = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(1.0, 1.0))

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

// 전체 번호판 CTC 모델 결과에서 한국 번호판 형식에 가까운 문자만 후보로 추립니다.
// 모델과 charset은 assets에 포함된 학습 결과를 사용합니다.
private fun adaptiveBinarizePlateBitmap(bitmap: Bitmap): Bitmap {
    if (!OpenCVLoader.initLocal()) {
        error("OpenCV 초기화에 실패했습니다.")
    }

    val rgba = Mat()
    val gray = Mat()
    val denoised = Mat()
    val binary = Mat()
    val cleaned = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CvSize(1.0, 1.0))

    return try {
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.bilateralFilter(gray, denoised, 5, 38.0, 38.0)
        Imgproc.adaptiveThreshold(
            denoised,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            35,
            5.0
        )
        Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_CLOSE, kernel)

        Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888).also { result ->
            Utils.matToBitmap(cleaned, result)
        }
    } finally {
        rgba.release()
        gray.release()
        denoised.release()
        binary.release()
        cleaned.release()
        kernel.release()
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

    // 학습 코드와 같은 전처리를 적용해야 실제 추론에서도 문자 위치와 밝기 분포가 맞습니다.
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

    // CTC 특성상 같은 문자가 연속 출력될 수 있어 blank와 반복 문자를 제거합니다.
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

// 글자를 따로 자른 후보는 전체 번호판 모델보다 단일 글자 분류 모델이 안정적이어서 별도 경로로 읽습니다.
// 자리 정보를 이용해 숫자와 한글 후보군을 제한하면 문자 선택이 더 안정적입니다.
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

    // 단일 글자 모델은 학습 때와 같은 48x48 흰 배경 입력에 맞춰야 정확도가 유지됩니다.
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

// 휴대전화 값은 숫자만 저장하고 표시 단계에서만 하이픈을 넣어 입력 검증과 화면 표시를 분리합니다.
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
