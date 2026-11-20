<!--
  설비 추가 창 — 지금 있는 설비 목록에서 골라 배치도에 놓기 (2026-11-20 재원님 요청)
  - 목록: equipmentCatalog.js (도장 공정 설비 · 보조 설비)
  - 여러 개를 골라 한 번에 추가, '모두 선택' 가능
  - 추가 단추 → pick([{ name, type }]) — 실제로 놓고 저장하는 일은 화면(FactoryPage)이 함
  - 추가한 설비는 저장 전까지 '새 설비'로 남고, 배치도의 빈 칸에 차례로 놓임 (끌어서 옮기면 됨)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { EQUIPMENT_CATALOG } from './equipmentCatalog'
import { typeLabel } from './layoutDraft'
import AppCheckbox from '@/components/AppCheckbox.vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 어디에 추가하는지 (예: '창원공장 › 1공장 › 도장 공정') */
  path: { type: String, default: '' },
})

const emit = defineEmits(['pick', 'cancel'])

const ALL = EQUIPMENT_CATALOG.flatMap((g) => g.items)
const chosen = ref(new Set()) // 고른 설비 이름

// 열 때마다 고른 것 비우기
watch(
  () => props.open,
  (open) => open && (chosen.value = new Set()),
)

function toggle(name) {
  const next = new Set(chosen.value)
  next.has(name) ? next.delete(name) : next.add(name)
  chosen.value = next
}

const allOn = computed(() => chosen.value.size === ALL.length)
function toggleAll() {
  chosen.value = allOn.value ? new Set() : new Set(ALL.map((i) => i.name))
}

function submit() {
  // 목록 순서대로 (공정 흐름 순)
  emit('pick', ALL.filter((i) => chosen.value.has(i.name)))
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="520" label="설비 추가" @close="emit('cancel')">
        <div class="picker">
          <div class="head">
            <h2>설비 추가</h2>
            <span v-if="path" class="sub">{{ path }}</span>
          </div>
          <p class="desc">지금 있는 설비 중에서 고르세요. 배치도의 빈 자리에 놓이며, 끌어서 옮길 수 있습니다.</p>

          <div class="bar">
            <AppCheckbox :model-value="allOn" @update:model-value="toggleAll">모두 선택</AppCheckbox>
            <span class="count">{{ chosen.size }}개 고름</span>
          </div>

          <div class="list">
            <section v-for="g in EQUIPMENT_CATALOG" :key="g.group">
              <h4>{{ g.group }}</h4>
              <button
                v-for="item in g.items"
                :key="item.name"
                type="button"
                class="row"
                :class="{ on: chosen.has(item.name) }"
                @click="toggle(item.name)"
              >
                <span class="ck"><AppIcon v-if="chosen.has(item.name)" name="ok" :size="12" /></span>
                <b>{{ item.name }}</b>
                <small>{{ typeLabel(item.type) }}</small>
              </button>
            </section>
          </div>

          <div class="m-actions">
            <button type="button" class="btn" @click="emit('cancel')">취소</button>
            <button type="button" class="btn btn-primary" :disabled="!chosen.size" @click="submit">
              <AppIcon name="plus" :size="16" />{{ chosen.size ? `${chosen.size}개 추가` : '추가' }}
            </button>
          </div>
        </div>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.picker {
  padding: 24px 24px 20px;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

h2 {
  font-size: 18px;
  font-weight: 700;
}

.sub {
  font-size: 13.5px;
  color: var(--color-text-sub);
}

.desc {
  margin: 8px 0 0;
  font-size: 13.5px;
  color: var(--color-text-sub);
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border);
  font-size: 13.5px;
}

.count {
  font-size: 13px;
  color: var(--color-text-sub);
}

.list {
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 0;
}

h4 {
  margin: 14px 0 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text-weak);
}

.row {
  width: 100%;
  min-height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  font-size: 14px;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}

.row:hover {
  background: var(--color-surface-sub);
}

.row.on {
  background: var(--color-primary-soft);
}

.ck {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--color-border-strong);
  border-radius: 4px;
  background: #fff;
  color: #fff;
}

.row.on .ck {
  border-color: var(--color-primary);
  background: var(--color-primary);
}

.row b {
  font-weight: 600;
}

.row small {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.m-actions {
  justify-content: flex-end;
  margin-top: 18px;
}

.m-actions .btn {
  height: 40px;
  padding: 0 18px;
}
</style>
