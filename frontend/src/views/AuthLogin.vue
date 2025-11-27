<template>
  <section class="auth-page">
    <el-card
      class="auth-card"
      shadow="hover"
    >
      <template #header>
        <div class="card-header">
          <h2>Welcome Back</h2>
          <p>Sign in to manage your picture library</p>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item
          label="Username or Email"
          prop="usernameOrEmail"
        >
          <el-input
            v-model="form.usernameOrEmail"
            placeholder="Enter username or email"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item
          label="Password"
          prop="password"
        >
          <el-input
            v-model="form.password"
            type="password"
            placeholder="Enter password"
            autocomplete="current-password"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="submit-btn"
            @click="handleSubmit"
          >
            Login
          </el-button>
        </el-form-item>
      </el-form>

      <p class="switch-text">
        Don't have an account?
        <router-link to="/auth/register">
          Create one
        </router-link>
      </p>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { isAxiosError } from "axios";
import type { AxiosError } from "axios";

import { useAuthStore } from "@/stores/auth";
import type { LoginPayload } from "@/types/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive<LoginPayload>({
  usernameOrEmail: "",
  password: "",
});

const rules = reactive<FormRules>({
  usernameOrEmail: [
    {
      required: true,
      message: "Username or email is required",
      trigger: "blur",
    },
  ],
  password: [
    { required: true, message: "Password is required", trigger: "blur" },
    {
      min: 6,
      message: "Password must be at least 6 characters",
      trigger: "blur",
    },
  ],
});

const resolveErrorMessage = (error: unknown): string => {
  if (isAxiosError(error)) {
    const axiosError = error as AxiosError<{ message?: string }>;
    return (
      axiosError.response?.data?.message || axiosError.message || "Login failed"
    );
  }
  return (error as Error)?.message || "Login failed";
};

const handleSubmit = () => {
  if (!formRef.value) {
    return;
  }

  formRef.value.validate(async (valid) => {
    if (!valid) {
      return;
    }

    try {
      loading.value = true;
      await authStore.login({ ...form });
      const redirect = (route.query.redirect as string) || "/dashboard";
      router.push(redirect);
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error));
    } finally {
      loading.value = false;
    }
  });
};
</script>

<style scoped>
.auth-page {
  max-width: 420px;
  margin: 0 auto;
  padding-top: 48px;
}

.auth-card {
  border-radius: 16px;
}

.card-header h2 {
  margin: 0 0 4px;
}

.card-header p {
  margin: 0;
  color: rgba(0, 0, 0, 0.55);
}

.submit-btn {
  width: 100%;
}

.switch-text {
  text-align: center;
  margin: 16px 0 0;
}
</style>
