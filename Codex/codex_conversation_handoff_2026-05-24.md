# Codex Conversation Handoff - 2026-05-24

이 파일은 다른 Codex/ChatGPT 대화에서 현재 작업을 이어가기 위한 인수인계 문서입니다.
주의: Codex 앱에서 대화 전체 원문을 자동 export하는 기능은 사용할 수 없어서, 아래 내용은 전체 원문 복사본이 아니라 지금까지의 대화와 작업 상태를 가능한 한 자세히 정리한 이어가기용 기록입니다.

## 프로젝트 위치

- Android 프로젝트: `D:\AI`
- 앱 메인 파일: `D:\AI\app\src\main\java\com\example\vehiclelicensereportapp\MainActivity.kt`
- OCR 학습 폴더: `D:\AI\plate_ocr_training`
- 앱 OCR 모델 assets:
  - `D:\AI\app\src\main\assets\plate_ocr.onnx`
  - `D:\AI\app\src\main\assets\charset.txt`
  - `D:\AI\app\src\main\assets\char_ocr.onnx`
  - `D:\AI\app\src\main\assets\char_charset.txt`

## 앱 목표

대한민국 불법 주정차 신고를 쉽게 하기 위한 Android 앱입니다.
핵심 기능은 차량 번호판을 카메라로 촬영하고 OCR로 번호판을 인식한 뒤, 사용자가 수정/확인해서 신고 정보를 작성하는 흐름입니다.

초기 아이디어:
- 안전신문고/120 신고처럼 번거로운 사진 첨부와 입력 절차를 줄임.
- AI/OCR로 번호판을 자동 입력.
- 사용자가 인식된 번호판을 확인하고 수정 가능.
- 신고 유형을 선택하고 차량 사진과 신고 내용을 작성.

## 앱 화면 흐름

현재 구현/요청된 흐름은 다음과 같습니다.

1. 메인 화면
   - 흰색 배경
   - 상단 제목: `불법 주정차 신고`
   - 부제: `대한민국의 깨끗한 도로를 위해`
   - 버튼: `신고하기`, `신고내역`, `프로필`, `도움말`
   - 버튼은 파란/청록 계열 그라데이션 카드 스타일
   - 버튼 안 이모지는 제거됨

2. 신고하기 클릭
   - `불법 주정차 위반유형 선택` 화면
   - 먼저 법 카테고리 선택
   - 카테고리:
     - 도로교통법
     - 장애인등 편의법
     - 소방기본법
     - 친환경 자동차법

3. 카테고리별 세부 위반유형
   - 도로교통법:
     - 소화전
     - 교차로
     - 버스정류장
     - 횡단보도
     - 어린이보호구역
     - 인도
     - 기타
   - 장애인등 편의법:
     - 장애인 전용구역
   - 소방기본법:
     - 소방차 전용구역
   - 친환경 자동차법:
     - 친환경차 충전구역

4. 번호판 OCR 촬영 화면
   - 차량 사진 위에 흰색 안내 배너 표시
   - 안내 문구: `번호판이 보이게 차량 사진을 찍어주세요`
   - 흰색 번호판 가이드 박스 표시
   - 하단에는 `이전` 버튼만 있음
   - 촬영 버튼은 흰색 원형 버튼
   - 현재는 이 촬영이 OCR용 1장 촬영

5. 번호판 확인/수정 화면
   - 제목: `이 번호가 맞습니까?`
   - 부제: `다시한번 정확한지 확인해주세요`
   - `OCR에 사용한 번호판 영역` 이미지 표시
   - `OCR 원문:` 표시
   - `번호판 확인/수정` 입력칸
   - 버튼: `다시찍기`, `다음으로`
   - 번호판이 비어 있으면 경고 배너: `번호판을 입력해주세요`

6. 차량 증빙 사진 촬영
   - 번호판 확인 후 차량 앞/뒤 총 2장 촬영 흐름
   - 첫 촬영 전 안내: `차량 앞 뒤 총 2장을 찍어주세요`
   - 첫 사진 촬영 후 화면 유지, 안내 문구: `한번 더찍어주세요`
   - 두 번째 사진 촬영 후 신고 정보 확인 화면으로 이동
   - 첨부사진 항목에는 촬영된 파일명만 표시

