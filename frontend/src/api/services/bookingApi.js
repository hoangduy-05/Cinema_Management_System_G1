import axiosClient from "@/api/client/axiosClient";

export const bookingApi = {
  // POST /api/v1/bookings/holds  (CẦN JWT)
  // body: { showtimeId, showtimeSeatIds: [...] }  <-- gửi showtimeSeatId, KHÔNG phải seatId
  holdSeats: (showtimeId, showtimeSeatIds) =>
    axiosClient.post("/bookings/holds", { showtimeId, showtimeSeatIds }),

  getSummary: (bookingId) => axiosClient.get(`/bookings/${bookingId}/summary`),
  updateCombos: (bookingId, items) =>
    axiosClient.put(`/bookings/${bookingId}/combos`, { items }),
  cancel: (bookingId) => axiosClient.post(`/bookings/${bookingId}/cancel`),
  checkout: (bookingId, paymentMethod) =>
    axiosClient.post(`/bookings/${bookingId}/checkout`, { paymentMethod }),
};
