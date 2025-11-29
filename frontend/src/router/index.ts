import { createRouter, createWebHistory } from "vue-router";
import type { NavigationGuardNext, RouteLocationNormalized } from "vue-router";

import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      redirect: "/dashboard",
    },
    {
      path: "/dashboard",
      name: "dashboard",
      component: () => import("@/views/DashboardHome.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/images/upload",
      name: "image-upload",
      component: () => import("@/views/ImageUpload.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/images/:imageId/tags",
      name: "image-tags",
      component: () => import("@/views/ImageTagManager.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/auth/login",
      name: "login",
      component: () => import("@/views/AuthLogin.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/auth/register",
      name: "register",
      component: () => import("@/views/AuthRegister.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/:pathMatch(.*)*",
      redirect: "/dashboard",
    },
  ],
});

const guard = (
  to: RouteLocationNormalized,
  _from: RouteLocationNormalized,
  next: NavigationGuardNext
) => {
  const authStore = useAuthStore();
  authStore.initialize();

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next({
      path: "/auth/login",
      query: { redirect: to.fullPath },
    });
    return;
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    next({ path: "/dashboard" });
    return;
  }

  next();
};

router.beforeEach(guard);

export default router;
