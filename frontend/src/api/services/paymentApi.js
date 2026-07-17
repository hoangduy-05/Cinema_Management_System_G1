import axiosClient from "@/api/client/axiosClient";

export const paymentApi = {
  getLatest: (bookingId) => axiosClient.get(`/payments/booking/${bookingId}/latest`),
  confirmBrowser: (paymentId) => axiosClient.post(`/payments/${paymentId}/browser-confirm`),
};
