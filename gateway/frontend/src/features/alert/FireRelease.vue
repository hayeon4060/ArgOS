<!--
  화재 창 아래 '경보 해제' (화면설계안 mFire() #ff, 화면설계서 C4 ⑧)
  - 관리자 아이디(로그인한 사람, 고칠 수 없음) + 비밀번호 + '경보 해제'
  - 틀리면 창이 흔들리고(부모가 shake) 칸 테두리 빨강 + '(n/5)' / 5회면 잠김 → 본사 관리자 승인 필요
  - 맞으면 초록 '해제되었습니다' → 0.9초 뒤 창 닫힘 + 알림 띠
  - POST /fires/{id}/clear { password } — 예시 데이터는 예시 계정 비밀번호와 비교
  - 본사 관리자: 해제 입력 없음 (열람만)
-->
<script setup>
import { onMounted, ref } from 'vue'
import { clearFire } from '@/api/emergency'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  /** GET /fires/{id} */
  fire: { type: Object, required: true },
  canAct: { type: Boolean, default: false },
})

const emit = defineEmits(['wrong', 'cleared'])

const auth = useAuthStore()

const password = ref('')
const show = ref(false)
const busy = ref(false)
const done = ref(false)
const error = ref('')
const input = ref(null)

onMounted(() => setTimeout(() => input.value?.focus(), 400))

async function submit() {
  if (!password.value || busy.value || props.fire.unlock.locked) return
  busy.value = true
  error.value = ''
  try {
    await clearFire(props.fire.id, { password: password.value, memo: '', by: auth.user?.name })
    done.value = true
    setTimeout(() => emit('cleared'), 900)
  } catch (e) {
    // 비밀번호 틀림: 401(FIRE_PASSWORD_MISMATCH)이어도 로그아웃하지 않음 (api/emergency.js) — 남은 횟수는 error.remaining
    const left = e.details?.remaining
    error.value =
      e.code === 'FIRE_PASSWORD_MISMATCH'
        ? `비밀번호가 맞지 않습니다.${left != null ? ` 남은 횟수 ${left}회` : ''}`
        : e.message
    password.value = ''
    emit('wrong')
    input.value?.focus()
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="rel">
    <div class="h"><AppIcon name="lock" :size="18" />경보 해제</div>

    <template v-if="canAct">
      <p class="desc">경보와 방송을 해제하려면 관리자 비밀번호를 다시 입력하세요. 비밀번호가 확인되기 전에는 해제할 수 없습니다.</p>
      <form class="row" @submit.prevent="submit">
        <div class="who">
          <small>관리자</small>
          <div class="pw fixed"><AppIcon name="user" :size="18" />{{ auth.user?.loginId }}</div>
        </div>
        <div class="field">
          <small>비밀번호</small>
          <label class="pw" :class="{ bad: error }">
            <AppIcon name="key" :size="18" />
            <input
              ref="input"
              v-model="password"
              :type="show ? 'text' : 'password'"
              :placeholder="fire.unlock.locked ? '입력할 수 없습니다' : '비밀번호를 입력하세요'"
              :disabled="fire.unlock.locked || done"
              autocomplete="current-password"
              aria-label="관리자 비밀번호"
              @input="error = ''"
            />
            <button type="button" class="eye" :aria-label="show ? '비밀번호 숨기기' : '비밀번호 보기'" @click="show = !show">
              <AppIcon :name="show ? 'eyeoff' : 'eye'" :size="18" />
            </button>
          </label>
        </div>
        <button type="submit" class="btn submit" :class="{ ok: done }" :disabled="!password || busy || fire.unlock.locked || done">
          <template v-if="done"><AppIcon name="check" :size="17" />해제되었습니다</template>
          <template v-else-if="busy"><span class="m-spin dark" />확인하는 중</template>
          <template v-else><AppIcon name="lock" :size="17" />경보 해제</template>
        </button>
      </form>
      <p v-if="done" class="msg ok">비밀번호를 확인했습니다. 경보와 방송을 해제합니다.</p>
      <p v-else-if="error || fire.unlock.locked" class="msg err" role="alert">
        <AppIcon name="alert" :size="14" />{{ fire.unlock.locked ? '5회 틀렸습니다. 본사 관리자 승인을 받은 뒤 다시 시도할 수 있습니다.' : error }}
      </p>
      <p v-else class="msg">5회 연속 틀리면 본사 관리자 승인이 필요합니다.</p>
    </template>
    <p v-else class="desc">현장 공장 담당자가 비밀번호를 다시 입력해 해제합니다. 본사 관리자는 열람만 합니다.</p>
  </section>
</template>

<style scoped>
.rel {
  padding: 22px 28px;
  border-top: 1px solid var(--color-border);
  background: #f8fafc;
}

.h {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
}

.desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-text-sub);
}

.row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.row small {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: #64748b;
}

.who {
  width: 230px;
}

.field {
  flex: 1;
}

/* 입력 칸 (설계안 .pw) */
.pw {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1.5px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 15px;
  transition:
    border-color 150ms,
    box-shadow 150ms;
}

.pw .app-icon {
  color: var(--color-text-weak);
}

.pw.fixed {
  background: #f1f5f9;
  color: var(--color-text-sub);
}

.pw.bad {
  border-color: var(--color-level-alarm);
}

.pw:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.pw input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: none;
  font-size: 15px;
}

.eye {
  display: grid;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
}

.submit {
  width: 200px;
  height: 48px;
  font-size: 15px;
}

.submit:not(:disabled) {
  border-color: var(--color-level-alarm);
  background: var(--color-level-alarm);
  color: #fff;
  font-weight: 700;
}

.submit.ok {
  border-color: var(--color-level-normal);
  background: var(--color-level-normal);
  color: #fff;
  font-weight: 700;
  opacity: 1;
}

.m-spin.dark {
  border-color: rgba(15, 23, 42, 0.2);
  border-top-color: var(--color-text);
}

.msg {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.msg.ok {
  font-weight: 600;
  color: var(--color-level-normal-text);
}

.msg.err {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-level-alarm-text);
  animation: softin 200ms both;
}
</style>
