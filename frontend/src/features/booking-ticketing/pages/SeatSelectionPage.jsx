import { useState } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { useSelector } from "react-redux";
import { useSeatMap } from "../hooks/useSeatMap";
import { useSeatSelection } from "../hooks/useSeatSelection";
import SeatMap from "../components/SeatMap";
import SeatLegend from "../components/SeatLegend";
import { bookingApi } from "@/api/services/bookingApi";
import { formatCurrency } from "@/utils/formatCurrency";
import { formatDateTime } from "@/utils/formatDate";
import MainLayout from "@/layouts/MainLayout";
import "./SeatSelectionPage.css";

export default function SeatSelectionPage() {
  const { showtimeId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const moviePoster = location.state?.moviePoster;
  const token = useSelector((s) => s.auth.token);
  const { seatMap, loading, error, reload } = useSeatMap(showtimeId);
  const { selected, selectedIds, toggle, total, clear } = useSeatSelection();
  const [holding, setHolding] = useState(false);

  const inner = () => {
    if (loading) return <p className="center-msg">Đang tải sơ đồ ghế…</p>;
    if (error) return <p className="center-msg">Lỗi: {error}</p>;
    if (!seatMap) return null;

    const handleNext = async () => {
      if (!selected.length) return alert("Vui lòng chọn ít nhất 1 ghế.");
      if (!token) {
        alert("Bạn cần đăng nhập để giữ ghế.");
        return navigate("/login", { state: { from: `/booking/${showtimeId}/seats` } });
      }
      setHolding(true);
      try {
        const booking = await bookingApi.holdSeats(
          Number(showtimeId),
          selected.map((s) => s.showtimeSeatId)
        );
        navigate(`/booking/${booking.bookingId}/combos`, { state: { booking } });
      } catch (e) {
        alert(e.message || "Không giữ được ghế. Có thể ai đó vừa chọn trước.");
        clear();
        reload();
      } finally {
        setHolding(false);
      }
    };

    return (
      <div className="booking-page">
        <div className="booking-frame">
          <div className="booking-title">BOOKING ONLINE</div>
          <div className="booking-sub">
            <b>{seatMap.branch.branchName} | {seatMap.room.roomName} | Số ghế </b>
            <i>({seatMap.availableSeats}/{seatMap.totalSeats})</i>
            <br />
            {formatDateTime(seatMap.startTime)} ~ {formatDateTime(seatMap.endTime)}
          </div>

          <div className="booking-bar">Người / Ghế</div>

          <SeatMap seatMap={seatMap} selectedIds={selectedIds} onToggle={toggle} />
          <SeatLegend seatTypes={seatMap.seatTypes} />

          <div className="film-strip">
            <button className="nav-btn prev" onClick={() => navigate(-1)}>
              <span>‹</span> PREVIOUS
            </button>

            <div className="film-info">
              {moviePoster && (
                <img className="film-poster" src={moviePoster} alt={seatMap.movie.title} />
              )}
              <div className="film-col film-name">
                <div className="film-title">{seatMap.movie.title}</div>
                <div>{seatMap.room.roomType}</div>
              </div>
              <div className="film-col">
                <span className="muted">Rạp</span>
                <b>{seatMap.branch.branchName}</b>
                <span className="muted">Suất chiếu</span>
                <b>{formatDateTime(seatMap.startTime)}</b>
                <span className="muted">Phòng chiếu</span>
                <b>{seatMap.room.roomName}</b>
              </div>
              <div className="film-col">
                <div><span className="muted">Ghế</span> <b>{selected.length}</b></div>
                <div><span className="muted">Combo</span> <b>0 đ</b></div>
                <div><span className="muted">Tổng</span> <b>{formatCurrency(total)}</b></div>
              </div>
            </div>

            <button className="nav-btn next" onClick={handleNext} disabled={holding}>
              {holding ? "..." : "NEXT"} <span>›</span>
            </button>
          </div>

          <div className="selected-line">
            Ghế đã chọn: {selected.map((s) => s.label).join(", ") || "—"}
            {!token && <em style={{ color: "#c62828", marginLeft: 10 }}>(cần đăng nhập để giữ ghế)</em>}
          </div>
        </div>
      </div>
    );
  };

  return <MainLayout>{inner()}</MainLayout>;
}
