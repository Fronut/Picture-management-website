<template>
  <el-container class="app-shell">
    <app-header />
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import AppHeader from "@/components/layout/AppHeader.vue";
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--pm-background);
  display: flex;
  flex-direction: column;
}

.app-main {
  flex: 1;
  padding: 24px;
}
</style>
