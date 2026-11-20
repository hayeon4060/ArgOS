/**
 * 펼침 메뉴를 바깥을 누르거나 Esc 를 누르면 닫기 (화면설계안 js/core.js menu() 의 바깥 누름 처리)
 * - 상단 바 알림 종 목록, '관리' 메뉴, 사용자 메뉴가 같이 씀
 * - pointerdown 을 잡는 단계(capture)에서 봄 → 바깥의 다른 단추를 누른 순간 먼저 닫힘 (설계안과 같음)
 * - 사용 예:
 *     const open = ref(false)
 *     const root = ref(null)          // 단추와 메뉴를 함께 감싼 요소 (안쪽을 누르면 닫지 않음)
 *     useOutsideClose(root, open)
 */
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * @param {import('vue').Ref<HTMLElement|null>} rootRef 단추 + 메뉴를 감싼 요소
 * @param {import('vue').Ref<boolean>} openRef 열림 여부 (바깥을 누르면 false 로 바꿈)
 */
export function useOutsideClose(rootRef, openRef) {
  function onPointerDown(e) {
    if (openRef.value && rootRef.value && !rootRef.value.contains(e.target)) openRef.value = false
  }

  function onKeydown(e) {
    if (e.key === 'Escape' && openRef.value) openRef.value = false
  }

  onMounted(() => {
    document.addEventListener('pointerdown', onPointerDown, true)
    document.addEventListener('keydown', onKeydown)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('pointerdown', onPointerDown, true)
    document.removeEventListener('keydown', onKeydown)
  })
}
