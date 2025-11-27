<template>
  <section class="dashboard">
    <el-row :gutter="24">
      <el-col
        :sm="24"
        :lg="12"
      >
        <el-card
          shadow="never"
          class="welcome-card"
        >
          <h2>Hello, {{ user?.username }}</h2>
          <p>
            Use the sidebar (coming soon) to upload, search, and manage your
            images.
          </p>
          <el-alert
            type="info"
            show-icon
            :closable="false"
          >
            Authentication is ready. Hook up future modules to the shared API
            client to reuse JWT handling.
          </el-alert>
        </el-card>
      </el-col>
      <el-col
        :sm="24"
        :lg="12"
      >
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>Session Overview</span>
            </div>
          </template>
          <el-descriptions :column="1">
            <el-descriptions-item label="Username">
              {{
                user?.username
              }}
            </el-descriptions-item>
            <el-descriptions-item label="Email">
              {{
                user?.email
              }}
            </el-descriptions-item>
            <el-descriptions-item label="Role">
              {{
                user?.role
              }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";

import { useAuthStore } from "@/stores/auth";

const authStore = useAuthStore();
const { user } = storeToRefs(authStore);
</script>

<style scoped>
.dashboard {
  max-width: 1100px;
  margin: 0 auto;
}

.welcome-card {
  margin-bottom: 24px;
}

.card-header {
  font-weight: 600;
}
</style>
