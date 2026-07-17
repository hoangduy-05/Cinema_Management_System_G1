import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { bookingApi } from "@/api/services/bookingApi";
import MainLayout from "@/layouts/MainLayout";
import { formatCurrency } from "@/utils/formatCurrency";
import { formatDateTime } from "@/utils/formatDate";
import "./MyTicketsPage.css";

const filters = [
  { label: "TẤT CẢ", value: "" },
  { label: "CHỜ THANH TOÁN", value: "PENDING_PAYMENT" },
  { label: "ĐÃ XÁC NHẬN", value: "CONFIRMED" },
  { label: "HOÀN TẤT", value: "COMPLETED" },
  { label: "ĐÃ HỦY", value: "CANCELLED" },
  { label: "HẾT HẠN", value: "EXPIRED" },
];

const bookingLabels = {
  CREATED: "Đang tạo",
  SEAT_HELD: "Đang giữ ghế",
  PENDING_PAYMENT: "Chờ thanh toán",
  CONFIRMED: "Đã xác nhận",
  COMPLETED: "Hoàn tất",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn",
};

const ticketLabels = {
  HELD: "Đang giữ",
  VALID: "Có hiệu lực",
  USED: "Đã sử dụng",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn",
};

export default function MyTicketsPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [history, setHistory] = useState(null);
  const [expandedId, setExpandedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    bookingApi.getMyHistory({ page, size: pageSize, status })
      .then((data) => {
        if (active) setHistory(data);
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || "Không tải được lịch sử mua vé.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, pageSize, status]);

  const changeFilter = (value) => {
    setStatus(value);
    setPage(0);
    setExpandedId(null);
  };

  return (
    <MainLayout>
      <div className="tickets-page">
        <div className="tickets-shell">
          <aside className="tickets-account-menu">
            <h1>TÀI KHOẢN F-CINEMA</h1>
            {["THÔNG TIN CHUNG", "CHI TIẾT TÀI KHOẢN", "THẺ THÀNH VIÊN", "ĐIỂM THƯỞNG", "VOUCHER", "COUPON"].map((item) => (
              <div key={item}>{item}</div>
            ))}
            <div className="active">LỊCH SỬ GIAO DỊCH</div>
          </aside>

          <main className="tickets-history">
            <h1>LỊCH SỬ MUA VÉ</h1>
            <div className="tickets-toolbar">
              <div className="tickets-filters">
                {filters.map((filter) => (
                  <button
                    key={filter.label}
                    type="button"
                    className={status === filter.value ? "active" : ""}
                    onClick={() => changeFilter(filter.value)}
                  >
                    {filter.label}
                  </button>
                ))}
              </div>
              <div className="tickets-page-size">
                <strong>{history?.totalElements || 0} sản phẩm</strong>
                <label>
                  HIỂN THỊ:
                  <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(0); }}>
                    <option value={5}>5</option>
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                  </select>
                </label>
              </div>
            </div>

            {loading && <p className="tickets-message">Đang tải lịch sử mua vé...</p>}
            {!loading && error && <p className="tickets-message tickets-error" role="alert">{error}</p>}
            {!loading && !error && !history?.content?.length && (
              <p className="tickets-message">Bạn chưa có vé nào trong trạng thái này.</p>
            )}

            {!loading && !error && history?.content?.map((booking) => (
              <article className="ticket-order" key={booking.bookingId}>
                <header>
                  <strong>Mã Đặt Vé: {booking.bookingCode}</strong>
                  <span className={`booking-status status-${booking.bookingStatus.toLowerCase()}`}>
                    {bookingLabels[booking.bookingStatus] || booking.bookingStatus}
                  </span>
                </header>
                <div className="ticket-order-main">
                  <div className="ticket-poster-wrap">
                    {booking.movie.posterUrl ? (
                      <img src={booking.movie.posterUrl} alt={booking.movie.title} />
                    ) : (
                      <div>F-CINEMA</div>
                    )}
                  </div>
                  <div className="ticket-order-info">
                    <h2>{booking.movie.title}</h2>
                    <p><b>Phân loại:</b> {booking.movie.ageRating || "--"}</p>
                    <p><b>Suất chiếu:</b> {formatDateTime(booking.showtime.startTime)} ~ {formatDateTime(booking.showtime.endTime)}</p>
                    <p><b>Rạp:</b> {booking.branch.branchName}</p>
                    <p><b>Phòng:</b> {booking.room.roomName} · {booking.room.roomType}</p>
                    <p><b>Ghế:</b> {booking.seatLabels.join(", ") || "--"}</p>
                    <p><b>Thanh toán:</b> {booking.payment?.status || "CHƯA CÓ"}{booking.payment?.method ? ` · ${booking.payment.method}` : ""}</p>
                    <strong className="ticket-total">{formatCurrency(booking.totalAmount)}</strong>
                    <div className="ticket-actions">
                      <button type="button" onClick={() => setExpandedId(expandedId === booking.bookingId ? null : booking.bookingId)}>
                        {expandedId === booking.bookingId ? "Thu gọn" : "Xem vé"}
                      </button>
                      {(booking.bookingStatus === "SEAT_HELD" || booking.bookingStatus === "PENDING_PAYMENT") && (
                        <button type="button" className="continue" onClick={() => navigate(`/booking/${booking.bookingId}/payment`)}>
                          Tiếp tục thanh toán
                        </button>
                      )}
                    </div>
                  </div>
                </div>

                {expandedId === booking.bookingId && (
                  <div className="ticket-details">
                    <h3>Chi tiết vé</h3>
                    {booking.tickets.length ? booking.tickets.map((ticket) => (
                      <div className="ticket-detail-row" key={ticket.ticketId}>
                        <div>
                          <span>Mã vé</span>
                          <strong>{ticket.ticketCode}</strong>
                        </div>
                        <div>
                          <span>Ghế</span>
                          <strong>{ticket.seatLabel}</strong>
                        </div>
                        <div>
                          <span>Giá vé</span>
                          <strong>{formatCurrency(ticket.price)}</strong>
                        </div>
                        <div>
                          <span>Trạng thái vé</span>
                          <strong className={`ticket-status ticket-${ticket.status.toLowerCase()}`}>
                            {ticketLabels[ticket.status] || ticket.status}
                          </strong>
                        </div>
                        {ticket.checkedInAt && (
                          <div>
                            <span>Check-in</span>
                            <strong>{formatDateTime(ticket.checkedInAt)}</strong>
                          </div>
                        )}
                      </div>
                    )) : <p>Booking chưa phát hành vé.</p>}
                  </div>
                )}
              </article>
            ))}

            {history && history.totalPages > 1 && (
              <div className="tickets-pagination">
                <button type="button" onClick={() => setPage((value) => Math.max(0, value - 1))} disabled={history.first}>‹ Trước</button>
                <span>Trang {history.page + 1} / {history.totalPages}</span>
                <button type="button" onClick={() => setPage((value) => value + 1)} disabled={history.last}>Sau ›</button>
              </div>
            )}

            <button type="button" className="tickets-back" onClick={() => navigate("/")}>« Quay lại</button>
          </main>
        </div>
      </div>
    </MainLayout>
  );
}
