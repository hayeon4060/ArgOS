<!--
  담당자 앱 로그인 (API 명세서 A1, client: APP)
  - 관제 웹 로그인 카드의 입력칸·단추·오류 상자를 그대로 씀 ('@/features/auth')
  - 공정 담당자 계정만: 관리자 계정이면 서버가 CLIENT_NOT_ALLOWED
  - 성공하면 원래 가려던 곳(알림으로 연 체크리스트 등) 또는 공정 현황으로
-->
<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { USE_MOCK } from '@/api/mock'
import AppCheckbox from '@/components/AppCheckbox.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import LoginAlert from '@/features/auth/LoginAlert.vue'
import LoginField from '@/features/auth/LoginField.vue'
import LoginSubmit from '@/features/auth/LoginSubmit.vue'
import { SESSION_HOURS } from '@/features/session/sessionConfig'
import { useAuthStore } from '@/stores/auth'
import { loadSavedLoginId, saveLoginId } from '@/utils/storage'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const savedId = loadSavedLoginId()
const loginId = ref(savedId)
const password = ref('')
const rememberId = ref(true)
const showPassword = ref(false)
const loading = ref(false)
const fieldError = ref({ loginId: '', password: '' })
const alertBox = ref(
  route.query.reason === 'expired'
    ? { icon: 'clock', title: '로그인 유지 시간이 끝났습니다', text: `${SESSION_HOURS}시간이 지나 자동으로 로그아웃되었습니다.` }
    : null,
)

const loginIdField = ref(null)
const passwordField = ref(null)

onMounted(() => (savedId ? passwordField : loginIdField).value?.focus())

function clearErrors() {
  fieldError.value = { loginId: '', password: '' }
  alertBox.value = null
}

async function onSubmit() {
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

  loading.value = true
  try {
    await auth.login(loginId.value.trim(), password.value, 'APP')
  } catch (error) {
    loading.value = false
    alertBox.value =
      error.code === 'NETWORK_ERROR'
        ? { icon: 'wifioff', title: '서버에 연결하지 못했습니다', text: '네트워크 상태를 확인한 뒤 다시 시도해 주세요.' }
        : { icon: 'alert', title: error.message, text: '다시 확인해 주세요.' }
    password.value = ''
    passwordField.value?.focus()
    return
  }

  saveLoginId(rememberId.value ? loginId.value.trim() : '')
  router.replace(route.query.redirect || { name: 'home' })
}
</script>

<template>
  <div class="login">
    <form class="card" novalidate autocomplete="off" @submit.prevent="onSubmit">
      <AppLogo />
      <h1>공정 담당자 로그인</h1>
      <p class="sub">설비 상태 확인 · 점검 체크리스트</p>

      <LoginAlert v-if="alertBox" v-bind="alertBox" />

      <LoginField
        id="phone-login-id"
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
        id="phone-login-pw"
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
            class="eye"
            :aria-label="showPassword ? '비밀번호 숨기기' : '비밀번호 보기'"
            @click="showPassword = !showPassword"
          >
            <AppIcon :name="showPassword ? 'eyeoff' : 'eye'" />
          </button>
        </template>
      </LoginField>

      <div class="row"><AppCheckbox v-model="rememberId">아이디 저장</AppCheckbox></div>

      <LoginSubmit :loading="loading" />

      <p class="guide">관제 웹(본사·공장 담당자)과 따로 쓰는 공정 담당자 전용 화면입니다.</p>
      <p v-if="USE_MOCK" class="mock-hint">예시 계정: u5_paint01 (김도현), u5_paint02 (박성훈) / argos1234</p>
    </form>
  </div>
</template>

<style scoped>
.login {
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px calc(24px + env(safe-area-inset-bottom));
  background: var(--color-bg);
}

.card {
  width: 100%;
  max-width: 420px;
  padding: 32px 24px 28px;
  border: 1px solid var(--color-border-card);
  border-radius: 14px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
  animation: rise var(--m-rise) both;
}

h1 {
  margin-top: 22px;
  font-size: 23px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.sub {
  margin-top: 6px;
  font-size: 14px;
  color: var(--color-text-sub);
}

.eye {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: none;
  background: none;
  color: var(--color-text-weak);
  cursor: pointer;
}

.row {
  margin-top: 16px;
  font-size: 14px;
  color: var(--color-text-sub);
}

.guide {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
  font-size: 12.5px;
  line-height: 19px;
  color: var(--color-text-sub);
}

.mock-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-weak);
}
</style>
