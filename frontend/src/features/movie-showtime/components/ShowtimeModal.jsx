import { useEffect, useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { showtimeApi } from "@/api/services/showtimeApi";
import "./ShowtimeModal.css";

const WD = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function buildDates(n = 30) {
  const out = [];
  const base = new Date("2026-09-01"); // đổi về new Date() khi DB có ngày hiện tại
  const p = (x) => String(x).padStart(2, "0");
  for (let i = 0; i < n; i++) {
    const d = new Date(base);
    d.setDate(base.getDate() + i);
    out.push({
      iso: `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`,
      month: p(d.getMonth() + 1),
      day: p(d.getDate()),
      wd: WD[d.getDay()],
    });
  }
  return out;
}

function groupByFormat(showtimes) {
  const map = new Map();
  for (const st of showtimes) {
    const fmt = st.roomType || "2D";
    if (!map.has(fmt)) map.set(fmt, []);
    map.get(fmt).push(st);
  }
  return [...map.entries()].map(([format, times]) => ({
    format,
    times: times.sort((a, b) => a.startTime.localeCompare(b.startTime)),
  }));
}

export default function ShowtimeModal({ movieId, movieTitle, moviePoster, onClose }) {
  const navigate = useNavigate();
  const dates = useMemo(() => buildDates(), []);
  const [date, setDate] = useState(dates[0].iso);
  const [branches, setBranches] = useState([]);
  const [branchId, setBranchId] = useState(null); // null = tất cả rạp
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    showtimeApi
      .getShowtimesByMovie(movieId, date)
      .then((res) => {
        if (!alive) return;
        const list = Array.isArray(res) ? res : [];
        setBranches(list);
        // nếu rạp đang chọn không còn suất trong ngày mới -> quay về "Tất cả rạp"
        setBranchId((cur) =>
          cur && list.some((b) => b.branch.branchId === cur) ? cur : null
        );
      })
      .catch((err) => alive && setError(err.message))
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [movieId, date]);

  const pick = (showtimeId) => {
    onClose?.();
    navigate(`/booking/${showtimeId}/seats`, {
      state: { moviePoster, movieTitle }, // mang poster sang trang ghế
    });
  };
  const hhmm = (iso) => (iso ? iso.slice(11, 16) : "");

  // lọc theo rạp đang chọn
  const branchesFiltered = branchId
    ? branches.filter((b) => b.branch.branchId === branchId)
    : branches;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span>{movieTitle || "Lịch chiếu"}</span>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>

        {/* 1. dải ngày */}
        <div className="date-strip">
          {dates.map((d) => (
            <button
              key={d.iso}
              className={`date-cell ${d.iso === date ? "active" : ""}`}
              onClick={() => setDate(d.iso)}
            >
              <small>{d.month}</small>
              <small>{d.wd}</small>
              <span className="day">{d.day}</span>
            </button>
          ))}
        </div>

        {/* 2. tab chọn RẠP (thay cho chọn định dạng) */}
        {!loading && !error && branches.length > 0 && (
          <div className="city-tabs">
            <button
              className={`city-tab ${branchId === null ? "active" : ""}`}
              onClick={() => setBranchId(null)}
            >
              Tất cả rạp
            </button>
            {branches.map((b) => (
              <button
                key={b.branch.branchId}
                className={`city-tab ${branchId === b.branch.branchId ? "active" : ""}`}
                onClick={() => setBranchId(b.branch.branchId)}
              >
                {b.branch.branchName}
              </button>
            ))}
          </div>
        )}

        {error && <div className="modal-error"><b>Không tải được lịch chiếu.</b><div>{error}</div></div>}
        {loading && <p className="modal-msg">Đang tải lịch chiếu…</p>}
        {!loading && !error && branchesFiltered.length === 0 && (
          <p className="modal-msg">Không có suất chiếu phù hợp trong ngày này.</p>
        )}

        {/* 3. danh sách rạp, vẫn group theo định dạng bên trong mỗi rạp */}
        {!loading && !error &&
          branchesFiltered.map((b) => (
            <div className="cinema-block" key={b.branch.branchId}>
              <h3 className="cinema-name">{b.branch.branchName}</h3>
              {b.branch.address && <div className="cinema-addr">{b.branch.address}</div>}
              {groupByFormat(b.showtimes).map((grp) => (
                <div className="format-group" key={grp.format}>
                  <div className="format-title">Rạp {grp.format}</div>
                  <div className="time-list">
                    {grp.times.map((st) => {
                      const disabled = ["SOLD_OUT", "CANCELLED"].includes(st.status);
                      return (
                        <button
                          key={st.showtimeId}
                          className={`time-btn ${disabled ? "sold" : ""}`}
                          disabled={disabled}
                          onClick={() => pick(st.showtimeId)}
                          title={st.roomName}
                        >
                          {hhmm(st.startTime)}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          ))}
      </div>
    </div>
  );
}