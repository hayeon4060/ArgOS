<!--
  뼈대 칸 하나 (불러오는 중 자리 표시)
  - 회색 칸 위로 밝은 띠가 왼쪽 → 오른쪽으로 1.4초마다 지나감 (transform 으로만 움직임)
  - 색·시간은 tokens.css 의 --color-skeleton* · --m-skeleton-shine
  - '움직임 줄이기' 설정이면 motion.css 가 띠를 멈추고 고정 회색만 남김
  - 크기는 쓰는 쪽이 정함 (PageSkeleton 의 격자 칸을 꽉 채움)
  - 사용: <SkeletonBlock /> (화면 낭독기에는 읽히지 않음 — 안내 글자는 PageSkeleton 이 가짐)
-->
<template>
  <div class="sk-block" aria-hidden="true" />
</template>

<style scoped>
.sk-block {
  position: relative;
  overflow: hidden; /* 띠가 칸 밖으로 나가지 않게 */
  min-width: 0;
  min-height: 0;
  background: var(--color-skeleton);
  border-radius: var(--radius-sm);
}

/* 밝은 띠: 칸 폭만큼 왼쪽 밖(-100%)에서 오른쪽 밖(100%)으로 */
.sk-block::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg, transparent 30%, var(--color-skeleton-shine) 50%, transparent 70%);
  transform: translateX(-100%);
  animation: sk-shine var(--m-skeleton-shine) ease-in-out infinite;
  will-change: transform;
}

@keyframes sk-shine {
  to {
    transform: translateX(100%);
  }
}
</style>
