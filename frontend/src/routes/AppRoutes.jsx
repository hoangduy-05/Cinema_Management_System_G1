import { Routes, Route } from "react-router-dom";
import HomePage from "@/pages/customer/HomePage";
import LoginPage from "@/features/auth/pages/LoginPage";
import SeatSelectionPage from "@/features/booking-ticketing/pages/SeatSelectionPage";
import ComboSelectionPage from "@/features/booking-ticketing/pages/ComboSelectionPage";
import PaymentPage from "@/features/payment-promotion/pages/PaymentPage";
import MyTicketsPage from "@/features/booking-ticketing/pages/MyTicketsPage";
import ProtectedRoute from "@/security/ProtectedRoute";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/my-tickets"
        element={<ProtectedRoute><MyTicketsPage /></ProtectedRoute>}
      />
      <Route path="/booking/:showtimeId/seats" element={<SeatSelectionPage />} />
      <Route
        path="/booking/:bookingId/combos"
        element={<ProtectedRoute><ComboSelectionPage /></ProtectedRoute>}
      />
      <Route
        path="/booking/:bookingId/payment"
        element={<ProtectedRoute><PaymentPage /></ProtectedRoute>}
      />
      <Route path="*" element={<div style={{ padding: 60, textAlign: "center" }}>404</div>} />
    </Routes>
  );
}
