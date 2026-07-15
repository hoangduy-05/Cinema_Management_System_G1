import axiosClient from "@/api/client/axiosClient";

export const showtimeApi = {
  // GET /api/v1/movies/{movieId}/showtimes?date=YYYY-MM-DD[&branchId=]
  // -> List<BranchShowtimesResponse>: [{ branch:{...}, showtimes:[...] }]
  getShowtimesByMovie: (movieId, date, branchId) =>
    axiosClient.get(`/movies/${movieId}/showtimes`, {
      params: { date, ...(branchId ? { branchId } : {}) },
    }),

  // GET /api/v1/showtimes/{id} -> ShowtimeDetailResponse (phim/rạp/phòng/giờ)
  getShowtimeDetail: (showtimeId) => axiosClient.get(`/showtimes/${showtimeId}`),

  // GET /api/v1/showtimes/{id}/seats -> List<ShowtimeSeatResponse> (danh sách ghế PHẲNG)
  getShowtimeSeats: (showtimeId) =>
    axiosClient.get(`/showtimes/${showtimeId}/seats`),

  // GET /api/v1/showtimes/dates?movieId= -> [{ date }]
  getAvailableDates: (movieId, branchId) =>
    axiosClient.get("/showtimes/dates", {
      params: { ...(movieId ? { movieId } : {}), ...(branchId ? { branchId } : {}) },
    }),
};
