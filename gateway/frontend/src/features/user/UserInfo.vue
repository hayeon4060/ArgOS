<!--
  사용자 정보 보기 (화면설계안 vUsers() info())
  - 머리: 이름 첫 글자 동그라미, 이름 + 상태 배지, 아이디
  - 기본 정보: 권한, 담당 범위, 연락처, 최근 접속, 등록일, 모바일 앱
  - 변경 기록 (최근 4개)
  - 임시 비밀번호(추가·초기화 직후 한 번만): 복사 단추
-->
<script setup>
import { ROLE, ROLE_COLOR, ROLE_LABEL, dateOf, shortTime } from './userMeta'
import AppIcon from '@/components/AppIcon.vue'
import { toast } from '@/utils/toast'

const props = defineProps({
  /** GET /users/{id} 또는 GET /me (+ scopeText) */
  user: { type: Object, required: true },
  isMe: { type: Boolean, default: false },
  /** 방금 받은 임시 비밀번호 (없으면 '') */
  tempPassword: { type: String, default: '' },
})

async function copyPw() {
  try {
    await navigator.clipboard.writeText(props.tempPassword)
    toast('임시 비밀번호를 복사했습니다.')
  } catch {
    toast('복사하지 못했습니다. 직접 적어 두세요.', { type: 'warn' })
  }
}
</script>

<template>
  <div class="info">
    <div class="head">
      <span class="av">{{ user.name[0] }}</span>
      <div>
        <div class="nm">
          {{ user.name }}
          <span v-if="user.active !== undefined" class="m-chip" :class="user.active ? 'ok' : 'gray'">{{ user.active ? '사용 중' : '사용 정지' }}</span>
        </div>
        <div class="mono id">{{ user.loginId }}</div>
      </div>
    </div>

    <!-- 임시 비밀번호: 한 번만 보임 -->
    <div v-if="tempPassword" class="temp" role="status">
      <AppIcon name="key" :size="16" />
      <span>임시 비밀번호 <b class="mono">{{ tempPassword }}</b><small>이 화면을 벗어나면 다시 볼 수 없습니다. 처음 로그인할 때 새 비밀번호를 정합니다.</small></span>
      <button type="button" class="btn copy" @click="copyPw">복사</button>
    </div>

    <h4 class="sh">기본 정보</h4>
    <div class="kv"><label>권한</label><span class="rl"><i :style="{ background: ROLE_COLOR[user.role] }" />{{ ROLE_LABEL[user.role] }}</span></div>
    <div class="kv"><label>담당 범위</label>{{ user.scopeText }}</div>
    <div class="kv"><label>연락처</label><span class="mono sm">{{ user.phone || '–' }}</span></div>
    <div class="kv">
      <label>최근 접속</label>
      <span v-if="isMe" class="dotc"><i />접속 중</span>
      <span v-else class="mono sm">{{ shortTime(user.lastLoginAt) }}</span>
    </div>
    <div v-if="user.createdAt" class="kv"><label>등록일</label><span class="mono sm">{{ dateOf(user.createdAt) }}</span></div>
    <div v-if="user.role === ROLE.WORKER" class="kv"><label>모바일 앱</label>{{ user.pushReady ? '등록됨 · 알림 받음' : '아직 등록하지 않음' }}</div>

    <template v-if="user.history?.length">
      <h4 class="sh">변경 기록</h4>
      <div v-for="(h, i) in user.history.slice(0, 4)" :key="i" class="kv">
        <span class="mono when">{{ dateOf(h.at) }}</span>{{ h.text }}<span class="by">{{ h.by }}</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.av {
  width: 52px;
  height: 52px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #bfdbfe;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-primary-hover);
}

.nm {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 19px;
  font-weight: 700;
}

.id {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.temp {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #bfdbfe;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  font-size: 13px;
  animation: pop-in var(--m-pop-in) both;
}

.temp svg {
  flex: none;
  color: var(--color-primary);
}

.temp b {
  margin-left: 4px;
  font-size: 15px;
}

.temp small {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-sub);
}

.copy {
  height: 32px;
  flex: none;
  margin-left: auto;
}

.sh {
  margin: 22px 0 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-sub);
}

.kv {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid #f1f5f9;
  font-size: 13.5px;
}

.sh + .kv {
  border-top: none;
}

.kv label {
  width: 84px;
  flex: none;
  color: var(--color-text-sub);
}

.sm {
  font-size: 13px;
}

.rl {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.rl i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.dotc {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--color-level-normal-text);
}

.dotc i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-level-normal);
}

.when {
  width: 96px;
  flex: none;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.by {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--color-text-weak);
}
</style>
