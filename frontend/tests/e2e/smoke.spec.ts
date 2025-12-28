import { test, expect } from "@playwright/test";

test.describe("Landing page", () => {
  test("renders the application shell", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Picture Management/i);
    await expect(page.locator("#app")).toBeVisible();
  });
});
