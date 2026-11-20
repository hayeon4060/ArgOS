<!--
  카드 틀 (화면설계안 css/base.css 의 .card / .ch 모양)
  - 제목줄: 제목(title 또는 title 슬롯) + 회색 설명(sub) + 오른쪽 끝 영역(actions 슬롯)
  - 화면에 나타날 때 아래에서 올라오며 차례로 나타남 (화면설계서 R2, motion.css 의 .rise)
  - 사용 예:
    <BaseCard title="사업장 목록" sub="3곳">
      <template #actions><button>...</button></template>
      본문
    </BaseCard>
-->
<script setup>
import { onMounted, ref } from 'vue'

defineProps({
  /** 카드 제목 (없으면 제목줄을 그리지 않음) */
  title: { type: String, default: '' },
  /** 제목 옆 회색 설명 */
  sub: { type: String, default: '' },
})

/**
 * 차례 번호(--i): 같은 화면 안 카드들 중 몇 번째인지 (위→아래, 왼쪽→오른쪽)
 * - 카드들이 같은 순간에 그려지므로, 그려진 직후 화면 안 카드 위치로 순서를 정함
 */
const el = ref(null)
onMounted(() => {
  const page = el.value.closest('.page') ?? document
  const order = [...page.querySelectorAll('.base-card')]
    .map((card) => ({ card, rect: card.getBoundingClientRect() }))
    .sort((a, b) => a.rect.top - b.rect.top || a.rect.left - b.rect.left)
    .map(({ card }) => card)
  el.value.style.setProperty('--i', order.indexOf(el.value))
})
</script>

<template>
  <section ref="el" class="base-card rise">
    <header v-if="title || $slots.title" class="card-head">
      <!-- 제목: 글자만이면 title, 경로(CardCrumb) 등이 필요하면 title 슬롯 -->
      <h3><slot name="title">{{ title }}</slot></h3>
      <!-- 설명: 글자만이면 sub, 링크 등이 필요하면 sub 슬롯 -->
      <span v-if="sub || $slots.sub" class="sub"><slot name="sub">{{ sub }}</slot></span>
      <div v-if="$slots.actions" class="actions"><slot name="actions" /></div>
    </header>
    <slot />
  </section>
</template>

<style scoped>
.base-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.card-head {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

h3 {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.2px;
}

.sub {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis; /* 좁은 화면에서 오른쪽 영역과 겹치지 않게 설명이 먼저 '…'로 줄어듦 */
  font-size: 13px;
  color: var(--color-text-weak);
}

.actions {
  flex: none;
}

.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--color-text-sub);
}
</style>
