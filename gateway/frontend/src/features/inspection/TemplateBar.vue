<!--
  맨 위 '점검 서류 양식' 바 — 빈 양식 내려받기 (현장에서 인쇄해 손으로 쓰는 용도)
  - F02 환기·배기설비 점검일지 · F03 수변전설비 점검일지 · 전체 받기
  - 실제: GET /doc-forms/{formType}/template (PDF) 를 받아 바로 저장 (getFormTemplate)
  - 예시 모드: 파일이 없어 안내만
  - '전체 받기'는 양식마다 차례로 받음 (저장 창 없이 브라우저 다운로드 폴더로)
-->
<script setup>
import { ref } from 'vue'
import { getFormTemplate } from '@/api/inspections'
import { USE_MOCK } from '@/api/mock'
import { downloadBlob } from '@/utils/saveFile'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import { FORM_LABEL, PAPER_FORMS } from './inspectionMeta'

const busy = ref('')

/** 양식 하나 받기 */
async function getOne(formType) {
  const blob = await getFormTemplate(formType)
  if (blob) downloadBlob(`ARG-${formType}_${FORM_LABEL[formType]}.pdf`, blob)
}

async function download(formType) {
  if (busy.value) return
  busy.value = formType
  try {
    const list = formType === 'all' ? PAPER_FORMS : [formType]
    for (const f of list) await getOne(f)
    const what = formType === 'all' ? `점검 서류 양식 ${list.length}종` : `${formType} ${FORM_LABEL[formType]} 빈 양식`
    toast(USE_MOCK ? `${what}을 내려받았습니다. (예시 모드라 실제 파일은 없습니다)` : `${what}을 내려받았습니다.`)
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <div class="tplbar base-card">
    <h3><AppIcon name="file" :size="17" />점검 서류 양식</h3>
    <button v-for="f in PAPER_FORMS" :key="f" type="button" class="tpl" :disabled="!!busy" @click="download(f)">
      <span class="fc mono">{{ f }}</span>{{ FORM_LABEL[f] }}<AppIcon name="download" :size="15" />
    </button>
    <span class="gap" />
    <button type="button" class="link" :disabled="!!busy" @click="download('all')">
      전체 양식 한 번에 받기<AppIcon name="download" :size="14" />
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

.gap {
  flex: 1;
}

.link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  color: var(--color-primary);
  font-size: 13px;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: progress;
}
</style>
