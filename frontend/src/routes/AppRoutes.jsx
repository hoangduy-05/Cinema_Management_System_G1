import { Routes, Route } from "react-router-dom";
import HomePage from "@/pages/customer/HomePage";
import LoginPage from "@/features/auth/pages/LoginPage";
import SeatSelectionPage from "@/features/booking-ticketing/pages/SeatSelectionPage";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/booking/:showtimeId/seats" element={<SeatSelectionPage />} />
      <Route path="/booking/:bookingId/combos" element={<div style={{ padding: 60, textAlign: "center" }}>Bước tiếp: chọn combo (đang phát triển)</div>} />
      <Route path="*" element={<div style={{ padding: 60, textAlign: "center" }}>404</div>} />
    </Routes>
  );
}
