<!--
  스캔 원본 미리보기 창 (점검 이력의 수기 줄 돋보기 버튼)
  - 실제: 올린 스캔 원본 GET /inspections/{inspectionId}/file — 그림이면 img, PDF 면 iframe
  - 예시 모드: F02 는 스캔 파일이 없어 그 건의 양식 값을 스캔본처럼(종이 색 · 기울임 · 손글씨) 그려 보여 줌
    (수기 서류 등록 창에서 이은 건은 예시 원본 그림이 있어 그 그림을 보여 줌)
  - 위 '내려받기' = 이력 줄의 내려받기와 같음 (scanFile.downloadScan)
-->
<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { getInspectionFile, getScheduleForm } from '@/api/inspections'
import { USE_MOCK } from '@/api/mock'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import PaperForm from './forms/PaperForm.vue'
import { PAPER_FORM } from './inspectionMeta'
import { downloadScan } from './scanFile'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 점검 이력 한 줄 (수기) */
  item: { type: Object, default: null },
})

const emit = defineEmits(['close'])

const file = ref(null) // 실제: { url, type, revoke }
const values = ref(null) // 예시: 양식 값
const loading = ref(false)

function clear() {
  file.value?.revoke()
  file.value = null
  values.value = null
}

watch(
  () => props.open,
  async (open) => {
    clear()
    if (!open || !props.item) return
    loading.value = true
    try {
      file.value = await getInspectionFile(props.item.inspectionId)
      // 예시 모드: 처음부터 있던 F02 예시 건은 스캔 그림이 없음 → 양식 값을 스캔본처럼 그림
      if (USE_MOCK && !file.value.url && props.item.formType === PAPER_FORM) {
        file.value = null
        values.value = (await getScheduleForm(props.item.scheduleId)).values
      }
    } catch (error) {
      toast(error.message, { type: 'err' })
    } finally {
      loading.value = false
    }
  },
)

onBeforeUnmount(clear)
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="890" label="스캔 원본 미리보기" @close="emit('close')">
        <div class="wrap">
          <div class="m-head">
            <h2>스캔 원본 미리보기</h2>
            <span class="loc mono">{{ item?.scanName }}</span>
            <button type="button" class="btn btn-sm dl" @click="downloadScan(item)"><AppIcon name="download" :size="14" />내려받기</button>
            <button type="button" class="m-close" aria-label="닫기" @click="emit('close')"><AppIcon name="x" /></button>
          </div>
          <div class="body">
            <div v-if="loading" class="empty">스캔 원본을 불러오는 중…</div>
            <PaperForm v-else-if="values" :values="values" :equipment-name="item.equipmentName" scan />
            <iframe v-else-if="file?.type === 'application/pdf'" :src="file.url" title="스캔 원본" class="pdf" />
            <img v-else-if="file?.url" :src="file.url" alt="스캔 원본" class="img" />
            <div v-else class="empty">스캔 원본을 찾지 못했습니다.</div>
          </div>
        </div>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.wrap {
  display: flex;
  flex-direction: column;
  max-height: calc(var(--screen-h) - 48px);
}

.m-head {
  flex: none;
}

.dl {
  margin-left: auto;
}

.dl + .m-close {
  margin-left: 0;
}

.btn-sm {
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
}

.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 0;
  background: #475569; /* 어두운 바탕 위에 종이 */
}

.img {
  display: block;
  max-width: calc(100% - 48px);
  margin: 0 auto;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.35);
}

.pdf {
  display: block;
  width: calc(100% - 48px);
  height: calc(var(--screen-h) - 200px);
  margin: 0 auto;
  border: none;
  background: #fff;
}

.empty {
  padding: 80px 0;
  text-align: center;
  color: #e2e8f0;
}
</style>
