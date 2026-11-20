<!--
  오른쪽 패널 (화면설계안 vUsers() panel())
  - me   : 내 정보 (기본) — 비밀번호 변경 · 내 정보 수정(이름·연락처)
  - view : 고른 사용자 — 삭제 · 비밀번호 초기화 · 수정 (X 를 누르면 내 정보로)
  - edit : 수정 폼 — 취소 · 저장 (권한·담당 범위·사용 상태)
  - add  : 추가 폼 — 취소 · 추가 → 임시 비밀번호가 한 번 보임
  - 내용이 바뀔 때 오른쪽에서 12px 밀려 들어옴 (240ms)
  - 저장·삭제가 끝나면 changed(id) → 화면이 목록을 다시 불러옴
-->
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ROLE, canManage, rolesFor } from './userMeta'
import PasswordModal from './PasswordModal.vue'
import UserForm from './UserForm.vue'
import UserInfo from './UserInfo.vue'
import {
  checkLoginId,
  createUser,
  deleteUser,
  getMe,
  getUser,
  resetUserPassword,
  setUserActive,
  updateMe,
  updateUser,
} from '@/api/users'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'

const props = defineProps({
  /** 고른 사용자 id (없으면 내 정보) */
  userId: { type: Number, default: null },
  /** 로그인한 사람 (auth.user) */
  actor: { type: Object, required: true },
  /** 공장 [{ id, name, processes }] — 공장 담당자는 소속 공장만 */
  factories: { type: Array, default: () => [] },
})

const emit = defineEmits(['close', 'changed', 'deleted'])

const mode = ref('me') // me · view · edit · add · meEdit
const user = ref(null)
const tempPassword = ref('')
const anim = ref(0) // 바뀔 때마다 +1 → 밀려 들어오는 움직임
const busy = ref(false)
const pwOpen = ref(false)
const delOpen = ref(false)

const isHq = computed(() => props.actor.role === ROLE.HQ_ADMIN)
/** 보여 줄 사용자 (내 정보는 GET /me 에 scopeText 가 없어 공장 목록이 오면 만듦) */
const shown = computed(() => user.value && { ...user.value, scopeText: user.value.scopeText ?? scopeTextOf(user.value) })
const isMe = computed(() => user.value?.id === props.actor.id)
const can = computed(() => canManage(props.actor, user.value))

/** 담당 범위 문구 (내 정보는 GET /me 에 scopeText 가 없어 만듦) */
function scopeTextOf(u) {
  if (u.role === ROLE.HQ_ADMIN) return '모든 사업장'
  const f = props.factories.find((x) => x.id === u.scope?.factoryIds?.[0])
  if (!f) return '–'
  if (u.role === ROLE.PLANT_ADMIN) return `${f.name} 전체`
  return `${f.name} · ${f.processes.filter((p) => u.scope.processIds.includes(p.id)).map((p) => p.name).join(', ')}`
}

/** 패널 내용 불러오기 (늦게 온 이전 결과는 버림 — 그사이 '사용자 추가'를 누른 경우 등) */
let loadSeq = 0
async function load(id, keepTemp = false) {
  const my = ++loadSeq
  if (!keepTemp) tempPassword.value = ''
  try {
    if (id && id !== props.actor.id) {
      const u = await getUser(id)
      if (my !== loadSeq) return
      user.value = u
      mode.value = 'view'
    } else {
      const me = await getMe()
      if (my !== loadSeq) return
      user.value = { ...me, active: true }
      mode.value = 'me'
    }
    anim.value += 1
  } catch (error) {
    if (my === loadSeq) toast(error.message, { type: 'err' })
  }
}
watch(
  () => props.userId,
  // 지금 보고 있는 사람을 다시 고르면(추가 직후 등) 임시 비밀번호는 지우지 않음
  (id) => load(id, id != null && id === user.value?.id),
  { immediate: true },
)

/* ───────── 입력 폼 ───────── */
const draft = ref(null)
const errors = reactive({ name: '', loginId: '', scope: '' })
const clearErrors = () => Object.assign(errors, { name: '', loginId: '', scope: '' })
const dirty = computed(() => ['edit', 'add', 'meEdit'].includes(mode.value))
defineExpose({ dirty, startAdd })

