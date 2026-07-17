import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { bookingApi } from "@/api/services/bookingApi";
import { paymentApi } from "@/api/services/paymentApi";
import MainLayout from "@/layouts/MainLayout";
import { formatCurrency } from "@/utils/formatCurrency";
import { formatDateTime } from "@/utils/formatDate";
import atmCard from "@/assets/images/atm-card.png";
import visa from "@/assets/images/visa.png";
import momo from "@/assets/images/momo.png";
import zalopay from "@/assets/images/zalopay.png";
import vnpay from "@/assets/images/vnpay.png";
import shopeepay from "@/assets/images/shopeepay.svg";
import "./PaymentPage.css";

const paymentMethods = [
  { id: "ATM", label: "Thẻ ATM (Thẻ nội địa)", image: atmCard },
  { id: "VISA", label: "Thẻ quốc tế (Visa, Master, Amex, JCB)", image: visa },
  { id: "MOMO", label: "Ví điện tử MoMo", image: momo },
  { id: "ZALOPAY", label: "Ví điện tử ZaloPay", image: zalopay },
  { id: "VNPAY", label: "Cổng thanh toán VNPAY", image: vnpay, wide: true },
  { id: "SHOPEEPAY", label: "Ví điện tử ShopeePay", image: shopeepay },
];

const terminalStatuses = ["CONFIRMED", "COMPLETED", "CANCELLED", "EXPIRED"];

const getRemainingSeconds = (expiresAt) => {
  if (!expiresAt) return 0;
  return Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
};

