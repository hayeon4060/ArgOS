<!--
  로그인 카드 (화면설계안 js/v-login.js, css/views.css .lc 기준)
  - 아이디·비밀번호 입력, 아이디 저장, 비밀번호 재설정 안내, 로그인 버튼
  - 로그인 성공(웹 사용 가능한 권한)이면 success 이벤트로 사용자 정보를 넘김
  - 이동·불러오기 화면은 부모(LoginPage)가 담당
  - 비밀번호를 먼저 바꿔야 하는 계정(로그인 응답 pwResetRequired)이면 비밀번호 변경 창을 먼저 띄움
    (그 전에는 서버가 다른 API 를 전부 403 으로 막음 — 2026-11-20 전수 비교에서 추가)
-->
<script setup>
import { onMounted, ref } from 'vue'
import LoginAlert from './LoginAlert.vue'
import LoginField from './LoginField.vue'
import LoginSubmit from './LoginSubmit.vue'
import AppCheckbox from '@/components/AppCheckbox.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import PasswordModal from '@/features/user/PasswordModal.vue'
import { SESSION_HOURS } from '@/features/session/sessionConfig'
import { homeRouteOf } from '@/router/home'
import { useAuthStore } from '@/stores/auth'
import { loadSavedLoginId, saveLoginId } from '@/utils/storage'

const props = defineProps({
  /** 성공 후 카드가 위로 사라지는 중 */
  leaving: { type: Boolean, default: false },
  /** 로그인 유지 시간이 지나 자동 로그아웃되어 온 경우 (안내 상자를 처음부터 띄움) */
  expired: { type: Boolean, default: false },
})

const emit = defineEmits(['success'])

const auth = useAuthStore()

// 입력값 ('아이디 저장'으로 저장된 아이디가 있으면 미리 채움)
const savedId = loadSavedLoginId()
const loginId = ref(savedId)
const password = ref('')
const rememberId = ref(true)
const showPassword = ref(false)

// 화면 상태
const loading = ref(false)
const fieldError = ref({ loginId: '', password: '' }) // 칸 아래 빨간 글씨
// 카드 위쪽 상자: { icon, title, text } — 자동 로그아웃으로 왔으면 처음부터 안내
const alertBox = ref(
  props.expired
    ? {
        icon: 'clock',
        title: '로그인 유지 시간이 끝났습니다',
        text: `${SESSION_HOURS}시간이 지나 자동으로 로그아웃되었습니다. 다시 로그인해 주세요.`,
      }
    : null,
)
const notice = ref('') // 비밀번호 재설정 안내
const shaking = ref(false) // 실패 시 카드 흔들기

const loginIdField = ref(null)
const passwordField = ref(null)

onMounted(() => {
  // 저장된 아이디가 있으면 비밀번호 칸에, 없으면 아이디 칸에 커서
  ;(savedId ? passwordField : loginIdField).value?.focus()
})

/**
 * 사용자가 다시 입력하면 오류 표시 지우기
 * - watch 대신 입력 이벤트에 연결: 실패 후 코드에서 비밀번호를 비울 때는 오류 상자가 남아 있어야 함
 */
function clearErrors() {
  fieldError.value = { loginId: '', password: '' }
  alertBox.value = null
}

/** 카드 흔들기 (애니메이션 다시 시작하려고 한 번 껐다 켬) */
function shake() {
  shaking.value = false
  requestAnimationFrame(() => (shaking.value = true))
}

/** 로그인 실패 안내: 서버 연결 실패 / 아이디·비밀번호 오류 */
function showLoginError(error) {
  if (error.code === 'NETWORK_ERROR') {
    alertBox.value = { icon: 'wifioff', title: '서버에 연결하지 못했습니다', text: '네트워크 상태를 확인한 뒤 다시 시도해 주세요.' }
  } else {
    alertBox.value = { icon: 'alert', title: error.message, text: '다시 확인해 주세요.' }
    password.value = ''
    passwordField.value?.focus()
  }
  shake()
}

async function onSubmit() {
  notice.value = ''

  // 1) 빈 칸 검사
  if (!loginId.value.trim()) {
    fieldError.value.loginId = '아이디를 입력해 주세요.'
    loginIdField.value?.focus()
    return
  }
  if (!password.value) {
    fieldError.value.password = '비밀번호를 입력해 주세요.'
    passwordField.value?.focus()
    return
  }

  // 2) 로그인 요청
  loading.value = true
  try {
    await auth.login(loginId.value.trim(), password.value)
  } catch (error) {
    loading.value = false
    showLoginError(error)
    return
  }

  // 3) 웹에서 갈 곳이 없는 권한(WORKER 등)이면 로그아웃 후 안내
  if (homeRouteOf(auth.user).name === 'login') {
    loading.value = false
    await auth.logout({ callServer: false })
    alertBox.value = { icon: 'info', title: '공정 담당자는 모바일 앱을 이용해 주세요', text: '관제 웹은 본사 관리자와 공장 담당자만 사용할 수 있습니다.' }
    return
  }

  // 4) 비밀번호를 먼저 바꿔야 하는 계정이면 변경 창부터 (바꾸면 finish, 취소하면 로그아웃)
  if (auth.pwResetRequired) {
    loading.value = false
    pwOpen.value = true
    return
  }

  finish()
}

