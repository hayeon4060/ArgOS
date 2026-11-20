<!--
  비밀번호 변경 창 (화면설계안 vUsers() pwModal())
  - 지금 비밀번호 / 새 비밀번호 / 새 비밀번호 확인
  - 규칙은 백엔드와 같게: 8자 이상, 영문·숫자·특수문자 포함 (설계안은 '영문, 숫자 8자 이상' — 코드 기준)
  - 틀리면 창이 흔들리고 아래 빨간 문구
  - notice: 창 위쪽 안내 한 줄 (로그인 화면에서 '비밀번호를 먼저 바꿔야 하는 계정'일 때 씀)
  - 바꾸면 changed → close 순서로 알림 (changed 는 '성공'만, close 는 취소 · 닫기에도)
-->
<script setup>
import { ref, watch } from 'vue'
import { changeMyPassword } from '@/api/users'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 창 위쪽 안내 문구 (없으면 안 보임) */
  notice: { type: String, default: '' },
})
const emit = defineEmits(['close', 'changed'])

const RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/
const p0 = ref('')
const p1 = ref('')
const p2 = ref('')
const err = ref('')
const busy = ref(false)
const box = ref(null)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    p0.value = p1.value = p2.value = err.value = ''
  },
)

/** 틀렸을 때: 문구 + 창 흔들기 */
function bad(text) {
  err.value = text
  const el = box.value?.closest('.box')
  if (!el) return
  el.classList.remove('shake')
  void el.offsetWidth
  el.classList.add('shake')
}

async function submit() {
  if (!p0.value) return bad('지금 비밀번호를 입력해 주세요.')
  if (!RULE.test(p1.value)) return bad('영문, 숫자, 특수문자를 섞어 8자 이상 입력해 주세요.')
  if (p1.value !== p2.value) return bad('새 비밀번호가 서로 다릅니다.')
  busy.value = true
  try {
    await changeMyPassword({ currentPassword: p0.value, newPassword: p1.value })
    toast('비밀번호를 바꿨습니다.')
    emit('changed')
    emit('close')
  } catch (error) {
    // 지금 비밀번호 틀림: 백엔드는 401(PASSWORD_MISMATCH)이지만 로그아웃하지 않음 (api/users.js) — 400 으로 바뀌어도 같은 문구
    bad(error.code === 'PASSWORD_MISMATCH' ? '지금 비밀번호가 맞지 않습니다.' : error.message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="460" label="비밀번호 변경" @close="emit('close')">
        <div ref="box" class="m-head">
          <h2>비밀번호 변경</h2>
          <button type="button" class="m-close" aria-label="닫기" @click="emit('close')"><AppIcon name="x" /></button>
        </div>
        <form class="pf" novalidate autocomplete="off" @submit.prevent="submit">
          <p v-if="notice" class="notice">{{ notice }}</p>
          <label for="pw0">지금 비밀번호</label>
          <input id="pw0" v-model="p0" type="password" class="inp" autocomplete="current-password" />
          <label for="pw1">새 비밀번호</label>
          <input id="pw1" v-model="p1" type="password" class="inp" autocomplete="new-password" />
          <label for="pw2">새 비밀번호 확인</label>
          <input id="pw2" v-model="p2" type="password" class="inp" autocomplete="new-password" />
          <p class="msg" :class="{ bad: err }" role="alert">
            <template v-if="err"><AppIcon name="alert" :size="14" />{{ err }}</template>
            <template v-else>영문, 숫자, 특수문자를 섞어 8자 이상</template>
          </p>
          <div class="m-actions acts">
            <button type="button" class="btn" @click="emit('close')">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="busy">
              <span v-if="busy" class="m-spin" />{{ busy ? '변경 중' : '변경' }}
            </button>
          </div>
        </form>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.pf {
  display: flex;
  flex-direction: column;
  padding: 22px 28px 24px;
}

.notice {
  margin: 0 0 14px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-level-warn-bg, #fff7ed);
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text);
}

.pf label {
  margin: 14px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.pf label:first-child {
  margin-top: 0;
}

.inp {
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.msg {
  min-height: 22px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 8px 0 0;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.msg.bad {
  color: var(--color-level-alarm-text);
}

.acts {
  justify-content: flex-end;
  margin-top: 18px;
}

.acts .btn {
  height: 44px;
  padding: 0 20px;
}
</style>