export default function PaymentPage() {
  const { bookingId } = useParams();
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [payment, setPayment] = useState(null);
  const [paymentMethod, setPaymentMethod] = useState("VNPAY");
  const [voucherCode, setVoucherCode] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [voucherProcessing, setVoucherProcessing] = useState(false);
  const [error, setError] = useState("");

  const loadSummary = useCallback(async () => {
    const bookingSummary = await bookingApi.getSummary(bookingId);
    setSummary(bookingSummary);
    const deadline = bookingSummary.status === "PENDING_PAYMENT"
      ? bookingSummary.paymentExpiresAt
      : bookingSummary.holdExpiresAt;
    setRemainingSeconds(getRemainingSeconds(deadline));
    return bookingSummary;
  }, [bookingId]);

  useEffect(() => {
    let active = true;

    loadSummary()
      .then(async (bookingSummary) => {
        if (!active || bookingSummary.status !== "PENDING_PAYMENT") return;
        try {
          const activePayment = await paymentApi.getLatest(bookingId);
          if (active) {
            setPayment(activePayment);
            setPaymentMethod(activePayment.paymentMethod || "VNPAY");
            setRemainingSeconds(getRemainingSeconds(activePayment.paymentExpiresAt));
          }
        } catch {
          if (active) setPayment(null);
        }
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || "Không tải được thông tin thanh toán.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [bookingId, loadSummary]);

  useEffect(() => {
    const deadline = summary?.status === "PENDING_PAYMENT"
      ? summary?.paymentExpiresAt
      : summary?.holdExpiresAt;
    if (!deadline || terminalStatuses.includes(summary?.status)) return undefined;

    const timer = window.setInterval(() => {
      const nextRemaining = getRemainingSeconds(deadline);
      setRemainingSeconds(nextRemaining);
      if (nextRemaining === 0) {
        window.clearInterval(timer);
        loadSummary().catch(() => undefined);
      }
    }, 1000);

    return () => window.clearInterval(timer);
  }, [loadSummary, summary?.holdExpiresAt, summary?.paymentExpiresAt, summary?.status]);

  useEffect(() => {
    if (summary?.status !== "PENDING_PAYMENT") return undefined;

    const poller = window.setInterval(async () => {
      try {
        const bookingSummary = await loadSummary();
        if (bookingSummary.status === "PENDING_PAYMENT") {
          setPayment(await paymentApi.getLatest(bookingId));
        }
      } catch {
        return;
      }
    }, 5000);

    return () => window.clearInterval(poller);
  }, [bookingId, loadSummary, summary?.status]);

  const canEditVoucher = summary?.allowedActions?.includes("APPLY_VOUCHER") && remainingSeconds > 0;
  const canCheckout = summary?.allowedActions?.includes("CHECKOUT") && remainingSeconds > 0;
  const canRetryPayment = summary?.allowedActions?.includes("RETRY_PAYMENT")
    && payment?.paymentStatus === "FAILED"
    && remainingSeconds > 0;
  const isSuccessful = summary?.status === "CONFIRMED" || summary?.status === "COMPLETED";
  const isTerminalFailure = summary?.status === "CANCELLED" || summary?.status === "EXPIRED";

  const selectedComboText = useMemo(
    () => (summary?.selectedCombos || [])
      .map((combo) => `${combo.comboName} x${combo.quantity}`)
      .join(", ") || "Không có",
    [summary?.selectedCombos]
  );

  const applyVoucher = async () => {
    const code = voucherCode.trim();
    if (!code || !canEditVoucher) return;
    setVoucherProcessing(true);
    setError("");
    try {
      await bookingApi.applyVoucher(bookingId, code);
      await loadSummary();
      setVoucherCode("");
    } catch (requestError) {
      setError(requestError.message || "Không thể áp dụng mã giảm giá.");
    } finally {
      setVoucherProcessing(false);
    }
  };

  const removeVoucher = async () => {
    if (!summary?.allowedActions?.includes("REMOVE_VOUCHER")) return;
    setVoucherProcessing(true);
    setError("");
    try {
      await bookingApi.removeVoucher(bookingId);
      await loadSummary();
    } catch (requestError) {
      setError(requestError.message || "Không thể gỡ mã giảm giá.");
    } finally {
      setVoucherProcessing(false);
    }
  };

  const handlePayment = async () => {
    if (!accepted || processing || !canCheckout) return;
    setProcessing(true);
    setError("");

    try {
      const activePayment = await bookingApi.checkout(bookingId, paymentMethod);
      setPayment(activePayment);
      setRemainingSeconds(getRemainingSeconds(activePayment.paymentExpiresAt));
      await paymentApi.confirmBrowser(activePayment.paymentId);
      await loadSummary();
    } catch (requestError) {
      setError(requestError.message || "Thanh toán không thành công. Vui lòng thử lại.");
      try {
        await loadSummary();
      } catch {
        return;
      }
    } finally {
      setProcessing(false);
    }
  };

  const handleRetry = async () => {
    if (!accepted || processing || remainingSeconds <= 0) return;
    setProcessing(true);
    setError("");
    try {
      const activePayment = await bookingApi.retryPayment(bookingId, paymentMethod);
      setPayment(activePayment);
      setRemainingSeconds(getRemainingSeconds(activePayment.paymentExpiresAt));
      await paymentApi.confirmBrowser(activePayment.paymentId);
      await loadSummary();
    } catch (requestError) {
      setError(requestError.message || "Không thể thử lại thanh toán.");
    } finally {
      setProcessing(false);
    }
  };

  const minutes = String(Math.floor(remainingSeconds / 60)).padStart(2, "0");
  const seconds = String(remainingSeconds % 60).padStart(2, "0");

  return (
    <MainLayout>
      <div className="payment-page">
        <section className="payment-booking">
          <h1 className="payment-title">THANH TOÁN</h1>

          {loading && <p className="payment-message">Đang tải thông tin thanh toán...</p>}
          {!loading && error && !summary && <p className="payment-message payment-error">{error}</p>}

          {!loading && summary && (
            <>
              {isSuccessful && (
                <div className="payment-result payment-result-success">
                  <span className="payment-result-icon">✓</span>
                  <div>
                    <h2>Thanh toán thành công</h2>
                    <p>Mã đặt vé <strong>{summary.bookingCode}</strong> đã được xác nhận. Vé của bạn đã sẵn sàng.</p>
                  </div>
                </div>
              )}

              {isTerminalFailure && (
                <div className="payment-result payment-result-failed">
                  <span className="payment-result-icon">!</span>
                  <div>
                    <h2>{summary.status === "EXPIRED" ? "Đơn đặt vé đã hết hạn" : "Đơn đặt vé đã hủy"}</h2>
                    <p>Ghế đã được trả lại hệ thống. Vui lòng chọn một suất chiếu mới.</p>
                  </div>
                </div>
              )}

              {!isSuccessful && !isTerminalFailure && (
                <div className="payment-layout">
                  <div className="payment-main">
                    <section className="payment-panel">
                      <h2><em>Bước 1:</em> GIẢM GIÁ</h2>
                      <div className="payment-voucher-row">
                        <div>
                          <strong>Mã khuyến mãi</strong>
                          <span>Nhập mã ưu đãi của F-Cinema</span>
                        </div>
                        <div className="payment-voucher-form">
                          <input
                            value={voucherCode}
                            onChange={(event) => setVoucherCode(event.target.value.toUpperCase())}
                            placeholder="Nhập mã giảm giá"
                            disabled={!canEditVoucher || voucherProcessing}
                            onKeyDown={(event) => {
                              if (event.key === "Enter") applyVoucher();
                            }}
                          />
                          <button type="button" onClick={applyVoucher} disabled={!voucherCode.trim() || !canEditVoucher || voucherProcessing}>
                            ÁP DỤNG
                          </button>
                        </div>
                      </div>
                      <div className="payment-applied-voucher">
                        <span>Voucher đang áp dụng</span>
                        {summary.appliedVoucher ? (
                          <div>
                            <strong>{summary.appliedVoucher.voucherCode}</strong>
                            <button
                              type="button"
                              onClick={removeVoucher}
                              disabled={voucherProcessing || !summary.allowedActions?.includes("REMOVE_VOUCHER")}
                            >
                              Gỡ
                            </button>
                          </div>
                        ) : <strong>Chưa có</strong>}
                      </div>
                    </section>

                    <section className="payment-panel">
                      <h2><em>Bước 2:</em> HÌNH THỨC THANH TOÁN</h2>
                      <div className="payment-methods">
                        {paymentMethods.map((method) => (
                          <label className={`payment-method ${paymentMethod === method.id ? "selected" : ""}`} key={method.id}>
                            <input
                              type="radio"
                              name="paymentMethod"
                              value={method.id}
                              checked={paymentMethod === method.id}
                              onChange={() => setPaymentMethod(method.id)}
                              disabled={summary.status === "PENDING_PAYMENT"}
                            />
                            <span className="payment-radio" />
                            <span className="payment-logo-wrap">
                              <img className={method.wide ? "wide" : ""} src={method.image} alt="" />
                            </span>
                            <strong>{method.label}</strong>
                          </label>
                        ))}
                      </div>
                    </section>

                    <label className="payment-terms">
                      <input type="checkbox" checked={accepted} onChange={(event) => setAccepted(event.target.checked)} />
                      <span>Tôi đồng ý với điều khoản sử dụng, chính sách thanh toán và mua vé cho người có độ tuổi phù hợp.</span>
                    </label>

                    {summary.status === "PENDING_PAYMENT" && (
                      <p className="payment-pending-note" role="status">
                        Giao dịch đang chờ cổng thanh toán xác nhận. Trang sẽ tự cập nhật khi thanh toán hoàn tất.
                      </p>
                    )}
                    {error && <p className="payment-inline-error" role="alert">{error}</p>}
                  </div>

                  <aside className="payment-sidebar">
                    <div className="payment-summary-card">
                      <h2>Tổng cộng</h2>
                      <div><span>Ghế</span><strong>{formatCurrency(summary.seatSubtotal)}</strong></div>
                      <div><span>Combo</span><strong>{formatCurrency(summary.comboSubtotal)}</strong></div>
                      <div><span>Khuyến mãi</span><strong>-{formatCurrency(summary.discountAmount)}</strong></div>
                    </div>
                    <div className="payment-total-card">
                      <h2>Tổng số tiền thanh toán</h2>
                      <strong>{formatCurrency(summary.totalAmount)}</strong>
                    </div>
                    <div className="payment-clock">
                      <h2>Countdown Clock</h2>
                      <div>
                        <span><b>{minutes}</b><small>Minutes</small></span>
                        <span><b>{seconds}</b><small>Seconds</small></span>
                      </div>
                    </div>
                    {payment?.transactionReference && (
                      <div className="payment-reference">
                        <span>Mã giao dịch</span>
                        <strong>{payment.transactionReference}</strong>
                      </div>
                    )}
                  </aside>
                </div>
              )}

              <div className="payment-film-strip">
                <button type="button" className="payment-nav-btn" onClick={() => navigate(summary.status === "SEAT_HELD" ? `/booking/${bookingId}/combos` : "/")}>
                  <span>‹</span>{summary.status === "SEAT_HELD" ? "PREVIOUS" : "TRANG CHỦ"}
                </button>

                <div className="payment-film-info">
                  {summary.movie.posterUrl && <img src={summary.movie.posterUrl} alt={summary.movie.title} />}
                  <div className="payment-film-col payment-film-name">
                    <strong>{summary.movie.title}</strong>
                    <span>{summary.room.roomType} · {summary.movie.ageRating}</span>
                  </div>
                  <div className="payment-film-col">
                    <span className="payment-muted">Rạp</span>
                    <strong>{summary.branch.branchName}</strong>
                    <span className="payment-muted">Suất chiếu</span>
                    <strong>{formatDateTime(summary.showtime.startTime)}</strong>
                    <span className="payment-muted">Phòng chiếu</span>
                    <strong>{summary.room.roomName}</strong>
                  </div>
                  <div className="payment-film-col payment-film-detail">
                    <div><span>Ghế</span><strong>{summary.selectedSeats.map((seat) => seat.seatLabel).join(", ")}</strong></div>
                    <div><span>Combo</span><strong>{selectedComboText}</strong></div>
                    <div><span>Tổng</span><strong>{formatCurrency(summary.totalAmount)}</strong></div>
                  </div>
                </div>

                {!isSuccessful && !isTerminalFailure ? (
                  <button
                    type="button"
                    className="payment-nav-btn payment-pay-btn"
                    onClick={canRetryPayment ? handleRetry : handlePayment}
                    disabled={!accepted || processing || remainingSeconds <= 0 || (!canCheckout && !canRetryPayment)}
                  >
                    <span className="payment-card-icon">▣</span>
                    {processing ? "ĐANG XỬ LÝ" : canRetryPayment ? "RETRY" : summary.status === "PENDING_PAYMENT" ? "PENDING" : "PAYMENT"}
                  </button>
                ) : (
                  <button type="button" className="payment-nav-btn payment-pay-btn" onClick={() => navigate("/")}>
                    <span>⌂</span>TRANG CHỦ
                  </button>
                )}
              </div>
            </>
          )}
        </section>
      </div>
    </MainLayout>
  );
}
