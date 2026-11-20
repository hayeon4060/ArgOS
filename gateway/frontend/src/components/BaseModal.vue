<!--
  창(모달) 틀 (화면설계안 js/core.js openModal(), css/extra.css .modal · .dim)
  - 어두운 배경 + 가운데 흰 상자. 화면 전체를 덮음(position: fixed)
    → 화면 전환 확대·축소, 본문 잘림(overflow)에 영향 받지 않게 body 아래에서 그려야 함
      (알림 창은 features/alert/AlertHost.vue 가 통째로 body 로 옮겨 그림)
  - 나타남: 배경 투명도 .22s, 상자 아래 14px · 96% 에서 .3s
    사라짐: 부모가 <Transition name="modal" :duration="{ enter: 300, leave: 200 }"> 로 감싸면 .18s (화면설계서 R2 --m-modal)
  - lock: 바깥 누르기·Esc 로 안 닫힘 → 대신 상자가 좌우로 흔들림 (주의 알림처럼 꼭 처리해야 하는 창)
  - tone: 'warn' 이면 상자 둘레 주황 빛 (설계안 .modal.warnm), 'alarm' 이면 빨간 빛 (설계안 .modal.alarm)
  - redDim: 어두운 배경을 붉게 (화재, 설계안 .dim.red)
  - 사용 예: <BaseModal :width="520" lock tone="warn" @close="..."> 내용 </BaseModal>
    (열고 닫기는 부모가 v-if 로, 사라지는 움직임은 부모의 <Transition name="modal"> 이 담당)
-->
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  width: { type: Number, required: true },
  lock: { type: Boolean, default: false },
  tone: { type: String, default: '' },
  /** 화면 읽기 프로그램용 제목 */
  label: { type: String, default: '' },
  redDim: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])

const box = ref(null)

/** 닫기 요청: lock 이면 흔들기만 */
function requestClose() {
  if (!props.lock) {
    emit('close')
    return
  }
  box.value?.classList.remove('shake')
  void box.value?.offsetWidth // 흔들기 애니메이션을 다시 시작하려고 한 번 그리게 함
  box.value?.classList.add('shake')
}

function onKey(e) {
  if (e.key === 'Escape') requestClose()
}

onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="modal-root">
    <div class="dim" :class="{ red: redDim }" @click="requestClose" />
    <div
      ref="box"
      class="box"
      :class="tone ? `tone-${tone}` : ''"
      :style="{ width: `${width}px` }"
      role="dialog"
      aria-modal="true"
      :aria-label="label"
    >
      <slot :close="requestClose" />
    </div>
  </div>
</template>

<style scoped>
/* 상단 바(10)·안내 문구(20) 위, 알림 띠(150) 아래 */
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 24px;
}

.dim {
  position: absolute;
  inset: 0;
  background: rgba(17, 24, 39, 0.5);
  animation: softin 220ms ease-out both; /* base.css */
}

.box {
  position: relative;
  max-width: 100%;
  max-height: calc(var(--screen-h) - 48px); /* 화면 높이 (배율 반영, tokens.css) */
  overflow: auto;
  border-radius: 10px;
  background: #fff;
  box-shadow:
    0 30px 80px rgba(15, 23, 42, 0.35),
    0 4px 14px rgba(15, 23, 42, 0.16);
  animation: modal-in 300ms cubic-bezier(0.2, 0.9, 0.3, 1.05) both;
}

.dim.red {
  background: rgba(127, 29, 29, 0.5);
}

.box.tone-alarm {
  box-shadow:
    0 0 0 4px rgba(220, 38, 38, 0.55),
    0 30px 80px rgba(127, 29, 29, 0.45);
}

.box.tone-warn {
  box-shadow:
    0 0 0 4px rgba(249, 115, 22, 0.5),
    0 30px 80px rgba(15, 23, 42, 0.35);
}

.box.shake {
  animation: shake 400ms ease-in-out; /* base.css */
}

/* 사라질 때 (부모의 <Transition name="modal">) */
.modal-root.modal-leave-active .dim {
  animation: fadeout 200ms ease-in both;
}

.modal-root.modal-leave-active .box {
  animation: modal-out 180ms ease-in both;
}

@keyframes fadeout {
  to {
    opacity: 0;
  }
}

@keyframes modal-out {
  to {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
}

@keyframes modal-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .box {
    animation-name: softin;
  }

  .box.shake {
    animation: none;
  }
}
</style>
