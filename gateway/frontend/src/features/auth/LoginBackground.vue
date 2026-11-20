<!--
  로그인 화면 배경 (장식용)
  - 설계안 기본 시안 1번(선으로 그린 공장 단지)을 정적 SVG 로 만든 그림
  - 가운데는 흐리게, 바깥은 진하게 보이도록 마스크를 씌우고 천천히 움직임
-->
<script setup>
import loginBg from '@/assets/images/login-bg.svg'
</script>

<template>
  <div class="login-bg" aria-hidden="true">
    <img :src="loginBg" alt="" />
  </div>
</template>

<style scoped>
.login-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  opacity: 0.75;
  pointer-events: none;
  -webkit-mask-image: radial-gradient(ellipse 520px 430px at 50% 49%, rgba(0, 0, 0, 0.12) 55%, #000 100%);
  mask-image: radial-gradient(ellipse 520px 430px at 50% 49%, rgba(0, 0, 0, 0.12) 55%, #000 100%);
}

/*
 * 그림(1920×1080)을 화면 전체에 채움 — 비율은 그대로 두고 남는 쪽은 잘라냄 (object-fit: cover)
 * (전에는 1920×1080 크기로 고정해서, 그보다 큰 화면(QHD 등)에서는 그림이 가장자리까지 닿지 않고 끊겨 보였음)
 */
img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: drift 36s ease-in-out infinite alternate;
}

@keyframes drift {
  from {
    transform: translate3d(0, 0, 0) scale(1.04);
  }
  to {
    transform: translate3d(-28px, -14px, 0) scale(1.08);
  }
}
</style>
