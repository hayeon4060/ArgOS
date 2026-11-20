<!--
  로그인 직후 불러오기 화면 (설계안과 같은 3단계 문구 + 진행 막대)
  - user 가 들어오면 단계를 차례로 보여 주고(약 2초) 끝나면 done 이벤트
  - 실제 데이터 로딩과 연결된 것은 아니고 화면 전환 연출용
-->
<script setup>
import { ref, watch } from 'vue'
import AppLogo from '@/components/AppLogo.vue'
import { ROLE, ROLE_LABEL } from '@/types/codes'

const props = defineProps({
  /** 로그인한 사용자 (null 이면 숨김) */
  user: { type: Object, default: null },
})

const emit = defineEmits(['done'])

const text = ref('')
const percent = ref(0)

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** 권한별 단계 문구: [문구, 진행률] */
function stepsOf(user) {
  const second = user.role === ROLE.HQ_ADMIN ? '사업장 정보를 불러오는 중' : '담당 공장 정보를 불러오는 중'
  return [
    ['계정 권한을 확인하는 중', 30],
    [second, 68],
    ['센서와 연결하는 중', 100],
  ]
}

watch(
  () => props.user,
  async (user) => {
    if (!user) return
    percent.value = 0
    for (const [stepText, stepPercent] of stepsOf(user)) {
      text.value = stepText
      percent.value = stepPercent
      await wait(620)
    }
    await wait(200)
    emit('done')
  },
)
</script>

<template>
  <div class="splash" :class="{ on: user }" aria-live="polite">
    <template v-if="user">
      <AppLogo big />
      <p>{{ text }}</p>
      <div class="bar"><i :style="{ width: `${percent}%` }" /></div>
      <small>{{ user.name }} · {{ ROLE_LABEL[user.role] }}</small>
    </template>
  </div>
</template>

<style scoped>
.splash {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(243, 245, 248, 0.9);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.35s ease-out 0.1s;
}

.splash.on {
  opacity: 1;
  pointer-events: auto;
}

p {
  height: 20px;
  margin-top: 26px;
  font-size: 14px;
  color: var(--color-text-sub);
}

.bar {
  width: 260px;
  height: 4px;
  margin-top: 12px;
  border-radius: 2px;
  background: #dde3ea;
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: var(--color-primary);
  transition: width 0.55s cubic-bezier(0.3, 0.6, 0.3, 1);
}

small {
  margin-top: 14px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}
</style>
