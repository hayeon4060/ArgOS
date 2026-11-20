<!--
  상단 바 오른쪽 끝: 사용자 버튼 + 펼침 메뉴(이름·아이디, 로그아웃)
  - 설계안의 '내 정보'는 설계 범위 밖이라 넣지 않음
  - 바깥을 누르거나 Esc 를 누르면 닫힘 (utils/useOutsideClose.js), 열고 닫힐 때 menu-drop 움직임 (motion.css)
-->
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABEL } from '@/types/codes'
import { useOutsideClose } from '@/utils/useOutsideClose'

const router = useRouter()
const auth = useAuthStore()

const open = ref(false)
const root = ref(null)
useOutsideClose(root, open)

async function onLogout() {
  open.value = false
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div ref="root" class="user-wrap">
    <button type="button" class="user" :aria-expanded="open" @click="open = !open">
      <span class="avatar"><AppIcon name="user" /></span>
      <span class="role">{{ ROLE_LABEL[auth.role] }}</span>
      <AppIcon name="cd" :size="14" class="chev" />
    </button>

    <Transition name="menu-drop">
      <div v-if="open" class="menu" role="menu">
        <div class="menu-head">{{ auth.user?.name }} · {{ auth.user?.loginId }}</div>
        <button type="button" class="menu-item" role="menuitem" @click="onLogout">
          <AppIcon name="logout" :size="16" />로그아웃
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.user-wrap {
  position: relative;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-text);
  font-weight: 500;
  cursor: pointer;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-border);
  color: var(--color-text-sub);
  display: grid;
  place-items: center;
}

.chev {
  color: var(--color-text-weak);
}

/* 좁은 화면: 역할 글자 숨김 (누르면 메뉴에 이름·아이디가 나옴) */
@media (max-width: 1600px) {
  .role {
    display: none;
  }
}

.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  width: 210px;
  padding: 6px;
  background: #fff;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.menu-head {
  padding: 8px 10px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  font-size: 14px;
  cursor: pointer;
}

.menu-item:hover {
  background: var(--color-surface-sub);
}
</style>
