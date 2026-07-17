import axiosClient from "@/api/client/axiosClient";

export const bookingApi = {
  // POST /api/v1/bookings/holds  (CẦN JWT)
  // body: { showtimeId, showtimeSeatIds: [...] }  <-- gửi showtimeSeatId, KHÔNG phải seatId
  holdSeats: (showtimeId, showtimeSeatIds) =>
    axiosClient.post("/bookings/holds", { showtimeId, showtimeSeatIds }),

  getSummary: (bookingId) => axiosClient.get(`/bookings/${bookingId}/summary`),
  getMyHistory: ({ page = 0, size = 10, status } = {}) =>
    axiosClient.get("/bookings/me", { params: { page, size, ...(status ? { status } : {}) } }),
  updateCombos: (bookingId, items) =>
    axiosClient.put(`/bookings/${bookingId}/combos`, { items }),
  applyVoucher: (bookingId, voucherCode) =>
    axiosClient.post(`/bookings/${bookingId}/voucher`, { voucherCode }),
  removeVoucher: (bookingId) => axiosClient.delete(`/bookings/${bookingId}/voucher`),
  cancel: (bookingId) => axiosClient.post(`/bookings/${bookingId}/cancel`),
  checkout: (bookingId, paymentMethod) =>
    axiosClient.post(`/bookings/${bookingId}/checkout`, { paymentMethod }),
  retryPayment: (bookingId, paymentMethod) =>
    axiosClient.post(`/bookings/${bookingId}/payments/retry`, { paymentMethod }),
};
