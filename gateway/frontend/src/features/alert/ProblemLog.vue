<!--
  경보 창 '문제 로그' / 화재 창 '자동 조치' (화면설계안 mAlarm() 오른쪽, mFire() 오른쪽)
  - 경보: 주의 발생부터의 기록을 시간순(최근 위)으로, 세로 선 위의 점 (설비 상세 이벤트 이력과 같은 모양)
  - 화재: 자동 조치 목록과 상태 칩(송출 중 / 완료 / 대기), 한 줄씩 차례로 나타남(.22초 간격)
-->
<script setup>
defineProps({
  /** 경보: response.logs [{ at, text, detail, level, type }] */
  logs: { type: Array, default: () => [] },
  /** 화재: fire.actions [{ step, at, done, text, running }] */
  actions: { type: Array, default: null },
})

const DOT = { WARN: '#f97316', ALARM: '#dc2626', FIRE: '#dc2626', NORMAL: '#94a3b8' }
const dot = (l) => (l.type === 'ACTION' ? '#2563eb' : (DOT[l.level] ?? '#94a3b8'))
const hm = (iso) => iso?.slice(11, 19) ?? ''

const chipOf = (a) => (a.running ? { cls: 'alarm', text: '송출 중' } : a.done ? { cls: 'ok', text: '완료' } : { cls: 'gray', text: '대기' })
</script>

<template>
  <div v-if="actions" class="m-box acts">
    <div v-for="(a, i) in actions" :key="a.step" class="act" :style="{ animationDelay: `${300 + i * 220}ms` }">
      <span>{{ a.text }}</span>
      <span class="mono at">{{ hm(a.at) }}</span>
      <span class="m-chip" :class="chipOf(a).cls">{{ chipOf(a).text }}</span>
    </div>
  </div>

  <div v-else class="m-box tlg">
    <div v-for="l in logs" :key="l.at + l.text" class="e">
      <span class="d" :style="{ color: dot(l) }" />
      <span class="tm mono">{{ hm(l.at) }}</span>
      <div class="tx">
        {{ l.text }}<small>{{ l.detail }}</small>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 자동 조치 */
.acts {
  padding: 6px 16px;
}

.act {
  height: 45px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px solid #f1f5f9;
  font-size: 14px;
  animation: softin 300ms ease-out both; /* base.css: 차례로 나타남 */
}

.act:first-child {
  border-top: none;
}

.at {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-weak);
}

.act .m-chip {
  width: 62px;
  justify-content: center;
}

/* 문제 로그 (설계안 .tlg) */
.tlg {
  height: 100%;
  padding: 16px 14px 4px;
  overflow-y: auto;
}

.e {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 18px;
}

.e::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: -2px;
  width: 2px;
  background: var(--color-border);
}

.e:last-child::before {
  display: none;
}

.d {
  position: relative;
  z-index: 1;
  flex: none;
  width: 12px;
  height: 12px;
  margin-top: 4px;
  border: 2.5px solid #fff;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 1.5px currentColor;
}

.tm {
  flex: none;
  width: 62px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.tx {
  font-size: 13px;
  line-height: 19px;
}

.tx small {
  display: block;
  font-size: 12px;
  color: var(--color-text-sub);
}
</style>
