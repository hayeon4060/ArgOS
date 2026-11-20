<!--
  채팅 글 목록 (화면설계안 rev-shell.js chatPanel() .cpm, 화면설계서 I2 ②)
  - 날짜 줄 + 말풍선: 내 글은 오른쪽 파란 바탕, 상대 글은 왼쪽 흰 바탕, 옆에 보낸 시각
  - 알림 카드가 붙은 글: 말풍선 안 위쪽에 단계 색 막대 · 제목 · 설비
  - 보내는 중: 흐리게 → 보내지면 진해짐. 실패: 빨간 '다시 보내기'
  - 새 글은 아래에서 8px 올라오며 나타남(0.2초). 맨 아래를 보고 있을 때만 자동으로 내려가고,
    위쪽을 보고 있으면 '새 메시지 ↓' 단추
  - 맨 위 '이전 메시지 더 보기' (hasMore 일 때, 30개씩)
-->
<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** [{ id, mine, body, alarm, sentAt, read, temp?, pending?, failed? }] */
  items: { type: Array, required: true },
  hasMore: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  /** 방이 바뀌면 바뀌는 값 (맨 아래로 내려가기) */
  roomKey: { type: [Number, String], default: null },
})

const emit = defineEmits(['older', 'retry'])

const WEEK = ['일', '월', '화', '수', '목', '금', '토']
const dayOf = (iso) => iso.slice(0, 10)
const dayLabel = (iso) => `${dayOf(iso)} (${WEEK[new Date(`${dayOf(iso)}T00:00:00`).getDay()]})`
const hm = (iso) => {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 날짜가 바뀌는 글 앞에 날짜 줄 */
const rows = computed(() =>
  props.items.map((m, i) => ({ m, day: i === 0 || dayOf(props.items[i - 1].sentAt) !== dayOf(m.sentAt) ? dayLabel(m.sentAt) : '' })),
)

/* ───────── 아래로 내려가기 ───────── */
const box = ref(null)
const atBottom = ref(true)
const newBelow = ref(false)

function onScroll() {
  const el = box.value
  if (!el) return // 패널이 닫히는 중
  atBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 40
  if (atBottom.value) newBelow.value = false
}
function toBottom() {
  const el = box.value
  if (el) el.scrollTop = el.scrollHeight
  newBelow.value = false
}

// 처음 · 방을 바꿨을 때는 맨 아래로
watch(
  () => props.roomKey,
  () => nextTick(toBottom),
  { immediate: true },
)
// 맨 아래 글이 바뀌면(새 글): 맨 아래를 보고 있거나 내가 보낸 글이면 내려감, 아니면 '새 메시지 ↓'
// (위에 이전 글이 붙을 때는 맨 아래 글이 그대로라 자리도 그대로)
watch(
  () => props.items.at(-1),
  (last, before) => {
    if (!last || last.id === before?.id) return // 다시 불러와 같은 글이면 그대로
    if (last.mine || atBottom.value) nextTick(toBottom)
    else newBelow.value = true
  },
)

const levelClass = (lv) => (lv === 'ALARM' || lv === 'FIRE' ? 'dng' : 'warn')
</script>

<template>
  <div class="wrap">
    <div ref="box" class="cpm" @scroll="onScroll">
      <button v-if="hasMore" type="button" class="older" :disabled="loading" @click="emit('older')">
        {{ loading ? '불러오는 중' : '이전 메시지 더 보기' }}
      </button>
      <template v-for="{ m, day } in rows" :key="m.id">
        <div v-if="day" class="day mono">{{ day }}</div>
        <div class="msg" :class="{ me: m.mine, pending: m.pending }">
          <span class="bb">
            <span v-if="m.alarm" class="alc" :class="levelClass(m.alarm.level)">
              <AppIcon :name="levelClass(m.alarm.level) === 'dng' ? 'alert' : 'tri'" :size="16" />
              <span>
                <!-- 지워진 알림이면 서버가 설비 이름 없이 '알림 #번호'만 줌 -->
                <b>{{ [m.alarm.title, m.alarm.equipmentName].filter(Boolean).join(' · ') }}</b>
                <small v-if="m.alarm.sensorTag" class="mono">{{ m.alarm.sensorTag }} {{ m.alarm.value }} {{ m.alarm.unit }}</small>
              </span>
            </span>
            {{ m.body }}
          </span>
          <button v-if="m.failed" type="button" class="retry" aria-label="다시 보내기" title="보내지 못했습니다. 다시 보내기" @click="emit('retry', m)">
            <AppIcon name="redo" :size="15" />
          </button>
          <span v-else class="tm mono">
            <template v-if="m.mine && !m.pending && m.read">읽음 · </template>{{ m.pending ? '보내는 중' : hm(m.sentAt) }}
          </span>
        </div>
      </template>
      <p v-if="!items.length && !loading" class="none">아직 주고받은 메시지가 없습니다.</p>
    </div>

    <Transition name="nb">
      <button v-if="newBelow" type="button" class="nb" @click="toBottom">새 메시지 <AppIcon name="cd" :size="14" /></button>
    </Transition>
  </div>
</template>

<style scoped>
.wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
}

