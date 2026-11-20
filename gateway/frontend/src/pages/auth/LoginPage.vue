<!--
  로그인 화면 (화면설계안 _design/화면설계안_소스/js/v-login.js 기준)
  - 배경(LoginBackground) + 로그인 카드(LoginForm) + 아래쪽 문의 안내 + 불러오기 화면(LoginSplash)
  - 흐름: 카드에서 로그인 성공 → 불러오기 화면 → 권한별 첫 화면 (원래 가려던 주소가 있으면 그쪽)
-->
<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LoginBackground from '@/features/auth/LoginBackground.vue'
import LoginForm from '@/features/auth/LoginForm.vue'
import LoginSplash from '@/features/auth/LoginSplash.vue'
import { homeRouteOf } from '@/router/home'

const route = useRoute()
const router = useRouter()

// 로그인한 사용자 (값이 생기면 불러오기 화면 시작)
const loggedInUser = ref(null)

function onLoginSuccess(user) {
  loggedInUser.value = user
}

/** 불러오기 화면이 끝나면 이동 */
function onSplashDone() {
  router.replace(route.query.redirect || homeRouteOf(loggedInUser.value))
}
</script>

<template>
  <div class="login-page">
    <LoginBackground />
    <LoginForm :leaving="!!loggedInUser" :expired="route.query.reason === 'expired'" @success="onLoginSuccess" />
    <p class="footer" :class="{ hidden: loggedInUser }">ArgOS v1.0 · 계정 문의: 환경안전팀</p>
    <LoginSplash :user="loggedInUser" @done="onSplashDone" />
  </div>
</template>

<style scoped>
.login-page {
  position: fixed;
  inset: 0;
  overflow: auto;
  background: #f3f5f8;
}

.footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 28px;
  text-align: center;
  font-size: 12px;
  color: #64748b;
  text-shadow:
    0 0 4px #fff,
    0 0 8px #fff;
  transition: opacity 0.3s;
}

.footer.hidden {
  opacity: 0;
}
</style>
