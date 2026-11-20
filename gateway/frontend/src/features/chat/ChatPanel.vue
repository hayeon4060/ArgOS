<!--
  채팅 패널 (화면설계안 rev-shell.js chatPanel() · css/rev.css .chatp, 화면설계서 I2)
  - 상단 바 아래 오른쪽 400px, 화면 내용을 밀지 않고 위에 덮음. 열어 둔 채로 다른 화면을 볼 수 있음
  - 오른쪽에서 밀려 나옴(0.22초) / 들어감(0.18초) — 설계안 chatin · chatout
  - ① 대화 상대: 본사 관리자는 사업장별 공장 담당자, 공장 담당자는 본사 관리자. 접속 중 초록 점, 안 읽은 수
  - ② 글 목록 (ChatMessageList)
  - 지금 진행 중인 알림이 있으면 '지금 알림 첨부' 줄 → 누르면 그 알림 카드를 붙여 보냄 (설계안)
  - ③ 입력: Enter 보내기, 최대 500자. 닫았다 열어도 입력하던 글은 남음
  - 닫기: X, 상단 바 아이콘 다시 누르기, Esc
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ChatMessageList from './ChatMessageList.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAlertStore } from '@/stores/alert'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { ROLE } from '@/types/codes'

const chat = useChatStore()
const alert = useAlertStore()
const auth = useAuthStore()

const isHq = computed(() => auth.role === ROLE.HQ_ADMIN)
/** 방 이름: 본사 관리자는 '울산공장 · 한지우', 공장 담당자는 '본사 관제 · 서지훈' (설계안) */
const roomName = (r) => (isHq.value ? `${r.siteName} · ${r.peer.name}` : `본사 관제 · ${r.peer.name}`)
const roleLabel = (r) => (r.peer.role === ROLE.HQ_ADMIN ? '본사 관리자' : '공장 담당자')

/** 붙일 수 있는 알림: 지금 대응 중인 대표 알림 (설계안: 주의 · 경보 · 화재 중 하나) */
const attachable = computed(() => alert.primary)

const input = ref(null)
function onSend() {
  const text = chat.draft
  if (!text.trim()) return
  chat.draft = ''
  chat.send(text)
}
function onAttach() {
  chat.send('현재 알림 공유드립니다.', attachable.value)
}

/** 열리면 입력 칸에 커서 (설계안 0.25초 뒤) */
watch(
  () => chat.open,
  (v) => v && setTimeout(() => input.value?.focus(), 250),
)

function onKey(e) {
  if (e.key === 'Escape' && chat.open) chat.close()
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <Transition name="chatp">
      <aside v-if="chat.open" class="chatp" aria-label="채팅">
        <header class="cph">
          <b>채팅</b>
          <span class="s">본사 관리자 · 공장 담당자</span>
          <button type="button" class="x" aria-label="닫기" @click="chat.close()"><AppIcon name="x" :size="18" /></button>
        </header>

        <!-- ① 대화 상대 -->
        <div class="cpr" role="tablist">
          <button
            v-for="r in chat.rooms"
            :key="r.roomId"
            type="button"
            role="tab"
            :aria-selected="r.roomId === chat.roomId"
            :class="{ on: r.roomId === chat.roomId }"
            @click="chat.selectRoom(r.roomId)"
          >
            <span class="av">{{ r.peer.name[0] }}</span>
            <span class="tx">
              <b>{{ roomName(r) }}</b>
              <small>{{ r.lastMessage?.body ?? roleLabel(r) }}</small>
            </span>
            <em v-if="r.unreadCount && r.roomId !== chat.roomId" class="cnt">{{ r.unreadCount }}</em>
            <i v-if="r.peer.online" class="online" title="접속 중" />
          </button>
          <p v-if="!chat.rooms.length" class="none">대화할 상대가 없습니다.</p>
        </div>

        <!-- ② 글 -->
        <ChatMessageList
          :items="chat.thread.items"
          :has-more="chat.thread.hasMore"
          :loading="chat.thread.loading"
          :room-key="chat.roomId"
          @older="chat.loadMessages({ older: true })"
          @retry="chat.retry"
        />

        <!-- 지금 알림 붙이기 -->
        <button v-if="attachable && chat.room" type="button" class="cpa" @click="onAttach">
          <AppIcon name="clip" :size="15" /><span>지금 알림 첨부</span>
          <b>{{ attachable.title }} · {{ attachable.location.split(' · ').at(-1) }}</b>
        </button>

        <!-- ③ 입력 -->
        <form class="cpi" autocomplete="off" @submit.prevent="onSend">
          <input
            ref="input"
            v-model="chat.draft"
            maxlength="500"
            :disabled="!chat.room"
            :placeholder="chat.room ? `${chat.room.peer.name} 님에게 보내기` : ''"
            aria-label="메시지"
          />
          <button class="btn btn-primary send" type="submit" aria-label="보내기" :disabled="!chat.draft.trim()"><AppIcon name="send" :size="17" /></button>
        </form>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.chatp {
  position: fixed;
  top: var(--topbar-height);
  right: 0;
  bottom: 0;
  z-index: 60; /* 화면 위, 알림 창(90~)·알림 띠(150) 아래 */
  width: 400px;
  max-width: var(--screen-w);
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--color-border);
  background: #fff;
  box-shadow: -12px 0 32px rgba(15, 23, 42, 0.1);
}

