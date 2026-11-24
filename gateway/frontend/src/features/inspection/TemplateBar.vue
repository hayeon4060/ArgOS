<!--
  맨 위 '점검 서류 양식' 바 — 빈 양식 내려받기 (현장에서 인쇄해 손으로 쓰는 용도)
  - F02 환기·배기설비 점검일지 하나 (변전소 F03 은 2026-11-20 에 빼서 '전체 받기'도 없앰)
  - 실제: GET /doc-forms/F02/template (PDF) 를 받아 바로 저장 (getFormTemplate, 브라우저 다운로드 폴더로)
  - 예시 모드: 파일이 없어 안내만
-->
<script setup>
import { ref } from 'vue'
import { getFormTemplate } from '@/api/inspections'
import { USE_MOCK } from '@/api/mock'
import { downloadBlob } from '@/utils/saveFile'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import { FORM_LABEL, PAPER_FORM } from './inspectionMeta'

const busy = ref(false)
const f = PAPER_FORM

async function download() {
  if (busy.value) return
  busy.value = true
  try {
    const blob = await getFormTemplate(f)
    if (blob) downloadBlob(`ARG-${f}_${FORM_LABEL[f]}.pdf`, blob)
    const what = `${f} ${FORM_LABEL[f]} 빈 양식`
    toast(USE_MOCK ? `${what}을 내려받았습니다. (예시 모드라 실제 파일은 없습니다)` : `${what}을 내려받았습니다.`)
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="tplbar base-card">
    <h3><AppIcon name="file" :size="17" />점검 서류 양식</h3>
    <button type="button" class="tpl" :disabled="busy" @click="download">
      <span class="fc mono">{{ f }}</span>{{ FORM_LABEL[f] }}<AppIcon name="download" :size="15" />
    </button>
  </div>
</template>

<style scoped>
.tplbar {
  flex: none;
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px 0 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  font-size: 13.5px;
  animation: rise var(--m-rise) both; /* 카드처럼 아래에서 올라오며 나타남 (base.css) */
}

h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 6px;
  font-size: 14.5px;
  font-weight: 700;
}

h3 svg {
  color: var(--color-primary);
}

.tpl {
  height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  transition:
    border-color var(--m-hover),
    background var(--m-hover);
}

.tpl:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.tpl svg {
  color: var(--color-text-sub);
}

.fc {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-primary);
}

button:disabled {
  opacity: 0.6;
  cursor: progress;
}
</style>