function startEdit() {
  const u = user.value
  draft.value = {
    name: u.name,
    loginId: u.loginId,
    phone: u.phone ?? '',
    role: u.role,
    factoryId: u.scope?.factoryIds?.[0] ?? props.factories[0]?.id ?? null,
    processIds: [...(u.scope?.processIds ?? [])],
    active: u.active ?? true,
  }
  clearErrors()
  mode.value = isMe.value ? 'meEdit' : 'edit'
  anim.value += 1
}

function startAdd() {
  loadSeq += 1 // 불러오는 중이던 내용은 버림
  draft.value = { name: '', loginId: '', phone: '', role: ROLE.WORKER, factoryId: props.factories[0]?.id ?? null, processIds: [], active: true }
  clearErrors()
  tempPassword.value = ''
  mode.value = 'add'
  anim.value += 1
}

function cancel() {
  clearErrors()
  load(mode.value === 'add' ? null : user.value?.id)
}

/** 아이디 칸을 벗어날 때 중복 확인 */
async function checkId() {
  errors.loginId = ''
  const id = draft.value.loginId
  if (!id) return
  if (!/^[a-z0-9_]{4,20}$/.test(id)) {
    errors.loginId = '영문 소문자, 숫자, 밑줄로 4~20자를 입력해 주세요.'
    return
  }
  const r = await checkLoginId(id)
  if (!r.available) errors.loginId = '이미 쓰는 아이디입니다.'
}

const bodyOf = (d) => ({
  name: d.name.trim(),
  loginId: d.loginId,
  phone: d.phone,
  role: d.role,
  scope: {
    siteId: d.role === ROLE.HQ_ADMIN ? null : 1,
    factoryIds: d.role === ROLE.HQ_ADMIN || !d.factoryId ? [] : [Number(d.factoryId)],
    processIds: d.role === ROLE.WORKER ? d.processIds : [],
  },
})

async function save() {
  clearErrors()
  const d = draft.value
  if (!d.name.trim()) errors.name = '이름을 입력해 주세요.'
  if (mode.value === 'add') await checkId()
  if (d.role === ROLE.WORKER && !d.processIds.length) errors.scope = '공정을 한 개 이상 선택해 주세요.'
  if (errors.name || errors.loginId || errors.scope) return
  busy.value = true
  try {
    if (mode.value === 'meEdit') {
      await updateMe({ name: d.name.trim(), phone: d.phone })
      toast('내 정보를 저장했습니다.')
      emit('changed', props.actor.id)
      await load(null)
    } else if (mode.value === 'add') {
      const r = await createUser(bodyOf(d))
      toast(`${d.name.trim()} 님을 등록했습니다. 임시 비밀번호를 전달해 주세요.`)
      tempPassword.value = r.tempPassword
      user.value = await getUser(r.id)
      mode.value = 'view'
      anim.value += 1
      emit('changed', r.id) // 화면이 이 사용자를 고르면 같은 사람이라 임시 비밀번호는 그대로 둠
    } else {
      const u = user.value
      await updateUser(u.id, bodyOf(d))
      if (d.active !== u.active) await setUserActive(u.id, d.active)
      toast(`${d.name.trim()} 님의 정보를 저장했습니다.`)
      emit('changed', u.id)
      await load(u.id)
    }
  } catch (error) {
    if (error.code === 'LOGIN_ID_TAKEN') errors.loginId = error.message
    else if (error.code === 'SCOPE_REQUIRED') errors.scope = error.message
    else toast(error.message, { type: 'err' })
  } finally {
    busy.value = false
  }
}

/* ───────── 삭제 · 비밀번호 초기화 ───────── */
async function remove() {
  busy.value = true
  try {
    const u = user.value
    await deleteUser(u.id)
    delOpen.value = false
    toast(`${u.name} 님을 삭제했습니다.`, { type: 'warn' })
    emit('deleted', u.id)
  } catch (error) {
    delOpen.value = false
    toast(error.message, { type: 'err' })
  } finally {
    busy.value = false
  }
}