/* 오른쪽에서 밀려 나옴 0.22초, 들어감 0.18초 (설계안 chatin · chatout) */
.chatp-enter-active {
  transition: transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.chatp-leave-active {
  transition: transform 180ms ease-in;
}

.chatp-enter-from,
.chatp-leave-to {
  transform: translateX(100%);
}

.cph {
  height: 52px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px 0 20px;
  border-bottom: 1px solid var(--color-border);
}

.cph b {
  font-size: 16px;
}

.cph .s {
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.x {
  display: grid;
  place-items: center;
  margin-left: auto;
  padding: 4px;
  border: none;
  background: none;
  color: var(--color-text-weak);
  cursor: pointer;
}

.cpr {
  flex: none;
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid var(--color-border);
}

.cpr button {
  position: relative;
  height: 54px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background var(--m-hover);
}

.cpr button:hover {
  background: #f8fafc;
}

/* 고른 상대: 왼쪽 파란 막대 */
.cpr button.on {
  background: var(--color-primary-soft);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.av {
  width: 32px;
  height: 32px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e2e8f0;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-sub);
}

.on .av {
  background: #bfdbfe;
  color: #1d4ed8;
}

.tx {
  flex: 1;
  min-width: 0;
}

.tx b,
.tx small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx b {
  font-size: 14px;
}

.tx small {
  font-size: 12px;
  color: var(--color-text-weak);
}

.cnt {
  min-width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--color-level-alarm);
  color: #fff;
  font-size: 10.5px;
  font-style: normal;
  font-weight: 700;
  animation: pop-in var(--m-pop-in) both;
}

/* 접속 중 초록 점 */
.online {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: var(--color-level-normal);
  box-shadow: 0 0 0 3px #dcfce7;
}

.none {
  margin: 0;
  padding: 16px 20px;
  font-size: 13px;
  color: var(--color-text-weak);
}

/* 지금 알림 첨부 */
.cpa {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  border: none;
  border-top: 1px solid var(--color-border);
  background: #fff;
  font-size: 12.5px;
  color: var(--color-text-sub);
  text-align: left;
  cursor: pointer;
  transition: background var(--m-hover);
}

.cpa:hover {
  background: #f8fafc;
}

.cpa b {
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--color-text);
}

.cpi {
  flex: none;
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
  background: #fff;
}

.cpi input {
  flex: 1;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 14px;
  outline: none;
  transition:
    border-color 150ms,
    box-shadow 150ms;
}

.cpi input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.send {
  width: 44px;
  height: 40px;
  padding: 0;
  justify-content: center;
}

@media (prefers-reduced-motion: reduce) {
  .chatp-enter-active,
  .chatp-leave-active {
    transition: none;
  }
}
</style>
