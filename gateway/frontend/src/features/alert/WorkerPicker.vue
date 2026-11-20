<!--
  체크리스트 받을 공정 담당자 고르기 (설계안 mWarnDetail() '전송 대상', 화면설계서 C2 ⑦)
  - GET /processes/{id}/workers 목록: 이름, '도장 공정 담당자 · 모바일 앱', 근무 중 / 근무 외
  - 한 명만 고름. 근무 중이 아닌 사람도 고를 수 있음 (화면설계서: 확인 창을 거침 → 지금은 주황 안내 글자만)
  - except: 담당자 변경 때 지금 담당자는 빼고 보여 줌
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** [{ id, name, onDuty }] */
  workers: { type: Array, required: true },
  /** 공정 이름 ('도장 공정') — 알림 위치에서 꺼내 넘겨 줌 */
  processName: { type: String, default: '' },
  except: { type: Number, default: null },
})

const selected = defineModel({ type: Number, default: null })

const list = computed(() => props.workers.filter((w) => w.id !== props.except))
const offDutyPicked = computed(() => list.value.find((w) => w.id === selected.value)?.onDuty === false)
</script>

<template>
  <div>
    <div class="m-box options" role="radiogroup">
      <label v-for="w in list" :key="w.id" class="who" :class="{ on: selected === w.id }">
        <input v-model="selected" type="radio" :value="w.id" name="worker" />
        <span class="av"><AppIcon name="user" :size="18" /></span>
        <span class="name">
          <b>{{ w.name }}</b>
          <small>{{ processName ? `${processName} 담당자` : '공정 담당자' }} · 모바일 앱</small>
        </span>
        <span class="m-chip" :class="w.onDuty ? 'ok' : 'gray'">{{ w.onDuty ? '근무 중' : '근무 외' }}</span>
      </label>
    </div>
    <p v-if="offDutyPicked" class="off">근무 중이 아닌 담당자입니다. 앱 알림을 바로 못 볼 수 있습니다.</p>
  </div>
</template>

<style scoped>
.options {
  overflow: hidden;
}

/* 한 줄: 동그란 사람 그림 + 이름 + 근무 배지 (설계안 .who) */
.who {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background var(--m-hover);
}

.who:last-child {
  border-bottom: none;
}

.who:hover {
  background: #f8fafc;
}

.who.on {
  background: var(--color-primary-soft);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.who input {
  accent-color: var(--color-primary);
}

.av {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-border);
  color: var(--color-text-sub);
}

.name {
  flex: 1;
}

.name b {
  display: block;
  font-size: 15px;
}

.name small {
  font-size: 12.5px;
  color: #64748b;
}

.off {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--color-level-warn-text);
}
</style>
