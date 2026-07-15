import { createSlice } from "@reduxjs/toolkit";
import { tokenStorage } from "@/security/tokenStorage";

const initialState = {
  user: JSON.parse(localStorage.getItem("cms_user") || "null"),
  token: tokenStorage.get() || null,
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setCredentials: (state, { payload }) => {
      state.user = payload.user;
      state.token = payload.token;
      tokenStorage.set(payload.token);
      localStorage.setItem("cms_user", JSON.stringify(payload.user));
    },
    logout: (state) => {
      state.user = null;
      state.token = null;
      tokenStorage.clear();
      localStorage.removeItem("cms_user");
    },
  },
});

export const { setCredentials, logout } = authSlice.actions;
export default authSlice.reducer;