.cpm {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  background: #f8fafc;
}

.day {
  align-self: center;
  font-size: 11.5px;
  color: var(--color-text-weak);
}

.older {
  align-self: center;
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: #fff;
  font-size: 12px;
  color: var(--color-text-sub);
  cursor: pointer;
}

.msg {
  max-width: 88%;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  animation: msg-in 200ms ease-out both;
}

/* 새 글: 아래에서 8px 올라오며 (화면설계서 I2) */
@keyframes msg-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
}

.msg.me {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.bb {
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px 12px 12px 3px;
  background: #fff;
  font-size: 13.5px;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-word;
  transition: opacity 200ms;
}

.me .bb {
  border-color: var(--color-primary);
  border-radius: 12px 12px 3px 12px;
  background: var(--color-primary);
  color: #fff;
}

/* 보내는 중: 흐리게 → 보내지면 진해짐 */
.pending .bb {
  opacity: 0.55;
}

.tm {
  flex: none;
  white-space: nowrap;
  font-size: 10.5px;
  color: var(--color-text-weak);
}

.retry {
  flex: none;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--color-level-alarm-soft, #fef2f2);
  color: var(--color-level-alarm);
  cursor: pointer;
}

/* 붙인 알림 카드 (설계안 .alc) */
.alc {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  padding: 8px 10px;
  border-left: 3px solid var(--color-level-warn);
  border-radius: 6px;
  background: #fff;
  color: var(--color-text);
  white-space: normal;
}

.alc.dng {
  border-left-color: var(--color-level-alarm);
}

.alc svg {
  flex: none;
  color: var(--color-level-warn-text);
}

.alc.dng svg {
  color: var(--color-level-alarm-text);
}

.alc b {
  display: block;
  font-size: 12.5px;
}

.alc small {
  font-size: 11px;
  color: var(--color-text-weak);
}

.me .alc {
  border-top: 1px solid #dbeafe;
  border-right: 1px solid #dbeafe;
  border-bottom: 1px solid #dbeafe;
}

.none {
  margin: auto;
  font-size: 13px;
  color: var(--color-text-weak);
}

.nb {
  position: absolute;
  left: 50%;
  bottom: 12px;
  height: 30px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  border: none;
  border-radius: 15px;
  background: var(--color-primary);
  color: #fff;
  font-size: 12.5px;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
  transform: translateX(-50%);
  cursor: pointer;
}

.nb-enter-active,
.nb-leave-active {
  transition:
    opacity 150ms,
    transform 150ms;
}

.nb-enter-from,
.nb-leave-to {
  opacity: 0;
  transform: translate(-50%, 6px);
}

@media (prefers-reduced-motion: reduce) {
  .msg {
    animation: none;
  }
}
</style>
