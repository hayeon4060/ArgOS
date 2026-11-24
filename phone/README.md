# ArgOS 공정 담당자 앱 (Capacitor · Android)

Vue 3 + Vite 화면을 **Capacitor 8** 로 감싼 Android 앱입니다. 화면은 수행계획서 '앱 화면' 그대로입니다.

1. **공정 현황**: 담당 공정 설비 상태 (내 담당 설비 먼저, 센서 지금 값·주의 기준·배기팬, 경보 이상이면 빨간 안내 띠)
   + **3D 설비 배치도** (웹과 같은 그림 `PaintMap`, 휴대폰 폭에서는 좌우로 밀어 봄). 설비·설비 카드를 누르면 **설비 상세**:
   단계·발생 시각, 센서 지금 값·기준·평소, 열화상(최고 온도 지점), 요청자 + '체크리스트 시작'
2. **체크리스트**: 공장 담당자에게 받은 점검 항목을 차례로 체크 (결과: 이상 없음 · 조치함 · 부품 교체 예정 · 추가 점검 필요 + 메모)
3. **완료 보고**: 모두 체크하면 한 줄 메모와 함께 보고 → 관제 웹에 '완료 · 확인 필요'
3-1. **점검 관리**: 관제 웹 점검 관리 화면(`@/pages/inspection/InspectionPage.vue`)을 그대로 씀 — 점검 스케줄 달력 · 서류 등록 · 점검 이력. 휴대폰 폭에서는 위아래 한 줄로 (`src/pages/InspectionPage.vue`)

관제 웹(`../gateway/frontend`)의 `src` 를 `@` 로 그대로 가져다 씁니다(예시 데이터 · API · 로그인 스토어 · 공통 부품). 이 폴더에는 앱 화면과 앱 전용 API만 있습니다. 관제 웹이 다른 곳에 있으면 `ARGOS_WEB=경로 npm run dev`.

## 준비

- Node 20 이상
- Android 앱 빌드: **Android Studio** (Android SDK 36 포함). Capacitor 8 은 Android Studio 에 들어 있는 JDK 21 을 씀
- 관제 웹 폴더에서도 `npm install` (같이 띄울 때)

```bash
npm install
cp .env.example .env   # 필요하면 값 입력
```

## 실행

### 브라우저에서 (개발)

```bash
# 1) 관제 웹 (5173)
cd ../gateway/frontend && npm run dev
# 2) 이 앱 (5174)
npm run dev
```

**http://localhost:5173/phone/** 로 엽니다(관제 웹 개발 서버가 `/phone` 을 이 앱으로 넘겨 줌). 예시 상황은 관제 웹 개발 서버가 하나로 들고 중계하므로(`vite.config.js` 의 `mockSync`, `/__mock/scenario`) 웹에서 체크리스트 전송 → 앱에 알림 띠, 앱에서 체크 → 웹 진행 창에 바로 반영됩니다.

**휴대폰으로 보기**: 두 서버를 `npm run dev -- --host` 로 켜고, 휴대폰을 PC 와 같은 Wi-Fi(또는 휴대폰 핫스팟에 PC 연결)에 두고 `http://PC주소:5173/phone/` 을 엽니다. PC 관제 웹과 같은 상황을 봅니다. 기기끼리 접속을 막는 공용 Wi-Fi 에서는 안 열립니다.

**다른 PC 에서 같이 보기**: 관제 웹 개발 서버는 `host: true` 라 같은 Wi-Fi 의 다른 PC 도 크롬으로 `http://PC주소:5173` 을 열면 같은 예시 상황을 보고, 내 휴대폰 앱과 연동됩니다. 다른 PC 에서 개발 서버를 따로 켜면 상황이 따로 노니, 서버는 한 PC 에서만 켭니다. FCM 키(`argos-fcm-key.json`)도 서버를 켠 PC 에만 있으면 됩니다.

| 아이디 | 이름 | 담당 |
| --- | --- | --- |
| `u5_paint01` | 김도현 | 5공장 도장 공정 · 상도 부스 · 상도 건조로 (예시 체크리스트를 받는 사람) |
| `u5_paint02` | 박성훈 | 5공장 도장 공정 · 전착 건조로 등 |

비밀번호 `argos1234` (예시 데이터 전용 계정).

### 휴대폰 알림 (시연용 웹 푸시 — 화면이 꺼져 있어도 옴)

웹에서 체크리스트 전송 · 다시 알림 · 담당자 변경을 하면, 관제 웹 개발 서버(`mockSync.js`)가 담당자 크롬으로 웹 푸시를 보냅니다. 크롬 웹 푸시는 구글 FCM 으로 전달되므로 휴대폰 화면이 꺼져 있거나 크롬을 닫아 둬도 알림이 오고, 알림을 누르면 그 체크리스트 화면(`#/checklists/번호`)이 열립니다. Firebase 프로젝트·파일은 필요 없습니다(표준 웹 푸시 VAPID, 키는 개발 서버가 `.mock-push.json` 에 만듦).

