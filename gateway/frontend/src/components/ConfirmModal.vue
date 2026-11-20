<!--
  확인 창 (화면설계안 js/core.js confirmBox())
  - 제목 + 내용(슬롯) + 취소 / 확인 단추. 바깥 누르기·Esc 는 취소
  - body 아래에 그려서 화면 전환·잘림에 영향받지 않게 (Teleport), 나타남·사라짐은 BaseModal 과 같음
  - 사용 예:
    <ConfirmModal :open="show" title="기준값을 저장할까요?" ok-text="저장" icon="save" @ok="save" @cancel="show = false">
      내용
    </ConfirmModal>
-->
<script setup>
import AppIcon from './AppIcon.vue'
import BaseModal from './BaseModal.vue'

defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  okText: { type: String, default: '확인' },
  /** 확인 단추 아이콘 (없으면 글자만) */
  icon: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  /** 확인 단추 끄기 (예: 필수 입력이 비었을 때) */
  okDisabled: { type: Boolean, default: false },
})

defineEmits(['ok', 'cancel'])
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="440" :label="title" @close="$emit('cancel')">
        <div class="confirm">
          <h2>{{ title }}</h2>
          <div class="body"><slot /></div>
          <div class="m-actions">
            <button type="button" class="btn" @click="$emit('cancel')">취소</button>
            <button type="button" class="btn btn-primary" :disabled="busy || okDisabled" @click="$emit('ok')">
              <span v-if="busy" class="m-spin" /><AppIcon v-else-if="icon" :name="icon" :size="16" />{{ busy ? '저장 중' : okText }}
            </button>
          </div>
        </div>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.confirm {
  padding: 24px 24px 20px;
}

h2 {
  font-size: 18px;
  font-weight: 700;
}

.body {
  margin-top: 10px;
  font-size: 14px;
  line-height: 22px;
  color: var(--color-text-sub);
}

.m-actions {
  justify-content: flex-end;
  margin-top: 20px;
}

.m-actions .btn {
  height: 40px;
  padding: 0 18px;
}
</style>
