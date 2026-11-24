# ArgOS 프론트엔드 (관제실·본사 웹)

Vue 3 (`<script setup>`, JavaScript) + Vite + Vue Router + Pinia + Axios + ECharts(vue-echarts)

## 실행

```bash
cd gateway/frontend
npm install
cp .env.example .env   # 필요하면 값 입력
npm run dev            # 개발 서버 (http://localhost:5173)
npm run build          # 배포용 빌드 (dist/)
```

백엔드 개발 전이라 기본은 **예시 데이터(mock) 모드**입니다. 백엔드 없이 아래 예시 계정으로 로그인할 수 있습니다.

| 아이디 | 권한 | 첫 화면 |
| --- | --- | --- |
| `hq_admin` | 본사 관리자 | 사업장 선택 |
| `ulsan5_admin` | 공장 담당자 (울산공장 5공장) | 울산공장 현황 (가정, `src/router/home.js` 의 `PLANT_ADMIN_HOME`) |

비밀번호는 둘 다 `argos1234` 입니다. 예시 데이터는 **5공장 도장 공정 전류 센서 1개가 '주의'** 인 상황입니다(다른 상황은 `src/api/mock/data.js` 의 5공장 줄 주석 참고). 실제 백엔드에 붙일 때는 `.env` 에 `VITE_USE_MOCK=false` 를 넣으면 `/api` 요청이 `http://localhost:8080` 으로 갑니다.

### 다른 PC의 서버에 붙기

백엔드가 다른 PC에서 돌 때는 `gateway/frontend/.env.local` 파일을 만들어 아래 두 줄을 넣고 `npm run dev` 를 다시 실행하세요. `.env.local` 은 커밋되지 않으므로 PC마다 자기 값을 써도 됩니다.

```dotenv
VITE_USE_MOCK=false
VITE_PROXY_TARGET=http://<서버 PC IP>:8080
```

`VITE_API_BASE_URL` 에 다른 PC 주소를 직접 넣으면 CORS 에 막히므로, 주소는 프록시 대상(`VITE_PROXY_TARGET`)으로만 바꿉니다.

### 로그인 유지 시간

로그인하면 **13시간** 유지되고, 상단 바의 남은 시간 옆 **'연장'** 을 누르면 1시간 늘어납니다. 만료 10분 전에 안내 문구가 뜨고, 시간이 다 되면 자동으로 로그아웃됩니다. 값은 `src/features/session/sessionConfig.js` 한 곳에 있습니다.

개발할 때 빨리 확인하려면 `.env` 에 `VITE_DEV_SESSION_MINUTES=2` 를 넣고 `npm run dev` 를 다시 실행하세요(개발 모드에서만 2분).

## 폴더 구조

```text
src/
├── api/              # 연계 서버 API (http.js = Axios 공통, mock/ = 예시 데이터)
├── assets/
│   ├── images/       # 설계안에서 만든 정적 그림 (로그인 배경, 한반도 지도, 울산공장 지도, 5공장 공정 배치도, 도장 공정 3D 배치도)
│   └── styles/       # tokens.css(색·글꼴·움직임 토큰), base.css(전역 기본), motion.css(화면 전환·등장·말풍선 움직임)
├── components/       # 여러 화면이 같이 쓰는 부품
│                     #   AppHeader(+Status, +Badges, +User), AppNav(상단 메뉴), SitePill(사업장 이름),
│                     #   AppLogo, AppIcon, AppCheckbox, AppNotice, BaseCard, LevelBadge
├── features/         # 화면별 부품 (페이지는 이 부품들을 조립만 함)
│   ├── auth/         #   로그인: LoginForm, LoginField, LoginAlert, LoginSubmit, LoginBackground, LoginSplash
│   ├── motion/       #   화면 전환: PageTransition(레이아웃이 씀), pageTransition.js(깊이로 줌 인·아웃 방향 결정)
│   ├── session/      #   로그인 유지 13시간·연장 1시간: sessionConfig(설정 한 곳), SessionTimer(상단 바), useSessionTimer
│   ├── dashboard/    #   사업장 현황: ZoneMapCard(UlsanMap, ZonePopover), FactoryStatusCard(Item),
│   │                 #   SensorTypeCard, AlarmListCard(AlarmRow), HourlyAlarmCard(HourlyChart)
│   ├── factory/      #   공장 대시보드: PlantMapCard(PlantMap, plantLayout.js), ProcessStatusCard(Item)
│   ├── process/      #   공정 3D 설비 배치도: ProcessMapCard(PaintMap, paintLayout.js, FanPopover, EquipmentStrip, SensorRing),
│   │                 #   InspectionTodayCard(InspectionRow)
│   └── site/         #   사업장 선택: SiteMapCard(KoreaMap), SiteSensorCard(SensorDonut),
│                     #   SiteListCard → SiteListItem → FactoryRow, StageCounts, StageBar, 집계·도우미 js
├── layouts/          # BlankLayout(로그인), MainLayout(상단 바 + 본문 전환, 사업장 선택은 meta.plain 으로 메뉴 없이)
├── pages/            # 화면 (데이터 불러오기·화면 이동만, 모양은 features 부품이 담당)
├── router/           # 주소·권한 가드(index.js), 첫 화면(home.js), 상단 메뉴(menus.js)
│                     #   주소: /sites(사업장 선택) → /sites/:siteId(사업장 현황) → /sites/:siteId/factories/:factoryId
│                     #         (공장 대시보드) → .../processes/:processId (3D 설비 배치도) → .../equipment/:equipmentId (설비 상세, 다음 작업)
├── stores/           # Pinia (auth: 로그인 상태)
├── types/            # 공통 코드 (상태 단계 LEVEL, 권한 ROLE, 센서 종류, 알림 진행 상태)
└── utils/            # echarts 등록, 차트 색(chartColors), 브라우저 저장, 날짜 형식, 안내 문구(useNotice)
docs/
└── backend-notes.md  # 백엔드에 전달할 API 사항 (화면 만들 때마다 추가)
```

## 규칙

- 화면 배치·색·문구는 설계 자료의 화면설계안을 기준으로 맞춤
- 색·글꼴·모서리는 `src/assets/styles/tokens.css` 변수만 사용 (차트는 CSS 변수를 못 읽어서 `utils/chartColors.js`, 값은 tokens.css 와 같게)
- 상태·권한 문자열은 `src/types/codes.js` 상수만 사용
- API 호출은 `src/api/` 모듈을 통해서만 (응답 형식·인증 방식 변경 시 `http.js` 만 수정)
- 새 API 를 쓰면 `docs/backend-notes.md` 에 함께 적기
- 한 파일에 화면 전체를 넣지 않음: 페이지는 조립만, 덩어리(카드·줄·입력칸)는 features/ 에 파일로 나눔
- 움직임: 시간·곡선은 `tokens.css` 의 `--m-*` 만 사용. 화면 전환은 라우터 `meta.depth`(대시보드 단계)로 자동 결정되고, 카드는 `BaseCard` 를 쓰면 자동으로 차례 등장. 말풍선·드롭다운은 `animation: pop-in var(--m-pop-in)`. '움직임 줄이기' 설정은 `motion.css` 에서 처리
