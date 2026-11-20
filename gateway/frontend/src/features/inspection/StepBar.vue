<!--
  단계 표시 (화면설계안 vInspect() steps())
  ① 파일 올리기 → ② 문자 인식 → ③ 원본 대조 → ④ 저장
  - 끝난 단계는 초록 체크, 지금 단계는 파란 번호가 천천히 깜빡임, 연결선은 0.3초에 초록으로 채워짐
-->
<script setup>
import { STEPS } from './inspectionMeta'
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  /** 0 ~ 3 지금 단계, 4 모두 끝 */
  stage: { type: Number, required: true },
})
</script>

<template>
  <ol class="steps" aria-label="등록 단계">
    <template v-for="(s, i) in STEPS" :key="s">
      <li v-if="i" class="line" :class="{ dn: i <= stage }" aria-hidden="true" />
      <li :class="{ dn: stage > 3 || i < stage, on: i === stage }" :aria-current="i === stage ? 'step' : undefined">
        <b class="mono"><AppIcon v-if="stage > 3 || i < stage" name="check" :size="13" />{{ stage > 3 || i < stage ? '' : i + 1 }}</b>{{ s }}
      </li>
    </template>
  </ol>
</template>

<style scoped>
.steps {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 0 18px;
  padding: 0;
  list-style: none;
}

li {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--color-text-weak);
  transition: color 300ms;
}

li b {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #f1f5f9;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-text-weak);
  transition:
    background 300ms,
    color 300ms;
}

li b svg {
  stroke-width: 3;
}

li.dn {
  color: var(--color-text-sub);
}

li.dn b {
  background: var(--color-level-normal);
  color: #fff;
}

li.on {
  font-weight: 700;
  color: var(--color-primary);
}

li.on b {
  background: var(--color-primary);
  color: #fff;
  animation: cur-pulse 1.6s ease-in-out infinite;
}

/* 연결선: 지나간 구간은 초록 */
.line {
  width: 28px;
  border-top: 2px solid #e2e8f0;
  transition: border-color 300ms;
}

.line.dn {
  border-color: var(--color-level-normal);
}

@keyframes cur-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.4);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(37, 99, 235, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  li.on b {
    animation: none;
  }
}
</style>
