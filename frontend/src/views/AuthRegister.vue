<template>
  <section class="auth-page">
    <el-card class="auth-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <h2>Create Account</h2>
          <p>Register to start organizing your images</p>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="Username" prop="username">
          <el-input
            v-model="form.username"
            placeholder="Choose a username"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="Email" prop="email">
          <el-input
            v-model="form.email"
            placeholder="Enter your email"
            autocomplete="email"
          />
        </el-form-item>

        <el-form-item label="Password" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="Create a password"
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="submit-btn"
            @click="handleSubmit"
          >
            Register
          </el-button>
        </el-form-item>
      </el-form>

      <p class="switch-text">
        Already have an account?
        <router-link to="/auth/login"> Sign in </router-link>
      </p>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { isAxiosError } from "axios";
import type { AxiosError } from "axios";

import { useAuthStore } from "@/stores/auth";
import type { RegisterPayload } from "@/types/auth";

const router = useRouter();
const authStore = useAuthStore();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive<RegisterPayload>({
  username: "",
  email: "",
  password: "",
});

const rules = reactive<FormRules>({
  username: [
    { required: true, message: "Username is required", trigger: "blur" },
    {
      min: 6,
      message: "Username must be at least 6 characters",
      trigger: "blur",
    },
  ],
  email: [
    { required: true, message: "Email is required", trigger: "blur" },
    { type: "email", message: "Please enter a valid email", trigger: "blur" },
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
      axiosError.response?.data?.message ||
      axiosError.message ||
      "Registration failed"
    );
  }
  return (error as Error)?.message || "Registration failed";
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
      await authStore.register({ ...form });
      router.push("/dashboard");
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
  max-width: 480px;
  width: 100%;
  margin: 0 auto;
  min-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 16px;
}

.auth-card {
  border-radius: 16px;
  width: 100%;
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

@media (max-width: 540px) {
  .auth-page {
    padding: 32px 12px 48px;
  }
}
</style>
