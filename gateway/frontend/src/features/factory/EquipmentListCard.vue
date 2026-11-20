<!--
  설비 목록 (화면설계안 vLayout() #lc)
  - 설비마다 이름 · 종류 · 센서 수, 사용 안 함이면 흐리게, 바뀐 설비는 파란 점 '변경'
  - 누르면 select(설비 id) → 배치도·오른쪽 정보가 그 설비로
  - 설비가 없으면(막 추가한 공정) '아직 설비가 없습니다'
-->
<script setup>
import { typeLabel } from './layoutDraft'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'

defineProps({
  /** [{ id, key, name, type, active, sensors, changed }] (type: DB equipment_type 코드) */
  rows: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
  sub: { type: String, default: '' },
})

defineEmits(['select'])
</script>

<template>
  <BaseCard title="설비 목록" :sub="sub">
    <div class="list">
      <p v-if="!rows.length" class="empty"><AppIcon name="layers" :size="22" />아직 설비가 없습니다</p>
      <button
        v-for="r in rows"
        :key="r.id"
        type="button"
        class="er"
        :class="{ on: r.id === selectedId, off: !r.active }"
        @click="$emit('select', r.id)"
      >
        <span class="tx">
          <b>{{ r.name }}</b>
          <small>{{ typeLabel(r.type) }} · 센서 {{ r.sensors.length }}개{{ r.active ? '' : ' · 사용 안 함' }}</small>
        </span>
        <span v-if="r.changed" class="chg"><i />변경</span>
        <AppIcon v-else name="cr" :size="14" class="go" />
      </button>
    </div>
  </BaseCard>
</template>

<style scoped>
.list {
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
