import axios from "axios";
import router from "@/router";
import { useAuthStore } from "@/stores/auth";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
});

// Attach Authorization header from localStorage on every request.
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("pm_auth_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On auth errors, clear session and redirect to login so the UI stops spamming 403s.
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status;
    if (status === 401 || status === 403) {
      const authStore = useAuthStore();
      await authStore.logout({ silent: true, notifyServer: false });
      router.replace({
        name: "login",
        query: { redirect: router.currentRoute.value.fullPath },
      });
    }
    return Promise.reject(error);
  }
);

export default apiClient;
