<!-- 提供不超过五项、适配 iPhone 安全区的底部导航。 -->
<script setup lang="ts">
const items = [
  { to: '/messages', label: '消息', icon: 'message' },
  { to: '/calls', label: '通话', icon: 'phone' },
  { to: '/device', label: '设备', icon: 'device' },
  { to: '/settings', label: '设置', icon: 'settings' },
]
</script>

<template>
  <nav class="app-nav" aria-label="主导航">
    <RouterLink v-for="item in items" :key="item.to" :to="item.to">
      <svg v-if="item.icon === 'message'" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h16v12H8l-4 3zM8 9h8M8 13h5" /></svg>
      <svg v-else-if="item.icon === 'phone'" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 3l3 4-2 2c1.6 3.1 3.9 5.4 7 7l2-2 4 3-2 4C10 20 4 14 3 5z" /></svg>
      <svg v-else-if="item.icon === 'device'" viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="2.5" width="12" height="19" rx="2"/><path d="M10 18h4" /></svg>
      <svg v-else viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2" /></svg>
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>