크롬은 `https` 또는 `localhost` 주소에서만 알림을 켤 수 있어서, 휴대폰(`http://PC주소`)은 한 번 설정이 필요합니다.

1. 휴대폰 **크롬** 주소창에 `chrome://flags` → 'insecure origins' 검색 → **Insecure origins treated as secure** 칸에 `http://PC주소:5173` (예: `http://192.168.0.29:5173`) 입력 → Enabled → 아래 **Relaunch**
2. `http://PC주소:5173/phone/` 에서 로그인 → 위 바 아래 파란 줄 **알림 켜기** → 허용 (파란 줄이 사라지면 됨)
3. 권장: 휴대폰 설정 → 애플리케이션 → Chrome → 배터리 **제한 없음** (절전 때문에 늦게 오는 것 방지)
4. 화면을 끄고 PC 관제 웹(`ulsan5_admin`)에서 체크리스트 전송이나 다시 알림 → 휴대폰 알림 → 누르면 체크리스트

- PC 주소가 바뀌면(다른 Wi-Fi) 1번 값도 바꿔야 합니다. PC 개발 서버가 켜져 있고 인터넷이 돼야 보냅니다.
- 알림 문구는 휴대폰이 PC 개발 서버에서 받아 옵니다(푸시에는 내용을 싣지 않음). PC 에 닿지 않으면 '새 알림이 있습니다' 기본 문구.
- 전원이 꺼져 있던 휴대폰은 켠 뒤 30분 안이면 받습니다(TTL 1800초).
- 로그아웃하면 그 브라우저로는 그 사람 알림을 그만 보냅니다.

### Android 앱 (Capacitor, APK)

```bash
npm run android        # vite build → dist → cap sync (android/app/src/main/assets/public 으로 복사)
npm run android:open   # Android Studio 로 열기 → 실행(▶) 또는 Build > Build APK
```

화면을 고친 뒤에는 `npm run android` 만 다시 하면 됩니다. `android/` 는 Capacitor 가 만든 Android 프로젝트(앱 ID `com.gte.argos.worker`, 이름 'ArgOS 담당자')이고, 빌드 결과물은 `android/.gitignore` 가 거릅니다.

**명령줄로 APK 만들기 · 설치** (Android Studio 를 안 열고):

```powershell
$env:JAVA_HOME = 'C:\Java\zulu21'                  # Capacitor 8 은 JDK 21 (Android Studio 2026.2 내장 JDK 25 는 Gradle 8.14 가 못 씀)
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:GRADLE_USER_HOME = 'C:\Gradle'                 # Windows 사용자 이름이 한글이라 Gradle 캐시는 영문 경로에
android\gradlew.bat -p android assembleDebug        # → android\app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r android\app\build\outputs\apk\debug\app-debug.apk   # USB 디버깅 켠 휴대폰
```

- **Android Studio 에서 빌드할 때**: Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK** 를 `C:\Java\zulu21` 로. 프로젝트 경로에 한글이 있어 `android/gradle.properties` 에 `android.overridePathCheck=true` 를 둠
- **시연 빌드(예시 데이터 + PC 연동)**: `.env.production.local` 에 `VITE_MOCK_RELAY=http://PC주소:5173` 을 넣고 빌드하면, 앱이 PC 관제 웹 개발 서버(`npm run dev -- --host`)와 같은 예시 상황을 봅니다 — 웹에서 체크리스트 전송 → 앱 알림 띠, 앱에서 체크 → 웹 진행 창. PC 주소가 바뀌면 다시 빌드하지 않고 앱 로그인 화면 아래 **PC 주소** 칸에 새 주소(예: `192.168.0.38:5173`)를 넣고 저장 (앱이 다시 켜지며 그 주소로 붙음).
  PC 의 `http://` 주소에 붙으려고 `capacitor.config.json` 에 `server.androidScheme: http` · `cleartext: true` 를 둠 (실제 서버가 https 가 되면 `cleartext` 는 끌 것).
- **비우면**: 앱 혼자 휴대폰 안 예시 데이터로 동작 (PC 필요 없음).
- **실제 서버**: `.env` 에 `VITE_USE_MOCK=false`, `VITE_API_BASE_URL=https://연계서버/api/v1` 을 넣고 `npm run android`. 앱 안에서 `/api/v1` 은 휴대폰 자신을 가리키므로 주소를 꼭 적어야 합니다.
  연계 서버에 CORS 설정이 없어서 `capacitor.config.json` 에 `CapacitorHttp.enabled: true` 를 둠 (요청을 앱 안 웹 화면이 아니라 Android 가 보냄 → CORS 에 안 걸림).
  통합 시험(192.168.0.31) 주소는 `.env.production.local` 에 넣어 둠 — 계정 `worker12` / `Argos1234!`, 휴대폰도 같은 Wi-Fi(`smartfactory_5G`).
  Android Studio 내장 JDK 가 25 면 Gradle 8.14 가 못 돌므로 JDK 21 로: `JAVA_HOME=JDK21경로 ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk gradlew assembleDebug`.