7. 신고 정보 확인 화면
   - 제목: `신고 정보 확인`
   - 부제: `제출 전 번호판과 신고 유형을 확인해주세요`
   - 항목:
     - 유형
     - 첨부사진
     - 번호판 확인/수정
     - 내용
     - 휴대전화
   - 내용 입력:
     - 5자 이상 900자 이하
     - 900자 초과 입력 불가
     - 라벨 옆 작은 글씨: `(수정 가능, 5~900자)`
   - 휴대전화:
     - 숫자만 입력
     - 11자리만 허용
     - `XXX-XXXX-XXXX` 형식으로 표시
   - 내용/휴대전화 양식이 맞지 않으면 경고 배너: `알맞은 양식으로 입력해주세요`

8. 최종 경고 화면
   - 신고 정보 확인에서 `다음으로` 클릭 시 이동
   - 중앙에 노란색 경고 아이콘
   - 굵은 제목: `한번 더 확인해주세요`
   - 설명: `허위 신고시 무고죄 또는 공무집행방해죄로 법적 처벌을 받을 수 있습니다`
   - 설명은 중앙 정렬
   - 하단 버튼: `이전`, `신고하기`

## 앱 아이콘

사용자가 제공한 불법주정차 신고 느낌의 아이콘을 앱 런처 아이콘으로 적용했습니다.

## 현재 OCR 방식

현재 메인 OCR은 Google ML Kit이 아니라 직접 학습한 ONNX OCR입니다.
ML Kit 관련 코드가 과거에 있었지만, 현재 핵심 경로는 직접 학습한 모델입니다.

현재 OCR 파이프라인:

1. 카메라 촬영
2. EXIF 방향 보정
3. 흰색 가이드 박스 영역 기준 1차 crop
4. 가이드 crop 안에서 OpenCV로 실제 번호판 후보 탐지
5. 밝은 번호판 후보도 추가
6. 탐지가 실패할 때를 대비해 가이드 crop fallback 유지
7. 번호판 후보를 정규 해상도로 통일
8. 여러 전처리 후보 생성
   - 강한 흑백 이진화
   - 검은 글자 1픽셀 두껍게
   - 검은 글자 2픽셀 두껍게
   - 일반 이진화
   - 적응형 이진화
   - 읽기 쉬운 회색 보정
   - 기본 선명화 보정
9. 직접 학습한 `plate_ocr.onnx` 전체 번호판 OCR 실행
10. 일부 후보는 글자 단위 `char_ocr.onnx`도 병행
11. 한국 번호판 형식 후처리
   - `[숫자 2개][한글 1개][숫자 4개]`
   - `[숫자 3개][한글 1개][숫자 4개]`
12. 후보 점수 비교
13. 마지막 4자리 숫자 후보는 여러 OCR 결과끼리 투표해서 보정
14. 가장 형식에 맞고 점수가 높은 결과를 입력칸에 표시
15. 사용자가 최종 확인/수정

## 최근 중요한 수정

최근 가장 효과가 컸던 수정은 `boldBinarizePlateBitmap()` 추가입니다.

수정 의도:
- crop은 맞는데 글자 획이 얇게 잡히는 경우 `1`, `2`, `3`, `5`, `6`, `8` 등이 흔들렸음.
- 단순 대비 증가가 아니라 흰 배경/검은 글자 이미지에서 OpenCV `erode`를 사용해 검은 획을 1~2픽셀 두껍게 만드는 후보를 추가함.
- 기존 잘 맞던 후보를 제거하지 않고 추가 후보로만 넣었기 때문에 기존 성능을 크게 해치지 않음.

현재 OCR 후보 목록에는 다음이 들어갑니다.

```kotlin
listOf(
    strongBinarizePlateBitmap(candidate),
    boldBinarizePlateBitmap(candidate, 1),
    boldBinarizePlateBitmap(candidate, 2),
    binarizePlateBitmap(candidate),
    adaptiveBinarizePlateBitmap(candidate),
    enhanceReadablePlateBitmap(candidate),
    enhancePlateBitmap(candidate)
)
```

최근 테스트 결과:
- `123가4568`은 안정적으로 `123가4568`로 인식됨.
- `32서1190`도 최근에는 `32서1190`로 인식되는 케이스가 나옴.
- 사용자는 “인식률이 진짜 좋아졌다”고 확인함.

## 중요한 코드 위치

대략적인 핵심 함수:

