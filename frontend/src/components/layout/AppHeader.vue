<template>
  <aside
    class="app-sidebar"
    :class="{
      collapsed: navCollapsed,
      mobile: isMobile,
      open: mobileOpen,
    }"
  >
    <div class="sidebar-inner">
      <div
        class="branding"
        @click="goHome"
      >
        <span class="logo">PM</span>
        <div
          v-if="!navCollapsed || isMobile"
          class="brand-text"
        >
          <p class="app-name">
            {{ appName }}
          </p>
          <small class="app-subtitle">Picture Management</small>
        </div>
      </div>

      <div class="sidebar-actions">
        <el-button
          circle
          class="collapse-btn"
          :aria-label="navCollapsed ? '展开侧边栏' : '收起侧边栏'"
          :icon="navCollapsed ? Expand : Fold"
          @click="emitToggle"
        />
      </div>

      <el-divider class="sidebar-divider" />

      <el-menu
        class="nav-menu"
        :default-active="activeIndex"
        :collapse="navCollapsed && !isMobile"
        :collapse-transition="false"
        :router="false"
      >
        <el-menu-item
          v-for="item in navItems"
          :key="item.path"
          :index="item.path"
          @click="navigateTo(item.path)"
        >
          <component
            :is="item.icon"
            class="menu-icon"
          />
          <span
            v-if="!navCollapsed || isMobile"
            class="menu-label"
          >
            {{ item.label }}
          </span>
        </el-menu-item>
      </el-menu>

      <div
        v-if="isAuthenticated"
        class="sidebar-footer"
      >
        <div
          class="user-box"
          :class="{ centered: navCollapsed && !isMobile }"
        >
          <div class="avatar-pill">
            {{ userInitials }}
          </div>
          <div
            v-if="!navCollapsed || isMobile"
            class="user-meta"
          >
            <p class="user-name">
              {{ user?.username }}
            </p>
            <small class="user-email">{{ user?.email }}</small>
          </div>
        </div>
        <el-button
          type="primary"
          plain
          class="logout-btn"
          :icon="SwitchButton"
          @click="handleLogout"
        >
          <span v-if="!navCollapsed || isMobile">退出登录</span>
        </el-button>
      </div>

      <div
        v-else
        class="guest-actions"
      >
        <el-button
          type="primary"
          @click="navigateTo('/auth/login')"
        >
          登录
        </el-button>
        <el-button
          plain
          @click="navigateTo('/auth/register')"
        >
          注册
        </el-button>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {
  ChatDotRound,
  Expand,
  Fold,
  House,
  Search,
  SwitchButton,
  UploadFilled,
} from "@element-plus/icons-vue";
import { computed, toRefs } from "vue";
import { storeToRefs } from "pinia";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";

import { useAuthStore } from "@/stores/auth";

const props = defineProps<{
  collapsed: boolean;
  isMobile: boolean;
  mobileOpen: boolean;
}>();

const emit = defineEmits<{
  (event: "toggle-collapse"): void;
  (event: "close-mobile"): void;
}>();

const { isMobile, mobileOpen, collapsed } = toRefs(props);

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const { isAuthenticated, user } = storeToRefs(authStore);

const appName = computed(
  () => import.meta.env.VITE_APP_NAME || "Picture Management"
);

const navCollapsed = computed(() => collapsed.value && !isMobile.value);

const navItems = [
  { label: "首页", path: "/dashboard", icon: House },
  { label: "上传图片", path: "/images/upload", icon: UploadFilled },
  { label: "搜索图片", path: "/images/search", icon: Search },
  { label: "AI 对话", path: "/ai/chat", icon: ChatDotRound },
];

const activeIndex = computed(() => {
  const path = route.path;
  if (path.startsWith("/images/")) {
    if (path.includes("/upload")) return "/images/upload";
    return "/images/search";
  }
  if (path.startsWith("/ai/")) return "/ai/chat";
  return navItems.find((item) => path.startsWith(item.path))?.path || path;
});

const userInitials = computed(() => {
  if (!user.value?.username) return "PM";
  return user.value.username.slice(0, 2).toUpperCase();
});

const emitToggle = () => {
  emit("toggle-collapse");
};

const goHome = () => {
  if (isAuthenticated.value) {
    router.push({ path: "/dashboard" });
  } else {
    router.push({ path: "/auth/login" });
  }
  emit("close-mobile");
};

const navigateTo = (path: string) => {
  if (route.path === path) {
    emit("close-mobile");
    return;
  }
  router.push({ path });
  emit("close-mobile");
};

const handleLogout = async () => {
  await authStore.logout();
  ElMessage.success("已退出登录");
  router.push({ path: "/auth/login" });
  emit("close-mobile");
};
</script>

<style scoped>
.app-sidebar {
  width: var(--sidebar-width);
  background: var(--pm-surface);
  border-right: 1px solid var(--pm-border);
  min-height: 100vh;
  position: sticky;
  top: 0;
  left: 0;
  transition: width 0.2s ease, transform 0.2s ease;
  z-index: 70;
}

.sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 12px;
  height: 100%;
}

.branding {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 8px 6px;
  border-radius: 10px;
  transition: background 0.2s ease;
}

.branding:hover {
  background: rgba(64, 158, 255, 0.08);
}

.logo {
  font-size: 22px;
  font-weight: 800;
  color: #1f2d3d;
}

.app-name {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}

.app-subtitle {
  margin: 0;
  color: rgba(0, 0, 0, 0.45);
}

.sidebar-actions {
  display: flex;
  justify-content: flex-end;
}

.collapse-btn {
  border: 1px solid var(--pm-border);
}

.sidebar-divider {
  margin: 4px 0 8px;
}

.nav-menu {
  flex: 1;
  border: none;
}

.menu-icon {
  font-size: 18px;
  width: 20px;
  height: 20px;
}

.nav-menu :deep(.el-menu-item .el-icon) {
  width: 20px;
  height: 20px;
  font-size: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
}

.user-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(64, 158, 255, 0.08);
}

.user-box.centered {
  justify-content: center;
}

.avatar-pill {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: #409eff;
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  margin: 0;
  font-weight: 600;
}

.user-email {
  color: rgba(0, 0, 0, 0.55);
}

.logout-btn {
  width: 100%;
}

.guest-actions {
  display: flex;
  gap: 8px;
}

.guest-actions .el-button {
  flex: 1 1 0;
}

.app-sidebar.collapsed {
  width: var(--sidebar-collapsed);
}

.app-sidebar.mobile {
  position: fixed;
  height: 100vh;
  transform: translateX(-100%);
  width: var(--sidebar-expanded);
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.18);
}

.app-sidebar.mobile.open {
  transform: translateX(0);
}

.app-sidebar.mobile .sidebar-inner {
  padding-top: 18px;
}

@media (max-width: 900px) {
  .app-sidebar {
    width: 0;
    transform: translateX(-100%);
  }

  .app-sidebar.open {
    transform: translateX(0);
  }
}
</style>
