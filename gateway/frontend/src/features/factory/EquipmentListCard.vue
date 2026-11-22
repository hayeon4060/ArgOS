<!--
  설비 목록 (화면설계안 vLayout() #lc)
  - 설비마다 이름 · 종류 · 센서 수, 사용 안 함이면 흐리게, 바뀐 설비는 파란 점 '변경'
  - 누르면 select(설비 id) → 배치도·오른쪽 정보가 그 설비로
  - 설비가 없으면(막 추가한 공정) '아직 설비가 없습니다'
  - 목록 맨 아래 '+ 설비 추가' (add) (2026-11-20)
  - 새 설비(저장 전)는 '새 설비' 표시 + 빼기 단추(remove)
  - 지울 설비(저장 전, deleting)는 줄을 긋고 '삭제 예정' — 지우기 · 되살리기는 설비 정보 카드에서
  - 2026-11-22 (재원님 요청): 설비가 많으면 카드 아래가 잘리던 것
    → 카드 높이는 옆 카드(배치도)와 같게 두고 설비 줄만 세로 스크롤, 제목줄 · '설비 추가'는 그대로 보임
    → 배치도에서 설비를 고르거나 새 설비를 놓으면 그 줄이 보이게 목록을 굴림
-->
<script setup>
import { nextTick, ref, watch } from 'vue'
import { typeLabel } from './layoutDraft'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'

const props = defineProps({
  /** [{ id, key, name, type, active, sensors, changed }] (type: DB equipment_type 코드) */
  rows: { type: Array, default: () => [] },
  selectedId: { type: [Number, String], default: null }, // 새 설비(저장 전)는 임시 id 'n1'
  sub: { type: String, default: '' },
})

defineEmits(['select', 'add', 'remove'])

// 고른 설비 줄이 스크롤 밖이면 보이는 곳까지만 목록을 굴림 (이미 보이면 그대로)
// scrollIntoView 는 화면 전체까지 굴려서(1600px 이하는 목록이 아래 줄) 목록 안에서만 직접 맞춤
const listEl = ref(null)
watch(
  () => props.selectedId,
  async () => {
    await nextTick()
    const box = listEl.value
    const row = box?.querySelector('.er.on')
    if (!row) return
    const top = row.offsetTop // .list 가 position: relative 라 목록 안 위치
    if (top < box.scrollTop) box.scrollTop = top
    else if (top + row.offsetHeight > box.scrollTop + box.clientHeight) box.scrollTop = top + row.offsetHeight - box.clientHeight
  },
)
</script>

<template>
  <BaseCard title="설비 목록" :sub="sub">
    <div ref="listEl" class="list">
      <p v-if="!rows.length" class="empty"><AppIcon name="layers" :size="22" />아직 설비가 없습니다</p>
      <button
        v-for="r in rows"
        :key="r.id"
        type="button"
        class="er"
        :class="{ on: r.id === selectedId, off: !r.active, del: r.deleting }"
        @click="$emit('select', r.id)"
      >
        <span class="tx">
          <b>{{ r.name }}</b>
          <small>{{ typeLabel(r.type) }} · 센서 {{ r.sensors.length }}개{{ r.active ? '' : ' · 사용 안 함' }}</small>
        </span>
        <template v-if="r.isNew">
          <span class="chg"><i />새 설비</span>
          <span class="rm" role="button" tabindex="0" :aria-label="`${r.name} 빼기`" @click.stop="$emit('remove', r.id)" @keydown.enter.stop="$emit('remove', r.id)">
            <AppIcon name="x" :size="14" />
          </span>
        </template>
        <span v-else-if="r.deleting" class="chg gone"><i />삭제 예정</span>
        <span v-else-if="r.changed" class="chg"><i />변경</span>
        <AppIcon v-else name="cr" :size="14" class="go" />
      </button>
    </div>
    <button type="button" class="add" @click="$emit('add')"><AppIcon name="plus" :size="16" />설비 추가</button>
  </BaseCard>
</template>

<style scoped>
/*
 * 카드 = 제목줄(고정) + 설비 줄 + '설비 추가'(고정)
 * - 설비가 적으면 예전처럼 '설비 추가'가 마지막 줄 바로 아래
 * - 카드 높이를 넘으면 설비 줄만 줄어들고 세로 스크롤 ('설비 추가'는 카드 맨 아래에 계속 보임)
 */
.base-card {
  display: flex;
  flex-direction: column;
}

.list {
  position: relative;
  flex: 0 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 6px 0;
}

.empty {
  height: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 0;
  font-size: 13.5px;
  color: var(--color-text-sub);
}

.empty .app-icon {
  color: var(--color-text-weak);
}

/* 맨 아래 '설비 추가' (설계안 vLayout() #ladd: 점선 줄) */
.add {
  flex: none;
  width: calc(100% - 32px);
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 12px 16px;
  border: 1px dashed var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-primary);
  cursor: pointer;
}

.add:hover {
  background: #dbeafe;
}

/* 지울 설비: 이름에 줄, 빨간 '삭제 예정' */
.er.del b {
  text-decoration: line-through;
  color: var(--color-text-weak);
}

.chg.gone {
  color: var(--color-level-alarm-text);
}

.chg.gone i {
  background: var(--color-level-alarm);
}

/* 새 설비 빼기 */
.rm {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-xs);
  color: var(--color-text-weak);
}

.rm:hover {
  background: var(--color-surface-sub);
  color: var(--color-level-alarm-text);
}

.er {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 16px 11px 20px;
  border: none;
  border-bottom: 1px solid #f1f5f9;
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background var(--m-hover);
}

.er:hover {
  background: var(--color-surface-sub);
}

.er.on {
  background: var(--color-primary-soft);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.er.off .tx {
  opacity: 0.5;
}

.tx {
  flex: 1;
  min-width: 0;
}

.tx b,
.tx small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx b {
  font-size: 14px;
  font-weight: 600;
}

.tx small {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.chg {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary);
  animation: pop-in var(--m-pop-in) both;
}

.chg i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
}

.go {
  color: #cbd5e1;
}
</style>