- OCR 시작: `recognizePlateFromImage(...)`
- 후보 생성: `buildPlateBitmapCandidates(...)`
- 가이드 crop: `cropPlateGuideArea(...)`
- 가이드 내부 번호판 후보 탐지: `detectPlateAreaCandidatesInsideGuide(...)`
- 번호판 해상도 통일: `normalizePlateResolution(...)`
- 강한 이진화: `strongBinarizePlateBitmap(...)`
- 진한 획 후보: `boldBinarizePlateBitmap(...)`
- 일반 이진화: `binarizePlateBitmap(...)`
- 적응형 이진화: `adaptiveBinarizePlateBitmap(...)`
- 읽기용 회색 보정: `enhanceReadablePlateBitmap(...)`
- ONNX OCR: `PlateOcrOnnxRecognizer`
- 글자 단위 ONNX OCR: `PlateCharOnnxRecognizer`

## 카메라/가이드 관련 현재 값

현재 `MainActivity.kt`에 다음 상수가 있음.

```kotlin
private const val PLATE_GUIDE_WIDTH_RATIO = 0.82f
private const val PLATE_GUIDE_HEIGHT_RATIO = 0.14f
private const val PLATE_GUIDE_TOP_RATIO = 0.38f
private val CAMERA_CAPTURE_RESOLUTION = Size(1920, 1080)
```

CameraX 쪽은 Preview와 ImageCapture에 같은 ViewPort/UseCaseGroup을 적용해서, 사용자가 보는 가이드와 실제 촬영 crop 좌표가 최대한 맞도록 처리함.

## OCR 학습 데이터 폴더

학습에 사용한 주요 폴더:

- 실제/수동 데이터:
  - `D:\AI\plate_ocr_training\dataset\images`
- 합성 번호판 10만장:
  - `D:\AI\plate_ocr_training\synthetic_100k\images`
- 글자 단위 데이터:
  - `D:\AI\plate_ocr_training\char_dataset\images`

주의:
- `char_dataset\images`는 한때 자동 crop이 잘못되어 두 글자 숫자, 잘린 숫자, 로고 조각, 라벨과 실제 이미지가 안 맞는 샘플이 섞였음.
- 그래서 글자 단위 데이터는 품질 검사를 거친 것만 쓰는 것이 좋음.
- 특히 `6_platecrop_...`인데 실제는 한글 `우` 같은 문제가 있었음.
- `00`, `43`, `80`처럼 두 글자가 한 파일에 들어간 샘플도 있었음.
- 이런 데이터는 학습 결과를 낮출 수 있음.

## 합성 데이터 관련

`Virtual_Number_Plate-master.zip` 기반으로 합성 번호판을 만들었고, 다음 문제가 있었음.

초기 문제:
- 번호판이 너무 길게 생성됨.
- 번호판 끝 글자가 잘림.
- 번호판 테두리 안에 글자가 제대로 들어가지 않음.
- 사용자가 금지한 한글 `아/바/사/자`가 포함되어 다시 제거하고 생성함.

이후 수정:
- 번호판 비율을 실제 양식에 맞춤.
- 글자가 테두리 안에 들어가도록 함.
- `아/바/사/자` 제거.
- 파일명에서 정답 라벨을 추출하는 방식 사용.

파일명 라벨 규칙 예:
- `999호8685_synthB_004766.png` -> 정답 `999호8685`
- 글자 단독 이미지: `주_char10k_009227.png` -> 정답 `주`
- 숫자 단독 이미지: `2_char10k_003671.png` -> 정답 `2`

## 학습 방식

학습은 `D:\AI\plate_ocr_training`에서 진행했습니다.
대표 명령:

```powershell
cd D:\AI\plate_ocr_training
python train.py --epochs 1 --batch-size 128 --workers 1
```

이후 GPU 사용/worker 관련 대화가 있었고:
- `--workers 4`는 환경에서 막히거나 불안정할 수 있었음.
- `--workers 2`로 fallback하는 방향을 이야기함.
- 수동 학습 + Codex가 실행한 학습을 이어서 모델에 반영함.

사용자 확인 기준:
- 사용자가 총 3회 이상 학습했다고 말함.
- 이후 합성 10만장 + 정리된 데이터로 추가 학습을 진행함.
- 마지막 쪽에서는 앱에 새 ONNX 모델을 반영함.

## 현재 빌드 확인

최근 빌드 명령:

```powershell
cd D:\AI
.\gradlew.bat :app:assembleDebug
```

최근 결과:

```text
BUILD SUCCESSFUL
```

Android Studio에서는 `Run`을 누르면 현재 앱이 실행됨.
APK 위치:

