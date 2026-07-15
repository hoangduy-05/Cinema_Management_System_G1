import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { "@": path.resolve(__dirname, "./src") } },
  server: {
    port: 5173,
    proxy: {
      // BE Spring Boot. Không tìm thấy server.port trong properties -> mặc định 8080.
      // Nếu BE chạy cổng khác, đổi ở đây rồi RESTART vite.
      "/api": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
});
