/**
 * 열화상 사진 주소 → <img src> 에 넣을 주소 (로그인 필요한 서버 사진은 blob: 주소로)
 * - ThermalImage.vue(설비 상세 · 주의 상세 · 경보 창 · 화재 창)와 이력 상세 패널이 같이 씀
 * - 주소가 바뀌거나 화면이 닫히면 이전 blob 주소를 풀어 줌
 * - 받기 실패하면 null → 부르는 쪽이 '열화상 없음' 처럼 보여 줌
 */
import { onBeforeUnmount, ref, toValue, watch } from 'vue'
import { loadThermalImage } from '@/api/equipment'

/** @param {import('vue').MaybeRefOrGetter<string|null>} imageUrl */
export function useThermalSrc(imageUrl) {
  const src = ref(null)
  let revoke = () => {}

  watch(
    () => toValue(imageUrl),
    async (url) => {
      revoke()
      revoke = () => {}
      src.value = null
      if (!url) return
      try {
        const r = await loadThermalImage(url)
        // 받는 사이 주소가 또 바뀌었으면 이번 것은 버림
        if (toValue(imageUrl) !== url) return r.revoke()
        src.value = r.url
        revoke = r.revoke
      } catch {
        src.value = null
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(() => revoke())
  return src
}