/** 아이디 저장 후 부모에 성공 알림 */
function finish() {
  saveLoginId(rememberId.value ? loginId.value.trim() : '')
  emit('success', auth.user)
}

/* ───────── 첫 로그인 · 비밀번호 초기화 뒤: 비밀번호 변경 창 ───────── */
const pwOpen = ref(false)

/** 새 비밀번호로 바꿈 → 이제 다른 화면을 쓸 수 있음 */
function onPasswordChanged() {
  auth.passwordChanged()
  finish()
}

/** 창이 닫힘: 바꾸지 않고 닫았으면 로그아웃 (바꾼 경우에는 위 onPasswordChanged 가 먼저 불림) */
async function onPasswordClose() {
  pwOpen.value = false
  if (!auth.pwResetRequired) return
  await auth.logout()
  password.value = ''
  alertBox.value = { icon: 'info', title: '비밀번호를 바꿔야 로그인할 수 있습니다', text: '임시 비밀번호로 다시 로그인한 뒤 새 비밀번호를 정해 주세요.' }
}
</script>

<template>
  <form class="card" :class="{ shake: shaking, leave: leaving }" novalidate autocomplete="off" @submit.prevent="onSubmit">
    <AppLogo />
    <h1>로그인</h1>
    <p class="sub">환경안전 통합관제 시스템</p>

    <LoginAlert v-if="alertBox" v-bind="alertBox" />

    <LoginField
      id="login-id"
      ref="loginIdField"
      v-model="loginId"
      label="아이디"
      icon="user"
      autocomplete="username"
      :disabled="loading"
      :error="fieldError.loginId"
      @input="clearErrors"
    />

    <LoginField
      id="login-pw"
      ref="passwordField"
      v-model="password"
      label="비밀번호"
      icon="lock"
      :type="showPassword ? 'text' : 'password'"
      autocomplete="current-password"
      :disabled="loading"
      :error="fieldError.password"
      :invalid="alertBox?.icon === 'alert'"
      @input="clearErrors"
    >
      <template #suffix>
        <button
          type="button"
          class="icon-btn"
          :aria-label="showPassword ? '비밀번호 숨기기' : '비밀번호 보기'"
          @click="showPassword = !showPassword"
        >
          <AppIcon :name="showPassword ? 'eyeoff' : 'eye'" />
        </button>
      </template>
    </LoginField>

    <div class="row">
      <AppCheckbox v-model="rememberId">아이디 저장</AppCheckbox>
      <button type="button" class="link" @click="notice = '비밀번호 재설정은 환경안전팀에 요청해 주세요.'">
        비밀번호 재설정
      </button>
    </div>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <LoginSubmit :loading="loading" />
  </form>

  <!-- 임시 비밀번호로 들어온 계정: 지금 비밀번호 = 방금 넣은 임시 비밀번호 -->
  <PasswordModal
    :open="pwOpen"
    notice="임시 비밀번호로 로그인했습니다. 새 비밀번호로 바꾼 뒤 이용할 수 있습니다. '지금 비밀번호'에는 임시 비밀번호를 넣어 주세요."
    @changed="onPasswordChanged"
    @close="onPasswordClose"
  />
</template>

<style scoped>
.card {
  position: relative;
  width: 440px;
  max-width: calc(100% - 32px);
  /* 카드 높이(약 476px)의 절반 + 10px → 화면 가운데보다 살짝 위 (안내 문구 삭제로 카드가 줄어 다시 맞춤) */
  margin: max(32px, calc(var(--screen-h) / 2 - 248px)) auto 80px;
  padding: 44px 40px 40px;
  background: #fff;
  border: 1px solid var(--color-border-card);
  border-radius: 10px;
  box-shadow: var(--shadow-card);
  animation: rise 0.5s cubic-bezier(0.2, 0.8, 0.2, 1) 0.12s both;
  transition:
    opacity 0.3s ease-in,
    transform 0.35s ease-in;
}

.card.shake {
  animation: shake 0.4s ease-in-out;
}

.card.leave {
  opacity: 0;
  transform: translateY(-14px) scale(0.97);
  pointer-events: none;
}

h1 {
  margin-top: 26px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.4px;
}

.sub {
  margin-top: 6px;
  color: #64748b;
  font-size: 14px;
}

.icon-btn {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 5px;
  background: none;
  color: var(--color-text-weak);
  cursor: pointer;
}

.icon-btn:hover {
  background: #f1f5f9;
}

/* 아이디 저장 · 비밀번호 재설정 */
.row {
  display: flex;
  align-items: center;
  margin-top: 16px;
  font-size: 13px;
  color: var(--color-text-sub);
  user-select: none;
}

.link {
  margin-left: auto;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-primary);
  font-size: 13px;
  cursor: pointer;
}

.notice {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--color-text-sub);
}
</style>
