<!--
  서류 등록 카드 (개편안 A안 왼쪽 아래) — 수기 서류 스캔 파일 올리기
  - 파일을 끌어다 놓거나 눌러서 고르면 file(File) → 부모가 '수기 서류 등록' 창(OcrRegisterModal)을 열고 바로 올림
  - 올릴 수 있는 파일: PDF · JPG · PNG, 20MB 이하 (명세서 E1)
  - 본사 관리자는 열람만 → 흐리게, 누를 수 없음
-->
<script setup>
import { ref } from 'vue'
import { FILE_ACCEPT, FILE_MAX } from '@/api/inspections'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'

const props = defineProps({
  readOnly: { type: Boolean, default: false },
})

const emit = defineEmits(['file'])

const input = ref(null)
const over = ref(false)

function take(file) {
  if (!file || props.readOnly) return
  if (file.size > FILE_MAX) {
    toast('20MB 이하 파일만 올릴 수 있습니다.', { type: 'err' })
    return
  }
  emit('file', file)
}

function onPick(e) {
  take(e.target.files?.[0])
  e.target.value = '' // 같은 파일을 다시 골라도 바뀜이 생기게
}

function onDrop(e) {
  over.value = false
  take(e.dataTransfer?.files?.[0])
}

function choose() {
  if (!props.readOnly) input.value?.click()
}
</script>

<template>
  <BaseCard class="up-card" title="서류 등록" sub="수기 서류 스캔 파일">
    <div
      class="drop"
      :class="{ over, off: readOnly }"
      role="button"
      :tabindex="readOnly ? -1 : 0"
      :aria-disabled="readOnly"
      @click="choose"
      @keydown.enter.prevent="choose"
      @keydown.space.prevent="choose"
      @dragover.prevent="over = !readOnly"
      @dragleave="over = false"
      @drop.prevent="onDrop"
    >
      <AppIcon name="upload" :size="30" />
      <span class="txt">
        <b>스캔한 파일을 여기에 끌어다 놓기</b>
        <span>{{ readOnly ? '본사 관리자는 열람만 할 수 있습니다.' : 'PDF, JPG, PNG · 놓으면 수기 서류 등록 창이 열립니다' }}</span>
      </span>
      <span class="btn btn-sm">파일 선택</span>
    </div>
    <input ref="input" type="file" hidden :accept="FILE_ACCEPT" @change="onPick" />
  </BaseCard>
</template>

<style scoped>
.up-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.drop {
  flex: 1;
  min-height: 0;
  margin: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  border: 1.5px dashed #93c5fd;
  border-radius: var(--radius-md);
  background: #f8fbff;
  color: var(--color-primary);
  cursor: pointer;
  transition:
    background var(--m-hover),
    border-color var(--m-hover);
}

.drop:hover,
.drop.over {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.drop.off {
  opacity: 0.55;
  cursor: not-allowed;
}

.txt {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.txt b {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.txt span {
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.btn-sm {
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
}
</style>
