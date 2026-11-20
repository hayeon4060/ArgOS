<!--
  사용자 추가·수정 입력 (화면설계안 vUsers() form())
  - 이름, 아이디(추가할 때만 — 칸을 벗어나면 중복 확인), 연락처
  - 권한 카드 3개(줄 수 있는 것만) → 권한에 맞게 담당 범위가 바뀜 (본사 = 없음, 공장 = 공장, 공정 = 공장 + 공정 여러 개)
  - 사용 상태 스위치 (수정할 때, 내 계정은 못 바꿈)
  - 내 정보 수정(me): 이름·연락처만 (권한·범위는 직접 못 바꿈)
  - 칸 아래 빨간 문구는 errors 로 받음
-->
<script setup>
import { computed } from 'vue'
import { ROLE, ROLE_COLOR, ROLE_DESC, ROLE_LABEL } from './userMeta'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** add 추가 · edit 수정 · me 내 정보 수정 */
  mode: { type: String, required: true },
  /** 줄 수 있는 권한 */
  roles: { type: Array, required: true },
  /** 공장 [{ id, name, processes: [{ id, name }] }] */
  factories: { type: Array, default: () => [] },
  /** 공장 담당자: 소속 공장만 고를 수 있음 */
  factoryLocked: { type: Boolean, default: false },
  /** 고치는 사람이 나 (권한·상태 못 바꿈) */
  self: { type: Boolean, default: false },
  /** { name, loginId, scope } 빨간 문구 */
  errors: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['check-id'])
/** { name, loginId, phone, role, factoryId, processIds, active } */
const draft = defineModel({ type: Object, required: true })

const factory = computed(() => props.factories.find((f) => f.id === Number(draft.value.factoryId)))

function pickRole(r) {
  if (props.self) return
  draft.value.role = r
  // 공장이 비어 있으면 첫 공장으로
  if (r !== ROLE.HQ_ADMIN && !draft.value.factoryId && props.factories[0]) draft.value.factoryId = props.factories[0].id
}
function pickFactory(e) {
  draft.value.factoryId = Number(e.target.value)
  draft.value.processIds = []
}
function toggleProcess(id) {
  const list = draft.value.processIds
  draft.value.processIds = list.includes(id) ? list.filter((x) => x !== id) : [...list, id]
}
</script>

<template>
  <div class="uf-wrap">
    <div class="uf">
      <label for="uf-name">이름</label>
      <input id="uf-name" v-model="draft.name" class="inp" :class="{ bad: errors.name }" placeholder="예: 홍길동" maxlength="12" />
      <p v-if="errors.name" class="fe"><AppIcon name="alert" :size="13" />{{ errors.name }}</p>
    </div>
    <div v-if="mode !== 'me'" class="uf">
      <label for="uf-id">아이디</label>
      <input
        id="uf-id"
        v-model="draft.loginId"
        class="inp mono"
        :class="{ bad: errors.loginId }"
        placeholder="예: u5_paint04"
        spellcheck="false"
        :readonly="mode !== 'add'"
        @blur="mode === 'add' && emit('check-id')"
      />
      <p v-if="errors.loginId" class="fe"><AppIcon name="alert" :size="13" />{{ errors.loginId }}</p>
      <p v-else-if="mode !== 'add'" class="hint">아이디는 바꿀 수 없습니다.</p>
    </div>
    <div class="uf">
      <label for="uf-tel">연락처</label>
      <input id="uf-tel" v-model="draft.phone" class="inp mono" placeholder="010-0000-0000" inputmode="tel" />
    </div>

    <template v-if="mode !== 'me'">
      <h4 class="sh">권한<span v-if="self">내 권한은 직접 바꿀 수 없습니다</span></h4>
      <button
        v-for="r in roles"
        :key="r"
        type="button"
        class="rc"
        :class="{ on: draft.role === r, dis: self }"
        :disabled="self && draft.role !== r"
        :aria-pressed="draft.role === r"
        @click="pickRole(r)"
      >
        <span class="rd" :style="draft.role === r ? { borderColor: ROLE_COLOR[r] } : undefined"><i v-if="draft.role === r" :style="{ background: ROLE_COLOR[r] }" /></span>
        <span><b>{{ ROLE_LABEL[r] }}</b><small>{{ ROLE_DESC[r] }}</small></span>
      </button>

      <!-- 담당 범위: 권한에 맞게 바뀜 -->
      <Transition name="scope" mode="out-in">
        <div v-if="draft.role !== ROLE.HQ_ADMIN" :key="draft.role" class="scope">
          <h4 class="sh">{{ draft.role === ROLE.WORKER ? '담당 공정' : '담당 공장' }}<span v-if="draft.role === ROLE.WORKER">여러 개 선택 가능</span></h4>
          <div class="line">
            <span class="lb">공장</span>
            <select :value="draft.factoryId" :disabled="factoryLocked" aria-label="담당 공장" @change="pickFactory">
              <option v-for="f in factories" :key="f.id" :value="f.id">{{ f.name }}</option>
            </select>
          </div>
          <div v-if="draft.role === ROLE.WORKER" class="line">
            <span class="lb">공정</span>
            <div class="chips">
              <button
                v-for="p in factory?.processes ?? []"
                :key="p.id"
                type="button"
                class="pc"
                :class="{ on: draft.processIds.includes(p.id) }"
                :aria-pressed="draft.processIds.includes(p.id)"
                @click="toggleProcess(p.id)"
              >
                <AppIcon v-if="draft.processIds.includes(p.id)" name="check" :size="13" />{{ p.name.replace(' 공정', '') }}
              </button>
            </div>
          </div>
          <p v-if="errors.scope" class="fe pad"><AppIcon name="alert" :size="13" />{{ errors.scope }}</p>
        </div>
      </Transition>

      <template v-if="mode === 'edit'">
        <h4 class="sh">설정</h4>
        <div class="kv">
          <label>사용 상태</label>
          {{ draft.active ? '사용 중' : '정지 (로그인 불가)' }}
          <button
            type="button"
            class="tg"
            :class="{ on: draft.active }"
            role="switch"
            :aria-checked="draft.active"
            :disabled="self"
            aria-label="사용 상태"
            @click="draft.active = !draft.active"
          />
        </div>
      </template>
      <div v-else class="kv first">
        <label>첫 비밀번호</label><span class="weak">추가하면 임시 비밀번호가 한 번 보입니다</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.uf {
  margin-bottom: 14px;
}

