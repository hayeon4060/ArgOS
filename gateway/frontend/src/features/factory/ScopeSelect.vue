<!--
  공장 관리 범위 고르기 드롭다운 (사업장 · 공장 · 공정 하나씩)
  - 2026-11-20 재원님 요청: 공장 · 공정 드롭다운 맨 아래에 '공장 추가' · '공정 추가' 줄
    (화면설계안은 드롭다운 옆 '+ 추가' 단추 하나였음 → 요청대로 드롭다운 안으로 옮김)
  - 브라우저 기본 <select> 는 맨 아래 '추가' 줄을 단추처럼 꾸밀 수 없고, 목록이 비면 고를 수도 없어서 직접 만듦
  - 목록이 비었으면 단추 자체가 '+ 공장 추가' → 누르면 바로 add
  - 펼친 목록은 단추 바로 아래(position: absolute) — 화면 배율(CSS zoom)에도 자리가 맞음
  - 바깥을 누르거나 Esc 면 닫힘 (utils/useOutsideClose.js)
  - 사용 예: <ScopeSelect v-model="pick.factoryId" :options="factories" add-label="공장 추가" can-add @change="..." @add="..." />
-->
<script setup>
import { computed, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useOutsideClose } from '@/utils/useOutsideClose'

const props = defineProps({
  /** 고른 id (문자열) */
  modelValue: { type: String, default: '' },
  /** [{ id, name }] */
  options: { type: Array, default: () => [] },
  /** 화면 읽기 프로그램용 이름 (예: '공장') */
  label: { type: String, required: true },
  /** 맨 아래 추가 줄 글자 (예: '공장 추가'), canAdd 일 때만 보임 */
  addLabel: { type: String, default: '' },
  canAdd: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  /** 목록이 비었고 추가도 못할 때 보이는 글자 */
  placeholder: { type: String, default: '없음' },
})

const emit = defineEmits(['update:modelValue', 'change', 'add'])

const open = ref(false)
const root = ref(null)
useOutsideClose(root, open)

const current = computed(() => props.options.find((o) => String(o.id) === props.modelValue) ?? null)
const empty = computed(() => !props.options.length)

/** 단추 누름: 목록이 비었으면 바로 추가, 아니면 펼치기 */
function onButton() {
  if (props.disabled) return
  if (empty.value && props.canAdd) emit('add')
  else if (!empty.value) open.value = !open.value
}

function choose(option) {
  open.value = false
  const id = String(option.id)
  if (id === props.modelValue) return
  emit('update:modelValue', id)
  emit('change', id)
}

function add() {
  open.value = false
  emit('add')
}
</script>

<template>
  <div ref="root" class="scope-select">
    <button
      type="button"
      class="trigger"
      :class="{ adder: empty && canAdd, open }"
      :disabled="disabled || (empty && !canAdd)"
      :aria-label="label"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="onButton"
    >
      <template v-if="empty && canAdd"><AppIcon name="plus" :size="15" />{{ addLabel }}</template>
      <template v-else>
        <span class="tx">{{ current?.name ?? placeholder }}</span>
        <AppIcon name="cd" :size="14" class="chev" />
      </template>
    </button>

    <Transition name="menu-drop">
      <div v-if="open" class="menu" role="listbox" :aria-label="label">
        <button
          v-for="o in options"
          :key="o.id"
          type="button"
          class="opt"
          :class="{ on: String(o.id) === modelValue }"
          role="option"
          :aria-selected="String(o.id) === modelValue"
          @click="choose(o)"
        >
          <span class="tx">{{ o.name }}</span>
          <AppIcon v-if="String(o.id) === modelValue" name="check" :size="15" class="ck" />
        </button>
        <!-- 맨 아래 추가 줄 -->
        <template v-if="canAdd">
          <div class="div" />
          <button type="button" class="opt add" @click="add"><AppIcon name="plus" :size="15" />{{ addLabel }}</button>
        </template>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.scope-select {
  position: relative;
}

/* 단추: 예전 <select> 와 같은 높이 · 테두리 */
.trigger {
  height: 36px;
  min-width: 120px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13.5px;
  color: var(--color-text);
  cursor: pointer;
  transition: border-color var(--m-hover), box-shadow var(--m-hover);
}

.trigger:hover:not(:disabled),
.trigger.open {
  border-color: var(--color-primary);
}

.trigger.open {
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.trigger:disabled {
  background: var(--color-surface-sub);
  color: var(--color-text-sub);
  cursor: default;
}

.tx {
  flex: 1;
  max-width: 200px;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chev {
  color: var(--color-text-weak);
}

/* 목록이 비었을 때: 파란 점선 '+ 공장 추가' */
.trigger.adder {
  gap: 6px;
  border-style: dashed;
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
  font-weight: 600;
}

.trigger.adder:disabled {
  border-color: var(--color-border-strong);
  background: var(--color-surface-sub);
  color: var(--color-text-weak);
}

/* 펼친 목록 */
.menu {
  position: absolute;
  left: 0;
  top: calc(100% + 4px);
  z-index: 20;
  min-width: 100%;
  width: max-content;
  max-width: 280px;
  max-height: 360px;
  overflow-y: auto;
  padding: 6px;
  background: #fff;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.opt {
  width: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  font-size: 13.5px;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}

.opt:hover {
  background: var(--color-surface-sub);
}

.opt.on {
  font-weight: 600;
  color: var(--color-primary-hover);
}

.ck {
  color: var(--color-primary);
}

.div {
  height: 1px;
  margin: 6px 4px;
  background: var(--color-border);
}

.opt.add {
  gap: 6px;
  font-weight: 600;
  color: var(--color-primary);
}

.opt.add:hover {
  background: var(--color-primary-soft);
}
</style>
