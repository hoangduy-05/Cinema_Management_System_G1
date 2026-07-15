import { styleOfType, UNAVAILABLE_BG, SELECTED_BG } from "../constants/seatStyles";

export default function Seat({ seat, isSelected, onToggle }) {
  const clickable = seat.selectable;
  const st = styleOfType(seat.seatType);

  let bg = st.fill || "transparent";
  if (!clickable) bg = UNAVAILABLE_BG;
  if (isSelected) bg = SELECTED_BG;
  const filled = bg !== "transparent";

  return (
    <button
      type="button"
      disabled={!clickable}
      onClick={() => onToggle(seat)}
      title={`${seat.label} · ${st.label} · ${seat.price.toLocaleString("vi-VN")}đ`}
      style={{
        gridColumnStart: seat.gridCol,
        gridColumnEnd: `span ${seat.colSpan}`,
        gridRowStart: seat.gridRow,
        gridRowEnd: `span ${seat.rowSpan}`,
        height: 22,
        fontSize: 9,
        lineHeight: 1,
        borderRadius: 2,
        border: `1px solid ${st.border}`,
        background: bg,
        color: filled ? "#fff" : "#555",
        cursor: clickable ? "pointer" : "not-allowed",
        padding: 0,
      }}
    >
      {seat.label}
    </button>
  );
}
