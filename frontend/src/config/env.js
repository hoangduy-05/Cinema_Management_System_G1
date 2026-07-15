// Dùng đường dẫn tương đối để Vite proxy chuyển tiếp sang backend.
// Khi có BE thật: chỉ đổi target trong vite.config.js (server.proxy), KHÔNG sửa FE.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api/v1";

export const SEAT_LOCK_MINUTES = Number(
  import.meta.env.VITE_SEAT_LOCK_MINUTES || 7
);
