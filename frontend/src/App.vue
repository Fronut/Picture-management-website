<template>
  <el-container
    class="app-shell"
    :class="{
      'is-collapsed': collapsed && !isMobile,
      'is-mobile': isMobile,
      'sidebar-open': sidebarOpen,
    }"
    :style="{
      '--sidebar-width': `${sidebarWidth}px`,
      '--sidebar-expanded': `${SIDEBAR_EXPANDED}px`,
      '--sidebar-collapsed': `${SIDEBAR_COLLAPSED}px`,
    }"
  >
    <app-header
      :collapsed="collapsed"
      :is-mobile="isMobile"
      :mobile-open="mobileOpen"
      @toggle-collapse="toggleSidebar"
      @close-mobile="closeMobile"
    />

    <div class="app-body">
      <header class="mobile-topbar" v-if="isMobile">
        <el-button
          circle
          class="ghost-btn"
          :icon="mobileOpen ? Close : Menu"
          @click="toggleSidebar"
        />
        <div class="topbar-title">{{ appName }}</div>
      </header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </div>

    <div
      v-if="isMobile && mobileOpen"
      class="sidebar-backdrop"
      @click="closeMobile"
    ></div>
  </el-container>
</template>

<script setup lang="ts">
import { Close, Menu } from "@element-plus/icons-vue";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

import AppHeader from "@/components/layout/AppHeader.vue";

const SIDEBAR_EXPANDED = 248;
const SIDEBAR_COLLAPSED = 72;

const collapsed = ref(false);
const mobileOpen = ref(false);
const isMobile = ref(false);

const appName = computed(
  () => import.meta.env.VITE_APP_NAME || "Picture Management"
);

const sidebarWidth = computed(() => {
  if (isMobile.value) {
    return mobileOpen.value ? SIDEBAR_EXPANDED : 0;
  }
  return collapsed.value ? SIDEBAR_COLLAPSED : SIDEBAR_EXPANDED;
});

const sidebarOpen = computed(() => (isMobile.value ? mobileOpen.value : true));

const toggleSidebar = () => {
  if (isMobile.value) {
    mobileOpen.value = !mobileOpen.value;
    return;
  }
  collapsed.value = !collapsed.value;
};

const closeMobile = () => {
  if (isMobile.value) {
    mobileOpen.value = false;
  }
};

const handleResize = () => {
  const mobile = window.innerWidth < 900;
  isMobile.value = mobile;
  if (!mobile) {
    mobileOpen.value = false;
  }
};

onMounted(() => {
  handleResize();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
});
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--pm-background);
  display: flex;
  position: relative;
  overflow: hidden;
}

.app-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.is-mobile .app-body {
  margin-left: 0;
}

.app-main {
  flex: 1;
  padding: clamp(16px, 4vw, 32px);
}

.mobile-topbar {
  display: none;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  position: sticky;
  top: 0;
  background: var(--pm-surface);
  z-index: 50;
  border-bottom: 1px solid var(--pm-border);
}

.topbar-title {
  font-weight: 600;
}

.ghost-btn {
  border: 1px solid var(--pm-border);
}

.sidebar-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 60;
}

@media (max-width: 900px) {
  .mobile-topbar {
    display: flex;
  }

  .app-main {
    padding: 16px 12px 24px;
  }
}

@media (max-width: 640px) {
  .app-main {
    padding: 12px 10px 20px;
  }
}
</style>
