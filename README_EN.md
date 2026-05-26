# Vehicle License Report App

This is an Android app for reporting illegal parking and stopping violations using license plate recognition. The app captures license plates and vehicle evidence photos with CameraX, lets the user review and edit OCR results, and stores report history.

This README describes only the Android app features. OCR model training folders and training procedures are intentionally excluded.

## Key Features

- Email/user ID based login and sign-up
- Guest mode
- Profile viewing and editing
- Profile image selection and removal
- Profile image display in the top-right corner of the home screen
- Password change
- Logout
- Violation type selection by legal category
- License plate photo capture and OCR recognition
- Preview of the license plate crop used for OCR
- OCR raw text and final license plate review/editing
- Capture of 2 vehicle evidence photos
- Report content and contact phone number input
- Final false-report warning confirmation before submission
- Recent 3-month report summary on the home screen
- Full report history view with period filter, sorting, and pagination
- Report detail view with saved photos
- Server-side user profile and report storage when Firebase is configured
- Vehicle photo upload URL storage when Firebase Storage is configured
- Local storage fallback when Firebase is unavailable or when using guest mode

## App Flow

1. Start with login, sign-up, or guest mode.
2. Select `Report` on the home screen.
3. Choose a legal category.
4. Choose a detailed violation type.
5. Capture the license plate.
6. Review the OCR result and crop image, then edit the plate number if needed.
7. Capture 2 vehicle evidence photos.
8. Review the report content and contact phone number.
9. Confirm the false-report warning and submit the report.
10. Check submitted reports in the report history screen.

## Screens

- `Login`: Login, sign-up navigation, guest mode
- `SignUp`: User ID, password, email, phone number, and birth date input
- `Home`: Report, report history, profile, my page cards, and recent 3-month report summary
- `Profile`: Member profile information, profile image, profile editing, password change, and logout
- `GuestProfile`: Guest account notice and login navigation
- `ViolationCategory`: Legal category selection
- `ViolationType`: Detailed violation type selection
- `PlateCamera`: License plate capture
- `PlateConfirm`: OCR result review and plate number editing
- `VehicleCamera`: Vehicle evidence photo capture
- `ReportForm`: Report content and contact phone number input
- `FinalConfirm`: False-report warning confirmation
- `Complete`: Report completion notice
- `ReportHistory`: Report history list
- `ReportHistoryDetail`: Report detail view

## Tech Stack

- Kotlin
- Android Jetpack Compose
- Material 3
- CameraX
- OpenCV
- ONNX Runtime Android
- Firebase Authentication
- Firebase Firestore
- Firebase Storage
- SharedPreferences

## Project Structure

```text
D:\...\AI
├─ app
│  ├─ src\main\java\com\example\vehiclelicensereportapp
│  │  └─ MainActivity.kt
│  ├─ src\main\assets
│  │  ├─ plate_ocr.onnx
│  │  ├─ char_ocr.onnx
│  │  ├─ charset.txt
│  │  └─ char_charset.txt
│  ├─ src\main\AndroidManifest.xml
│  └─ build.gradle.kts
├─ gradle\libs.versions.toml
├─ build.gradle.kts
├─ settings.gradle.kts
└─ gradlew.bat
```

## Important Files

- `app/src/main/java/com/example/vehiclelicensereportapp/MainActivity.kt`
  - Contains screen navigation, Compose UI, CameraX capture, OpenCV preprocessing, ONNX OCR, Firebase integration, and local storage logic.
- `app/src/main/assets/plate_ocr.onnx`
  - Full license plate OCR model.
- `app/src/main/assets/char_ocr.onnx`
  - Character-level OCR model.
- `app/src/main/assets/charset.txt`
  - Character set for the full license plate OCR model.
- `app/src/main/assets/char_charset.txt`
  - Character set for the character-level OCR model.
- `app/build.gradle.kts`
  - Defines Android, Compose, CameraX, OpenCV, ONNX Runtime, and Firebase dependencies.
- `app/src/main/AndroidManifest.xml`
  - Declares camera and internet permissions.

## Firebase Setup

The app can run in guest/local-storage mode without Firebase configuration. To enable real login and server-side storage, configure Firebase as follows.

1. Register an Android app in Firebase Console.
2. Use `com.example.vehiclelicensereportapp` as the package name.
3. Add `google-services.json` to `app/google-services.json`.
4. Enable email/password sign-in in Firebase Authentication.
5. Create a Firestore Database.
6. Enable Firebase Storage if vehicle photo upload is required.

`app/build.gradle.kts` applies the Google Services plugin only when `google-services.json` exists.

## Storage Behavior

- Member profiles are stored locally with SharedPreferences.
- When Firebase is configured, profiles are also stored in Firestore.
- Report history is stored locally with SharedPreferences.
- When a user is logged in and Firestore is configured, reports are stored at `users/{uid}/reports/{reportId}`.
- When Firebase Storage is configured, vehicle photos are uploaded and their download URLs are saved with the report.
- Some server-linked features are limited in guest mode.

## Permissions

The app uses the following Android permissions.

- `android.permission.CAMERA`
- `android.permission.INTERNET`

If camera permission is missing, the app shows a permission request screen before entering license plate or vehicle photo capture.

## Build

Windows PowerShell:

```powershell
cd D:\...\AI
.\gradlew.bat :app:assembleDebug
```

The debug APK is usually generated at:

```text
D:\...\AI\app\build\outputs\apk\debug\app-debug.apk
```

## Notes

- The core app implementation is currently concentrated in `MainActivity.kt`.
- Firebase Storage availability may depend on the Firebase plan and console settings.
- OCR results are not submitted automatically. The user reviews and can edit the recognized plate number before submission.
- OCR model files are included as runtime app assets, but model training is not covered in this README.