```text
D:\AI\app\build\outputs\apk\debug\app-debug.apk
```

## 최근 사용자 피드백 정리

사용자는 OCR crop/전처리에 대해 매우 민감하게 확인했습니다.
중요한 요구는 다음입니다.

- 흰색 가이드 박스 안 전체를 그냥 OCR하지 말 것.
- 가이드 박스는 촬영 보조이고, 그 안에서 실제 번호판을 다시 찾아야 함.
- 실제 번호판만 crop해야 함.
- 전처리는 흰색 바탕에 검은 글자를 더 강조해야 함.
- 얇은 띠처럼 잘리는 crop은 잘못된 것.
- 형식에 맞지 않는 결과는 채택하지 말고 다시 촬영하도록 해야 함.
- 잘 맞는 케이스를 망가뜨리지 말고 후보를 추가하는 방식이 좋음.

가장 최근 피드백:
- `boldBinarizePlateBitmap` 추가 후 `123가4568`, `32서1190` 인식률이 많이 좋아졌다고 함.

## 앞으로 이어서 할 때 추천 작업

다음 대화에서 이어서 작업할 때 추천 순서:

1. 현재 `MainActivity.kt` 상태를 먼저 읽기.
2. `buildPlateBitmapCandidates`, `detectPlateAreaCandidatesInsideGuide`, `boldBinarizePlateBitmap`, `processPlateBitmapCandidates`를 확인.
3. 앱 빌드:
   ```powershell
   cd D:\AI
   .\gradlew.bat :app:assembleDebug
   ```
4. 새 실패 이미지가 나오면 다음 중 하나를 조정:
   - crop 후보 점수
   - 번호판 후보 contour 조건
   - 이진화 후보 순서
   - `boldBinarizePlateBitmap`의 contrast/beta/iteration
   - 형식 후처리 점수
5. 무작정 모델 재학습보다, 앱에서 실패한 crop 이미지를 저장해서 학습 데이터로 추가하는 것이 효과적.

## 새 대화에 붙여넣을 요약 프롬프트

아래 내용을 새 Codex 대화에 붙여넣으면 이어가기 쉽습니다.

```text
D:\AI Android 번호판 신고 앱을 이어서 작업해줘.
주요 파일은 D:\AI\app\src\main\java\com\example\vehiclelicensereportapp\MainActivity.kt 이야.
앱은 CameraX + OpenCV + 직접 학습한 ONNX OCR을 사용해 차량 번호판을 인식한다.
현재 OCR 흐름은 흰색 가이드 박스 영역 crop -> 그 안에서 OpenCV로 실제 번호판 후보 탐지 -> 번호판 후보 해상도 통일 -> 여러 전처리 후보 생성 -> plate_ocr.onnx 전체 OCR -> char_ocr.onnx 글자 OCR 병행 -> 한국 번호판 형식 후처리 -> 최종 후보 선택이다.
최근 boldBinarizePlateBitmap(candidate, 1/2)을 추가해서 흰 배경/검은 글자 이미지에서 검은 획을 1~2픽셀 두껍게 만든 후보를 넣었고, 이 수정 후 123가4568과 32서1190 인식률이 많이 좋아졌다.
학습 폴더는 D:\AI\plate_ocr_training 이고, 주요 데이터는 dataset\images, synthetic_100k\images, char_dataset\images 이다.
char_dataset에는 과거 잘못 잘린 글자/두 글자/로고 조각이 섞였던 적이 있으니 주의해야 한다.
현재는 ML Kit이 아니라 직접 학습한 ONNX OCR이 메인이다.
먼저 MainActivity.kt를 읽고 현재 상태를 파악한 뒤 이어서 수정해줘.
```

## 주의할 점

- 현재 인식률이 좋아진 상태이므로 전처리 함수를 무작정 갈아엎지 말 것.
- 잘 맞는 후보는 유지하고 새 후보를 추가하는 방식이 안전함.
- 번호판 형식에 맞지 않는 결과는 최종 채택하면 안 됨.
- `32서1190` 같은 실제 번호판은 한글이 흔들릴 수 있으므로, 한글 포함 실제 crop 데이터를 더 모으는 것이 좋음.
- 합성 데이터만 늘리면 실제 카메라/모니터 촬영 환경과 차이가 생길 수 있음.
- 앱에서 실패한 crop 이미지를 저장하고 정답 파일명으로 추가 학습하는 방식이 가장 현실적임.
