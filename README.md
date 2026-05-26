# Vehicle License Report App

Android 번호판 인식 기반 불법 주정차 신고 앱입니다. CameraX로 번호판과 차량 사진을 촬영하고, 앱 안에서 번호판 OCR 결과를 확인/수정한 뒤 신고 내역을 저장합니다.

이 README는 Android 앱 기능만 설명합니다. OCR 모델 학습 폴더와 학습 절차는 제외합니다.

## 주요 기능

- 이메일/아이디 기반 로그인 및 회원가입
- 비회원으로 앱 사용
- 프로필 정보 조회 및 수정
- 프로필 이미지 선택/삭제
- 홈 화면 우측 상단 프로필 이미지 표시
- 비밀번호 변경
- 로그아웃
- 법령 카테고리별 위반 유형 선택
- 번호판 촬영 및 OCR 인식
- OCR에 사용된 번호판 crop 이미지 확인
- OCR 원문과 최종 번호판 확인/수정
- 차량 증빙 사진 2장 촬영
- 신고 내용과 연락처 입력
- 허위 신고 주의 확인 후 신고 접수
- 최근 3개월 신고내역 요약 표시
- 전체 신고내역 조회, 기간 필터, 정렬, 페이지 이동
- 신고 상세 내역과 저장된 사진 확인
- Firebase 설정이 있으면 사용자 프로필/신고 내역을 서버에 저장
- Firebase Storage 설정이 있으면 차량 사진 업로드 URL 저장
- Firebase 설정이 없거나 비회원이면 로컬 저장 기반으로 동작

## 앱 흐름

1. 로그인, 회원가입 또는 비회원으로 시작합니다.
2. 홈 화면에서 `신고하기`를 선택합니다.
3. 법령 카테고리를 고릅니다.
4. 세부 위반 유형을 고릅니다.
5. 번호판을 촬영합니다.
6. OCR 결과와 crop 이미지를 확인하고 번호판을 수정할 수 있습니다.
7. 차량 증빙 사진 2장을 촬영합니다.
8. 신고 내용과 연락처를 확인합니다.
9. 허위 신고 주의 문구를 확인한 뒤 신고를 접수합니다.
10. 접수된 신고는 신고내역 화면에서 확인합니다.

## 화면 구성

- `Login`: 로그인, 회원가입 이동, 비회원 계속
- `SignUp`: 아이디, 비밀번호, 이메일, 전화번호, 생년월일 입력
- `Home`: 신고하기, 신고내역, 프로필, 마이페이지 카드와 최근 3개월 신고내역
- `Profile`: 회원 프로필 정보, 프로필 이미지, 정보 수정, 비밀번호 변경, 로그아웃
- `GuestProfile`: 비회원 안내와 로그인 이동
- `ViolationCategory`: 법령 카테고리 선택
- `ViolationType`: 세부 위반 유형 선택
- `PlateCamera`: 번호판 촬영
- `PlateConfirm`: OCR 결과 확인 및 번호판 수정
- `VehicleCamera`: 차량 증빙 사진 촬영
- `ReportForm`: 신고 내용과 연락처 입력
- `FinalConfirm`: 허위 신고 주의 확인
- `Complete`: 신고 완료 안내
- `ReportHistory`: 신고내역 목록
- `ReportHistoryDetail`: 신고 상세 내역

## 기술 구성

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

## 프로젝트 구조

```text
D:\AI
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

## 주요 파일

- `app/src/main/java/com/example/vehiclelicensereportapp/MainActivity.kt`
  - 화면 전환, Compose UI, CameraX 촬영, OpenCV 전처리, ONNX OCR, Firebase/로컬 저장 로직을 포함합니다.
- `app/src/main/assets/plate_ocr.onnx`
  - 전체 번호판 OCR 모델입니다.
- `app/src/main/assets/char_ocr.onnx`
  - 문자 단위 OCR 모델입니다.
- `app/src/main/assets/charset.txt`
  - 전체 번호판 OCR 문자셋입니다.
- `app/src/main/assets/char_charset.txt`
  - 문자 단위 OCR 문자셋입니다.
- `app/build.gradle.kts`
  - Android, Compose, CameraX, OpenCV, ONNX Runtime, Firebase 의존성을 정의합니다.
- `app/src/main/AndroidManifest.xml`
  - 카메라와 인터넷 권한을 선언합니다.

## Firebase 설정

Firebase 설정 파일이 없어도 앱은 비회원/로컬 저장 방식으로 실행됩니다. 실제 로그인과 서버 저장을 사용하려면 다음 설정이 필요합니다.

1. Firebase Console에서 Android 앱을 등록합니다.
2. 패키지명은 `com.example.vehiclelicensereportapp`로 맞춥니다.
3. `google-services.json` 파일을 `app/google-services.json` 위치에 추가합니다.
4. Firebase Authentication에서 이메일/비밀번호 로그인을 활성화합니다.
5. Firestore Database를 생성합니다.
6. Storage를 사용하는 경우 Firebase Storage도 활성화합니다.

`app/build.gradle.kts`는 `google-services.json` 파일이 있을 때만 Google Services 플러그인을 적용합니다.

## 저장 방식

- 회원 프로필은 로컬 SharedPreferences에 저장됩니다.
- Firebase 설정이 있으면 프로필을 Firestore에도 저장합니다.
- 신고 내역은 로컬 SharedPreferences에 저장됩니다.
- 로그인 상태이고 Firestore가 설정되어 있으면 신고 내역을 `users/{uid}/reports/{reportId}` 경로에 저장합니다.
- Storage가 설정되어 있으면 차량 사진을 업로드하고 다운로드 URL을 신고 내역에 함께 저장합니다.
- 비회원은 일부 서버 연동 기능이 제한됩니다.

## 권한

앱은 다음 Android 권한을 사용합니다.

- `android.permission.CAMERA`
- `android.permission.INTERNET`

카메라 권한이 없으면 번호판/차량 사진 촬영 화면으로 진행하기 전에 권한 요청 화면을 표시합니다.

## 빌드 방법

Windows PowerShell 기준:

```powershell
cd D:\AI
.\gradlew.bat :app:assembleDebug
```

빌드 결과 APK는 일반적으로 다음 위치에 생성됩니다.

```text
D:\AI\app\build\outputs\apk\debug\app-debug.apk
```

## 현재 참고 사항

- 앱의 핵심 구현은 현재 `MainActivity.kt`에 집중되어 있습니다.
- Firebase Storage는 요금제/콘솔 설정에 따라 사용 가능 여부가 달라질 수 있습니다.
- OCR 결과는 자동 제출되지 않고 사용자가 확인/수정하는 흐름으로 설계되어 있습니다.
- OCR 모델 파일은 앱 실행 자산으로 포함되어 있으나, 모델 학습 과정은 이 README에서 다루지 않습니다.
