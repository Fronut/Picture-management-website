<template>
  <el-header class="app-header">
    <div class="branding" @click="goHome">
      <span class="logo">PM</span>
      <div>
        <p class="app-name">
          {{ appName }}
        </p>
        <small class="app-subtitle">Picture Management Platform</small>
      </div>
    </div>
    <div class="spacer" />
    <template v-if="isAuthenticated">
      <el-space alignment="center" :size="16">
        <div class="user-info">
          <p class="user-name">
            {{ user?.username }}
          </p>
          <small class="user-email">{{ user?.email }}</small>
        </div>
        <el-button type="success" plain @click="navigateTo('/images/upload')">
          上传图片
        </el-button>
        <el-button type="primary" plain @click="navigateTo('/images/search')">
          搜索图片
        </el-button>
        <el-button type="primary" @click="handleLogout"> Logout </el-button>
      </el-space>
    </template>
    <template v-else>
      <el-button-group>
        <el-button @click="navigateTo('/auth/login')"> Login </el-button>
        <el-button type="primary" @click="navigateTo('/auth/register')">
          Register
        </el-button>
      </el-button-group>
    </template>
  </el-header>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";

import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const authStore = useAuthStore();
const { isAuthenticated, user } = storeToRefs(authStore);

const appName = computed(
  () => import.meta.env.VITE_APP_NAME || "Picture Management"
);

const goHome = () => {
  if (isAuthenticated.value) {
    router.push({ path: "/dashboard" });
    return;
  }
  router.push({ path: "/auth/login" });
};

const navigateTo = (path: string) => {
  router.push({ path });
};

const handleLogout = async () => {
  await authStore.logout();
  ElMessage.success("You have been logged out");
  router.push({ path: "/auth/login" });
};
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  padding: 0 24px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: var(--pm-surface);
}

.branding {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.logo {
  font-size: 28px;
}

.app-name {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.app-subtitle {
  margin: 0;
  color: rgba(0, 0, 0, 0.45);
}

.spacer {
  flex: 1;
}

.user-info {
  text-align: right;
}

.user-name {
  font-weight: 600;
  margin: 0;
}

.user-email {
  color: rgba(0, 0, 0, 0.45);
}
</style>
