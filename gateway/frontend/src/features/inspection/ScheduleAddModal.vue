<!--
  점검 스케줄 추가 창
  - 칸: 날짜 · 시각, 서류 양식(F02 하나라 글자로만), 설비(F02 설비만), 점검자, 반복(한 번 / 매일 / 매주 / 매월)
  - 반복이면 '횟수'(몇 번 만들지)도 받음 — 시안에는 없던 칸 (백엔드가 끝을 알아야 해서 추가)
  - 추가하면 POST /inspections/schedule → saved(만든 건 목록, 첫 날짜)
-->
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { addSchedule, getScheduleOptions } from '@/api/inspections'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import SegButtons from '@/components/SegButtons.vue'
import { FORM_LABEL, PAPER_FORM, REPEATS } from './inspectionMeta'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 처음 날짜 'YYYY-MM-DD' */
  date: { type: String, default: '' },
  factoryId: { type: Number, default: null },
})

const emit = defineEmits(['close', 'saved'])

/** 반복별 기본 횟수 (시안: 매일 5번 · 매주 4번 · 매월 4번) */
const DEFAULT_COUNT = { ONCE: 1, DAILY: 5, WEEKLY: 4, MONTHLY: 4 }

const f = reactive({ date: '', time: '14:00', formType: PAPER_FORM, equipmentId: null, assigneeId: null, repeat: 'ONCE', count: 1 })
const opts = ref({ equipments: [], inspectors: [] })
const saving = ref(false)

/** 이 양식(F02)을 점검하는 설비만 */
const equipments = computed(() => opts.value.equipments.filter((e) => !e.formType || e.formType === f.formType))

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    Object.assign(f, { date: props.date, time: '14:00', repeat: 'ONCE', count: 1 })
    try {
      opts.value = await getScheduleOptions(props.factoryId ?? undefined)
    } catch (error) {
      toast(error.message, { type: 'err' })
    }
    f.equipmentId = equipments.value[0]?.id ?? null
    f.assigneeId = opts.value.inspectors[0]?.id ?? null
  },
)

watch(
  () => f.repeat,
  (r) => (f.count = DEFAULT_COUNT[r]),
)

async function save() {
  saving.value = true
  try {
    const made = await addSchedule({ ...f, count: f.repeat === 'ONCE' ? 1 : Number(f.count) })
    toast(`점검 스케줄 ${made.length}건을 추가했습니다.`)
    emit('saved', { items: made, date: f.date })
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="480" label="점검 스케줄 추가" @close="emit('close')">
        <div class="m-head">
          <h2>점검 스케줄 추가</h2>
          <button type="button" class="m-close" aria-label="닫기" @click="emit('close')"><AppIcon name="x" /></button>
        </div>

        <div class="form">
          <label for="sa-date">날짜 · 시각</label>
          <div class="two">
            <input id="sa-date" v-model="f.date" type="date" />
            <input v-model="f.time" type="time" aria-label="시각" />
          </div>

          <label>서류 양식</label>
          <span class="fixed"><b class="mono">{{ f.formType }}</b> {{ FORM_LABEL[f.formType] }}</span>

          <label for="sa-eq">설비</label>
          <select id="sa-eq" v-model="f.equipmentId">
            <option v-for="e in equipments" :key="e.id" :value="e.id">{{ e.name }} ({{ e.code }})</option>
          </select>

          <label for="sa-who">점검자</label>
          <select id="sa-who" v-model="f.assigneeId">
            <option v-for="w in opts.inspectors" :key="w.id" :value="w.id">{{ w.name }}</option>
          </select>

          <label>반복</label>
          <div class="rep">
            <SegButtons v-model="f.repeat" :options="REPEATS" />
            <template v-if="f.repeat !== 'ONCE'">
              <input v-model="f.count" type="number" min="2" max="31" aria-label="반복 횟수" class="cnt" />
              <span class="unit">번</span>
            </template>
          </div>
        </div>

        <div class="mf">
          <button type="button" class="btn" @click="emit('close')">취소</button>
          <button type="button" class="btn btn-primary" :disabled="saving || !f.date || !f.equipmentId || !f.assigneeId" @click="save">
            <span v-if="saving" class="m-spin" /><AppIcon v-else name="ok" :size="15" />추가
          </button>
        </div>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.form {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 22px 28px 10px;
  font-size: 13.5px;
}

label {
  color: var(--color-text-sub);
}

input,
select {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13.5px;
  color: var(--color-text);
}

.fixed b {
  font-size: 12.5px;
  color: var(--color-primary);
}

.two {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  gap: 8px;
}

.rep {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cnt {
  width: 64px;
  text-align: right;
}

.unit {
  color: var(--color-text-sub);
}

.mf {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 28px 20px;
}
</style>
