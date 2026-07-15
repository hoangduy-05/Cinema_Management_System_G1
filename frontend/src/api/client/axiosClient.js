import axios from "axios";
import { API_BASE_URL } from "@/config/env";
import { tokenStorage } from "@/security/tokenStorage";

const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
  timeout: 15000,
});

axiosClient.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

axiosClient.interceptors.response.use(
  (res) => {
    const body = res.data;
    // BE bọc: { success, message, data, timestamp }
    if (body && typeof body === "object" && "success" in body) {
      if (body.success === false) {
        return Promise.reject(new Error(body.message || "Yêu cầu thất bại"));
      }
      return body.data;
    }
    return body;
  },
  (error) => {
    if (error.response?.status === 401) tokenStorage.clear();
    const msg =
      error.response?.data?.message ||
      error.message ||
      "Không kết nối được máy chủ";
    return Promise.reject(new Error(msg));
  }
);

export default axiosClient;
