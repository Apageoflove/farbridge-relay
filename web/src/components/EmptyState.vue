<!-- 为无数据和失败场景提供明确原因与下一步操作。 -->
<script setup lang="ts">
defineProps<{ title: string; detail: string; actionLabel: string }>()
const emit = defineEmits<{ action: [] }>()
</script>

<template>
  <section class="empty-state" aria-labelledby="empty-title">
    <svg class="empty-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 5.5h16v13H4zM8 9h8M8 13h5" />
    </svg>
    <h2 id="empty-title">{{ title }}</h2>
    <p>{{ detail }}</p>
    <button class="secondary-button" type="button" @click="emit('action')">{{ actionLabel }}</button>
  </section>
</template>
