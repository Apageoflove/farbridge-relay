<!-- 用可自定义昵称呈现安卓到 iPhone 的中继链路状态。 -->
<script setup lang="ts">
import type { DeviceStatusName } from '../types/device'
import { useRelayLabels } from '../composables/useRelayLabels'
withDefaults(defineProps<{ status?: DeviceStatusName; compact?: boolean }>(), { status: 'UNKNOWN', compact: false })
const { sourceLabel, targetLabel } = useRelayLabels()
</script>

<template>
  <div class="relay-spine" :class="{ compact }" :aria-label="`${sourceLabel} 到 ${targetLabel}，中继链路状态 ${status}`" role="img">
    <div class="relay-node" :class="status.toLowerCase()" data-testid="relay-source">
      <span class="relay-dot" aria-hidden="true"></span><span>{{ sourceLabel }}</span>
    </div>
    <span class="relay-line" aria-hidden="true"><i class="relay-arrow" data-testid="relay-arrow">→</i></span>
    <div class="relay-node iphone" data-testid="relay-target"><span class="relay-dot" aria-hidden="true"></span><span>{{ targetLabel }}</span></div>
  </div>
</template>
