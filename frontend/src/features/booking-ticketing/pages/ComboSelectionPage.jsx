import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { bookingApi } from "@/api/services/bookingApi";
import { concessionApi } from "@/api/services/concessionApi";
import MainLayout from "@/layouts/MainLayout";
import { formatCurrency } from "@/utils/formatCurrency";
import { formatDateTime } from "@/utils/formatDate";
import "./ComboSelectionPage.css";

const getRemainingSeconds = (expiresAt) => {
  if (!expiresAt) return 0;
  return Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
};

export default function ComboSelectionPage() {
  const { bookingId } = useParams();
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [combos, setCombos] = useState([]);
  const [quantities, setQuantities] = useState({});
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    Promise.all([
      bookingApi.getSummary(bookingId),
      concessionApi.getCombos(),
    ])
      .then(([bookingSummary, comboCatalog]) => {
        if (!active) return;
        setSummary(bookingSummary);
        setCombos(comboCatalog || []);
        setQuantities(
          Object.fromEntries(
            (bookingSummary.selectedCombos || []).map((combo) => [
              combo.comboId,
              combo.quantity,
            ])
          )
        );
        setRemainingSeconds(getRemainingSeconds(bookingSummary.holdExpiresAt));
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || "Không tải được danh sách combo.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [bookingId]);

  useEffect(() => {
    if (!summary?.holdExpiresAt) return undefined;

    const timer = window.setInterval(() => {
      setRemainingSeconds(getRemainingSeconds(summary.holdExpiresAt));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [summary?.holdExpiresAt]);

  const comboSubtotal = useMemo(
    () =>
      combos.reduce(
        (total, combo) => total + Number(combo.price) * (quantities[combo.comboId] || 0),
        0
      ),
    [combos, quantities]
  );

  const totalAmount = Math.max(
    0,
    Number(summary?.seatSubtotal || 0) +
      comboSubtotal -
      Number(summary?.discountAmount || 0)
  );

  const canUpdate = summary?.allowedActions?.includes("UPDATE_COMBOS") && remainingSeconds > 0;

  const changeQuantity = (comboId, change) => {
    if (!canUpdate) return;
    setQuantities((current) => ({
      ...current,
      [comboId]: Math.min(10, Math.max(0, (current[comboId] || 0) + change)),
    }));
  };

  const handleNext = async () => {
    if (!canUpdate) return;
    setSaving(true);
    setError("");

    try {
      const items = Object.entries(quantities)
        .filter(([, quantity]) => quantity > 0)
        .map(([comboId, quantity]) => ({ comboId: Number(comboId), quantity }));
      const booking = await bookingApi.updateCombos(bookingId, items);
      navigate(`/booking/${bookingId}/payment`, { state: { booking } });
    } catch (requestError) {
      setError(requestError.message || "Không thể cập nhật combo.");
    } finally {
      setSaving(false);
    }
  };

  const minutes = String(Math.floor(remainingSeconds / 60)).padStart(2, "0");
  const seconds = String(remainingSeconds % 60).padStart(2, "0");

  return (
    <MainLayout>
      <div className="combo-page">
        <section className="combo-booking">
          <h1 className="combo-booking-title">BOOKING ONLINE</h1>

          {loading && <p className="combo-message">Đang tải danh sách combo...</p>}
          {!loading && error && !summary && <p className="combo-message combo-error">{error}</p>}

          {!loading && summary && (
            <>
              <div className="combo-booking-meta">
                <div>
                  <strong>
                    {summary.branch.branchName} | {summary.room.roomName} | Số ghế ({summary.selectedSeats.length})
                  </strong>
                  <span>
                    {formatDateTime(summary.showtime.startTime)} ~ {formatDateTime(summary.showtime.endTime)}
                  </span>
                </div>
                <div className="combo-countdown">
                  <span className="combo-countdown-label">Countdown Clock</span>
                  <div className="combo-countdown-boxes">
                    <div><b>{minutes}</b><span>Minutes</span></div>
                    <div><b>{seconds}</b><span>Seconds</span></div>
                  </div>
                </div>
              </div>

              <h2 className="combo-section-title">Bắp Nước</h2>

              {combos.length ? (
                <ol className="combo-products-list">
                  {combos.map((combo) => {
                    const quantity = quantities[combo.comboId] || 0;
                    return (
                      <li className="combo-product" key={combo.comboId}>
                        <div className="combo-image-wrap">
                          {combo.imageUrl ? (
                            <img src={combo.imageUrl} alt={combo.name} className="combo-product-image" />
                          ) : (
                            <div className="combo-image-placeholder">CGV COMBO</div>
                          )}
                        </div>
                        <div className="combo-product-shop">
                          <h3>{combo.name}</h3>
                          <p>{combo.description || "Combo bắp nước tại rạp."}</p>
                          <div className="combo-product-bottom">
                            <div className="combo-price">
                              <span>Giá:</span>
                              <strong>{formatCurrency(combo.price)}</strong>
                            </div>
                            <div className="combo-quantity" aria-label={`Số lượng ${combo.name}`}>
                              <button
                                type="button"
                                onClick={() => changeQuantity(combo.comboId, -1)}
                                disabled={!canUpdate || quantity === 0}
                                aria-label={`Giảm ${combo.name}`}
                              >
                                −
                              </button>
                              <output>{quantity}</output>
                              <button
                                type="button"
                                onClick={() => changeQuantity(combo.comboId, 1)}
                                disabled={!canUpdate || quantity === 10}
                                aria-label={`Tăng ${combo.name}`}
                              >
                                +
                              </button>
                            </div>
                          </div>
                        </div>
                      </li>
                    );
                  })}
                </ol>
              ) : (
                <p className="combo-message">Hiện chưa có combo đang bán.</p>
              )}

              {error && <p className="combo-inline-error">{error}</p>}
              {!canUpdate && (
                <p className="combo-inline-error">Thời gian giữ ghế đã hết hoặc booking không thể cập nhật combo.</p>
              )}

              <div className="combo-film-strip">
                <button type="button" className="combo-nav-btn" onClick={() => navigate(-1)}>
                  <span>‹</span> PREVIOUS
                </button>

                <div className="combo-film-info">
                  {summary.movie.posterUrl && (
                    <img className="combo-film-poster" src={summary.movie.posterUrl} alt={summary.movie.title} />
                  )}
                  <div className="combo-film-col combo-film-name">
                    <strong>{summary.movie.title}</strong>
                    <span>{summary.room.roomType}</span>
                  </div>
                  <div className="combo-film-col">
                    <span className="combo-muted">Rạp</span>
                    <strong>{summary.branch.branchName}</strong>
                    <span className="combo-muted">Suất chiếu</span>
                    <strong>{formatDateTime(summary.showtime.startTime)}</strong>
                    <span className="combo-muted">Phòng chiếu</span>
                    <strong>{summary.room.roomName}</strong>
                  </div>
                  <div className="combo-film-col combo-totals">
                    <div><span>Ghế</span><strong>{summary.selectedSeats.map((seat) => seat.seatLabel).join(", ")}</strong></div>
                    <div><span>Combo</span><strong>{formatCurrency(comboSubtotal)}</strong></div>
                    <div><span>Tổng</span><strong>{formatCurrency(totalAmount)}</strong></div>
                  </div>
                </div>

                <button
                  type="button"
                  className="combo-nav-btn combo-next"
                  onClick={handleNext}
                  disabled={!canUpdate || saving}
                >
                  {saving ? "..." : "NEXT"} <span>›</span>
                </button>
              </div>
            </>
          )}
        </section>
      </div>
    </MainLayout>
  );
}