.uf > label,
.lb {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--color-text-sub);
}

.inp {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--color-text);
}

.inp.mono {
  font-size: 13px;
}

.inp[readonly] {
  background: var(--color-surface-sub);
  color: var(--color-text-sub);
}

.inp.bad {
  border-color: var(--color-level-alarm);
}

.fe {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 5px 0 0;
  font-size: 12.5px;
  color: var(--color-level-alarm-text);
}

.fe.pad {
  margin-left: 46px;
}

.hint {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--color-text-weak);
}

.sh {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 20px 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-sub);
}

.sh span {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-weak);
}

/* 권한 카드 */
.rc {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  padding: 11px 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--m-hover),
    background var(--m-hover);
}

.rc:hover:not(:disabled) {
  border-color: #93c5fd;
}

.rc.on {
  border: 2px solid var(--color-primary);
  padding: 10px 13px;
  background: var(--color-primary-soft);
}

.rc:disabled {
  opacity: 0.45;
  cursor: default;
}

.rc span:last-child {
  display: flex;
  flex-direction: column;
}

.rc b {
  font-size: 14px;
}

.rc small {
  font-size: 12px;
  color: var(--color-text-weak);
}

.rd {
  width: 18px;
  height: 18px;
  flex: none;
  display: grid;
  place-items: center;
  border: 2px solid #cbd5e1;
  border-radius: 50%;
}

.rd i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

/* 담당 범위: 권한을 바꾸면 0.2초에 바뀜 */
.scope-enter-active,
.scope-leave-active {
  transition:
    opacity 200ms,
    transform 200ms;
}

.scope-enter-from,
.scope-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.line {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.line .lb {
  width: 36px;
  margin: 8px 0 0;
}

.line select {
  height: 36px;
  min-width: 160px;
  padding: 0 8px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13.5px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pc {
  height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 17px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  transition:
    border-color var(--m-hover),
    background var(--m-hover);
}

.pc.on {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  font-weight: 600;
  color: var(--color-primary-hover);
}

.kv {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
}

.kv.first {
  margin-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.kv label {
  width: 84px;
  flex: none;
  color: var(--color-text-sub);
}

.weak {
  font-size: 13px;
  color: var(--color-text-weak);
}

/* 스위치 */
.tg {
  position: relative;
  width: 40px;
  height: 22px;
  margin-left: auto;
  padding: 0;
  border: none;
  border-radius: 11px;
  background: #cbd5e1;
  cursor: pointer;
  transition: background 200ms;
}

.tg::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: transform 200ms;
}

.tg.on {
  background: var(--color-level-normal);
}

.tg.on::after {
  transform: translateX(18px);
}

.tg:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
