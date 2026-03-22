<template>
  <div class="loading-container" :class="{ 'loading-overlay': overlay }">
    <div v-if="type === 'spinner'" class="loading-spinner">
      <svg viewBox="0 0 50 50" class="spinner-svg">
        <circle cx="25" cy="25" r="20" fill="none" stroke-width="4" />
      </svg>
      <span v-if="text" class="loading-text">{{ text }}</span>
    </div>

    <div v-else-if="type === 'dots'" class="loading-dots">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
      <span v-if="text" class="loading-text">{{ text }}</span>
    </div>

    <div v-else-if="type === 'pulse'" class="loading-pulse">
      <div class="pulse-ring"></div>
      <div class="pulse-ring"></div>
      <div class="pulse-ring"></div>
      <span v-if="text" class="loading-text">{{ text }}</span>
    </div>

    <Skeleton v-else-if="type === 'skeleton'" :variant="skeletonVariant" :rows="skeletonRows" :columns="skeletonColumns" />
  </div>
</template>

<script setup>
import Skeleton from './Skeleton.vue'

defineProps({
  type: {
    type: String,
    default: 'spinner',
    validator: (val) => ['spinner', 'dots', 'pulse', 'skeleton'].includes(val)
  },
  text: {
    type: String,
    default: ''
  },
  overlay: {
    type: Boolean,
    default: false
  },
  skeletonVariant: {
    type: String,
    default: 'text'
  },
  skeletonRows: {
    type: Number,
    default: 3
  },
  skeletonColumns: {
    type: Number,
    default: 4
  }
})
</script>

<style scoped>
.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-lg);
  width: 100%;
}

.loading-container.loading-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(4px);
  z-index: 100;
}

/* Spinner */
.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
}

.spinner-svg {
  width: 48px;
  height: 48px;
  animation: rotate 1.5s linear infinite;
}

.spinner-svg circle {
  stroke: var(--primary);
  stroke-linecap: round;
  stroke-dasharray: 90, 150;
  stroke-dashoffset: 0;
  animation: dash 1.5s ease-in-out infinite;
}

@keyframes rotate {
  100% {
    transform: rotate(360deg);
  }
}

@keyframes dash {
  0% {
    stroke-dasharray: 1, 150;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -35;
  }
  100% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -124;
  }
}

/* Dots */
.loading-dots {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--primary);
  animation: dotBounce 1.4s ease-in-out infinite;
}

.dot:nth-child(1) {
  animation-delay: 0s;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dotBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* Pulse */
.loading-pulse {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
}

.pulse-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 3px solid var(--primary);
  border-radius: 50%;
  opacity: 0;
  animation: pulseRing 2s cubic-bezier(0.215, 0.61, 0.355, 1) infinite;
}

.pulse-ring:nth-child(1) {
  animation-delay: 0s;
}

.pulse-ring:nth-child(2) {
  animation-delay: 0.5s;
}

.pulse-ring:nth-child(3) {
  animation-delay: 1s;
}

@keyframes pulseRing {
  0% {
    transform: scale(0.5);
    opacity: 1;
  }
  100% {
    transform: scale(1.5);
    opacity: 0;
  }
}

.loading-text {
  margin-top: var(--space-md);
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
