import axiosClient from "@/api/client/axiosClient";

export const concessionApi = {
  getCombos: () => axiosClient.get("/combos"),
};
