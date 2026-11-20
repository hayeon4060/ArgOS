<!--
  ArgOS 로고 (파란 네모 안 눈 아이콘 + 글자)
  - 상단 바, 로그인 카드, 로그인 후 불러오기 화면에서 같이 사용
  - big: 불러오기 화면용 큰 로고 (아이콘만 크게, 글자는 아래)
-->
<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  big: { type: Boolean, default: false },
})
</script>

<template>
  <span class="app-logo" :class="{ big }">
    <span class="mark"><AppIcon name="eye" :size="big ? 34 : 22" /></span>
    <b>ArgOS</b>
  </span>
</template>

<style scoped>
.app-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--color-primary-hover);
  color: #fff;
  display: grid;
  place-items: center;
}

b {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--color-text);
}

/* 큰 로고: 아이콘 위, 글자 아래 */
.big {
  flex-direction: column;
  gap: 16px;
}

.big .mark {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: var(--color-primary);
  box-shadow: 0 10px 28px rgba(37, 99, 235, 0.3);
}

.big b {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.5px;
}
</style>