**설치형 앱 알림(FCM)** — 앱이 꺼져 있거나 화면이 꺼져 있어도 옴. Firebase 파일 두 개가 있어야 켜짐:

1. Firebase 콘솔 → 프로젝트 → Android 앱 추가(패키지 `com.gte.argos.worker`) → `google-services.json` 을 `android/app/` 에 (커밋 금지, `android/.gitignore`) → `npm run android` 다시 · 빌드 · 설치 (파일이 없으면 앱은 푸시를 아예 안 켬 — `vite.config.js` `__FCM__`)
2. 시연 빌드: 프로젝트 설정 → 서비스 계정 → 새 비공개 키(JSON) → **사용자 폴더의 `argos-fcm-key.json`** (예: `C:\Users\사용자\argos-fcm-key.json`, 저장소 밖)에 두면 관제 웹 개발 서버가 켜질 때 읽음 (다른 곳이면 `ARGOS_FCM_KEY=경로`) → 체크리스트 전송 · 다시 알림 · 담당자 변경 때 개발 서버가 FCM 으로 보냄
   실제 서버: 백엔드 `PushSender` 실제 전송 구현 필요 (아래 '아직 안 한 것')
3. 알림을 누르면 그 체크리스트 화면이 열림 (`src/push.js`)

- 앱이 열린 채 화면만 꺼지면 FCM 이 알림을 앱 안으로만 넘김 → `capacitor.config.json` `plugins.PushNotifications.presentationOptions: alert` 로 그때도 휴대폰 알림을 띄움
- 채널: `argos_alert`(중요도 최대, 앱이 처음 켜질 때 만듦) — `AndroidManifest.xml` 의 FCM 기본 채널도 이것
- 처음 켜면 Android 13+ '알림 허용' 창 → 허용. 휴대폰이 무음이면 소리 없이 알림만 옴
- 휴대폰 크롬에서도 웹 푸시(위 '휴대폰 알림')를 켜 두었으면 같은 알림이 두 번 옴 → 앱을 쓸 때는 크롬 사이트 알림을 끔
- 확인(2026-11-18, 폴드7 SM-F966N · Android 16): 화면 꺼짐 + 앱 열림, 화면 꺼짐 + 앱 종료 둘 다 알림 도착(FCM 200), 누르면 그 체크리스트 화면

## 폴더

```text
phone/
├── capacitor.config.json   # appId · appName · webDir(dist)
├── android/                # Capacitor Android 프로젝트 (npx cap add android 로 생성)
├── vite.config.js          # '@' = 관제 웹 src, 개발 /phone/ · 빌드 ./, '@/utils/storage' 만 앱 것으로
├── index.html
├── public/sw.js            # 웹 푸시 서비스 워커: 알림 띄우기 · 누르면 그 체크리스트 열기
└── src/
    ├── main.js             # 시작점 (웹 글꼴·토큰·스타일, 웹 로그인 스토어)
    ├── router.js           # 해시 주소: #/login, #/ 공정 현황, #/equipment/:id 설비 상세, #/checklists, #/checklists/:id, #/inspections 점검 관리
    ├── storage.js          # 로그인 저장 argos.phone.* (웹 argos.* 와 따로)
    ├── codes.js            # 체크리스트 상태 이름, 항목 결과 4가지
    ├── push.js             # 휴대폰 알림(FCM, 설치형 앱): 허용 묻기 · 토큰 등록(실제 서버 /me/push-token 또는 시연 PC 개발 서버) · 누르면 체크리스트로
    ├── webPush.js          # 시연용 휴대폰 알림(웹 푸시, 브라우저 + 예시 데이터): '알림 켜기' · 구독 등록
    ├── api/myChecklists.js # GET /me/checklists · PATCH 항목 · POST 완료 보고 (+ 예시 데이터 흉내)
    ├── stores/myChecklists.js
    ├── layouts/PhoneLayout.vue   # 위 바 + 아래 탭 + 알림 띠 + 진동(@capacitor/haptics)
    ├── components/         # PushBanner, EquipmentCard, ChecklistItem
    └── pages/              # LoginPage, HomePage, EquipmentPage, ChecklistListPage, ChecklistPage, InspectionPage
```

## 아직 안 한 것

- **실제 서버 푸시(FCM)**: 시연(브라우저 웹 푸시 · 설치형 앱 + 개발 서버 FCM)은 됨. 실제 서버는 앱 쪽만 됨 (`src/push.js`).
  **보내는 쪽은 아직 없음** — 백엔드(`ArgOS-develop`) `PushSender` 는 '푸시 전송(시뮬레이션)' 로그만 남김(firebase-admin 전송 코드 없음).
  관제 웹 `mockSync.js` 의 FCM HTTP v1 보내기(`fcmMessage` · `googleAssertion`)와 같은 모양으로 구현하면 됨
- **화재 '대피했습니다'**(`POST /fires/{id}/evacuated`): 명세서 M2 에 있으나 아직 화면 없음.
- **앱 실시간(SSE)**: 웹과 같은 `GET /stream` 을 앱에서도 열지 정해야 함.
