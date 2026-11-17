# ArgOS 공정 담당자 앱 (Capacitor · Android)

Vue 3 + Vite 화면을 **Capacitor 8** 로 감싼 Android 앱입니다. 화면은 수행계획서 '앱 화면' 그대로입니다.

1. **공정 현황**: 담당 공정 설비 상태 (내 담당 설비 먼저, 센서 지금 값·주의 기준·배기팬, 경보 이상이면 빨간 안내 띠)
2. **체크리스트**: 공장 담당자에게 받은 점검 항목을 차례로 체크 (결과: 이상 없음 · 조치함 · 부품 교체 예정 · 추가 점검 필요 + 메모)
3. **완료 보고**: 모두 체크하면 한 줄 메모와 함께 보고 → 관제 웹에 '완료 · 확인 필요'

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

| 아이디 | 이름 | 담당 |
| --- | --- | --- |
| `u5_paint01` | 김도현 | 5공장 도장 공정 · 상도 부스 · 상도 건조로 (예시 체크리스트를 받는 사람) |
| `u5_paint02` | 박성훈 | 5공장 도장 공정 · 전착 건조로 등 |

비밀번호 `argos1234` (예시 데이터 전용 계정).

### Android 앱

```bash
npm run android        # vite build → dist → cap sync (android/app/src/main/assets/public 으로 복사)
npm run android:open   # Android Studio 로 열기 → 실행(▶) 또는 Build > Build APK
```

화면을 고친 뒤에는 `npm run android` 만 다시 하면 됩니다. `android/` 는 Capacitor 가 만든 Android 프로젝트(앱 ID `com.gte.argos.worker`, 이름 'ArgOS 담당자')이고, 빌드 결과물은 `android/.gitignore` 가 거릅니다.

- **예시 데이터 모드(기본)**: 앱 혼자 동작합니다. 휴대폰 안 예시 상황이라 PC 관제 웹과는 연결되지 않습니다.
- **실제 서버**: `.env` 에 `VITE_USE_MOCK=false`, `VITE_API_BASE_URL=https://연계서버/api/v1` 을 넣고 `npm run android`. 앱 안에서 `/api/v1` 은 휴대폰 자신을 가리키므로 주소를 꼭 적어야 합니다. `http://` 주소면 Android cleartext 허용 설정이 필요합니다.

## 폴더

```text
phone/
├── capacitor.config.json   # appId · appName · webDir(dist)
├── android/                # Capacitor Android 프로젝트 (npx cap add android 로 생성)
├── vite.config.js          # '@' = 관제 웹 src, 개발 /phone/ · 빌드 ./, '@/utils/storage' 만 앱 것으로
├── index.html
└── src/
    ├── main.js             # 시작점 (웹 글꼴·토큰·스타일, 웹 로그인 스토어)
    ├── router.js           # 해시 주소: #/login, #/ 공정 현황, #/checklists, #/checklists/:id
    ├── storage.js          # 로그인 저장 argos.phone.* (웹 argos.* 와 따로)
    ├── codes.js            # 체크리스트 상태 이름, 항목 결과 4가지
    ├── api/myChecklists.js # GET /me/checklists · PATCH 항목 · POST 완료 보고 (+ 예시 데이터 흉내)
    ├── stores/myChecklists.js
    ├── layouts/PhoneLayout.vue   # 위 바 + 아래 탭 + 알림 띠 + 진동(@capacitor/haptics)
    ├── components/         # PushBanner, EquipmentCard, ChecklistItem
    └── pages/              # LoginPage, HomePage, ChecklistListPage, ChecklistPage
```

## 아직 안 한 것

- **푸시(FCM)**: 지금 '새 체크리스트 · 다시 알림' 띠는 앱이 켜져 있을 때만 뜹니다. `@capacitor/push-notifications` + Firebase `google-services.json`(커밋 금지) + `POST /me/push-token` 이 필요합니다.
- **화재 '대피했습니다'**(`POST /fires/{id}/evacuated`): 명세서 M2 에 있으나 아직 화면 없음.
- **앱 실시간(SSE)**: 웹과 같은 `GET /stream` 을 앱에서도 열지 정해야 함.
