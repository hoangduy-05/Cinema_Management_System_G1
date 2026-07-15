import { createSlice } from "@reduxjs/toolkit";

const initialState = {
  showtimeId: null,
  selectedSeats: [], // [{ seatId, row, number, seatTypeId, typeName, price }]
  lockId: null,
  lockedUntil: null,
};

const bookingSlice = createSlice({
  name: "booking",
  initialState,
  reducers: {
    startBooking: (state, { payload }) => {
      state.showtimeId = payload.showtimeId;
      state.selectedSeats = [];
      state.lockId = null;
      state.lockedUntil = null;
    },
    toggleSeat: (state, { payload: seat }) => {
      const i = state.selectedSeats.findIndex((s) => s.seatId === seat.seatId);
      if (i >= 0) state.selectedSeats.splice(i, 1);
      else state.selectedSeats.push(seat);
    },
    setLock: (state, { payload }) => {
      state.lockId = payload.lockId;
      state.lockedUntil = payload.lockedUntil;
    },
    clearBooking: () => initialState,
  },
});

export const { startBooking, toggleSeat, setLock, clearBooking } =
  bookingSlice.actions;
export default bookingSlice.reducer;
