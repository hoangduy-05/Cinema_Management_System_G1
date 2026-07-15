import Seat from "./Seat";
import "./SeatMap.css";

export default function SeatMap({ seatMap, selectedIds, onToggle }) {
  const { totalRows, totalCols } = seatMap.layout;

  if (!seatMap.seats.length) {
    return <p style={{ textAlign: "center", padding: 30, color: "#999" }}>
      Suất chiếu này chưa có dữ liệu ghế (showtime_seat rỗng).
    </p>;
  }

  return (
    <div className="seatmap-wrap">
      <div className="screen">
        <div className="screen-arc" />
        <span className="screen-label">SCREEN</span>
      </div>
      <div className="seat-scroll">
        <div
          className="seat-grid"
          style={{
            gridTemplateColumns: `repeat(${totalCols}, 24px)`,
            gridTemplateRows: `repeat(${totalRows}, 24px)`,
          }}
        >
          {seatMap.seats.map((s) => (
            <Seat
              key={s.showtimeSeatId}
              seat={s}
              isSelected={selectedIds.has(s.showtimeSeatId)}
              onToggle={onToggle}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