async function resetPw() {
  try {
    const r = await resetUserPassword(user.value.id)
    tempPassword.value = r.tempPassword
    user.value = await getUser(user.value.id)
    toast('임시 비밀번호를 만들었습니다.')
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

const title = computed(() => ({ me: '내 정보', view: '사용자 정보', edit: '사용자 수정', add: '사용자 추가', meEdit: '내 정보 수정' })[mode.value])
</script>

<template>
  <section class="pcard base-card rise">
    <header class="ch">
      <h3>{{ title }}</h3>
      <button v-if="mode === 'view'" type="button" class="m-close x" aria-label="닫기 — 내 정보로" @click="emit('close')"><AppIcon name="x" /></button>
    </header>

    <div :key="anim" class="pn">
      <template v-if="user || mode === 'add'">
        <UserInfo v-if="mode === 'me' || mode === 'view'" :user="shown" :is-me="isMe" :temp-password="tempPassword" />
        <UserForm
          v-else
          v-model="draft"
          :mode="mode === 'meEdit' ? 'me' : mode"
          :roles="rolesFor(actor.role)"
          :factories="factories"
          :factory-locked="!isHq"
          :self="mode === 'edit' && isMe"
          :errors="errors"
          @check-id="checkId"
        />
      </template>
      <p v-else class="wait">불러오는 중</p>
    </div>

    <footer class="bt">
      <template v-if="mode === 'me'">
        <button type="button" class="btn" @click="pwOpen = true"><AppIcon name="key" :size="16" />비밀번호 변경</button>
        <button type="button" class="btn btn-primary right" @click="startEdit"><AppIcon name="edit" :size="16" />내 정보 수정</button>
      </template>
      <template v-else-if="mode === 'view'">
        <button type="button" class="btn danger" :disabled="isMe || !can" @click="delOpen = true"><AppIcon name="x" :size="16" />삭제</button>
        <button type="button" class="btn" :disabled="!can" @click="resetPw"><AppIcon name="key" :size="16" />비밀번호 초기화</button>
        <button type="button" class="btn btn-primary right" :disabled="!can" @click="startEdit"><AppIcon name="edit" :size="16" />수정</button>
      </template>
      <template v-else>
        <button type="button" class="btn right" :disabled="busy" @click="cancel">취소</button>
        <button type="button" class="btn btn-primary" :disabled="busy" @click="save">
          <span v-if="busy" class="m-spin" /><AppIcon v-else :name="mode === 'add' ? 'plus' : 'save'" :size="16" />{{ busy ? '저장 중' : mode === 'add' ? '추가' : '저장' }}
        </button>
      </template>
    </footer>

    <PasswordModal :open="pwOpen" @close="pwOpen = false" />
    <ConfirmModal
      :open="delOpen"
      :title="`${user?.name} 님을 삭제할까요?`"
      ok-text="삭제"
      :busy="busy"
      @ok="remove"
      @cancel="delOpen = false"
    >
      {{ user?.name }}({{ user?.loginId }})을 삭제하면 로그인할 수 없고 목록에서 사라집니다.<br />
      이 사용자가 남긴 조치 기록과 이용 로그는 그대로 남습니다.
    </ConfirmModal>
  </section>
</template>

<style scoped>
.pcard {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.ch {
  height: 48px;
  flex: none;
  display: flex;
  align-items: center;
  padding: 0 12px 0 20px;
  border-bottom: 1px solid var(--color-border);
}

.ch h3 {
  font-size: 16px;
  font-weight: 700;
}

.x {
  margin-left: auto;
}

/* 내용이 바뀌면 오른쪽에서 12px 밀려 들어옴 */
.pn {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  animation: pn-in var(--m-panel) both;
}

@keyframes pn-in {
  from {
    opacity: 0;
    transform: translateX(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.wait {
  font-size: 13px;
  color: var(--color-text-weak);
}

.bt {
  flex: none;
  display: flex;
  gap: 8px;
  padding: 16px 20px 20px;
  border-top: 1px solid var(--color-border);
}

.bt .btn {
  height: 44px;
}

.right {
  margin-left: auto;
}

.danger:not(:disabled) {
  border-color: #fca5a5;
  color: var(--color-level-alarm-text);
}

@media (prefers-reduced-motion: reduce) {
  .pn {
    animation: none;
  }
}
</style>
