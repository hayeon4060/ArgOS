<!--
  상단 바의 사업장 이름 표시 (예: 📍울산공장) — 설계안 .site
  - 본사 관리자: 누르면 사업장 선택 화면으로
  - 공장 담당자: 이름만 표시 (누를 수 없음)
-->
<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'

defineProps({
  name: { type: String, default: '' },
})

const router = useRouter()
const auth = useAuthStore()

const clickable = computed(() => auth.role === ROLE.HQ_ADMIN)

function onClick() {
  if (clickable.value) router.push({ name: 'site-select' })
}
</script>

<template>
  <button
    type="button"
    class="site-pill"
    :class="{ link: clickable }"
    :title="clickable ? '사업장 선택으로' : ''"
    :disabled="!clickable"
    @click="onClick"
  >
    <AppIcon name="pin" :size="16" />{{ name }}
  </button>
</template>

<style scoped>
.site-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid #bfdbfe;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  cursor: default;
}

/* disabled 여도 흐리게 보이지 않게 */
.site-pill:disabled {
  opacity: 1;
}

.site-pill.link {
  cursor: pointer;
}

.site-pill.link:hover {
  background: #dbeafe;
}
</style>
