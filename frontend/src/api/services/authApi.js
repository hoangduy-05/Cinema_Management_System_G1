import axiosClient from "@/api/client/axiosClient";

export const authApi = {
  // POST /api/v1/auth/login  body: { usernameOrEmail, password }
  login: ({ usernameOrEmail, password }) =>
    axiosClient.post("/auth/login", { usernameOrEmail, password }),

  register: (payload) => axiosClient.post("/auth/register", payload),
};
