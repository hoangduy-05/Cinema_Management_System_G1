import { styleOfType, UNAVAILABLE_BG, SELECTED_BG } from "../constants/seatStyles";

const box = { width: 20, height: 20, borderRadius: 2, display: "inline-block" };
const row = { display: "flex", gap: 8, alignItems: "center", fontSize: 14 };

export default function SeatLegend({ seatTypes = [] }) {
  return (
    <div style={{ display: "flex", gap: 60, justifyContent: "center", margin: "24px 0", flexWrap: "wrap" }}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <span style={row}><span style={{ ...box, background: SELECTED_BG }} /> Ghế bạn chọn</span>
        <span style={row}><span style={{ ...box, background: UNAVAILABLE_BG }} /> Không thể chọn</span>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {seatTypes.map((t) => {
          const st = styleOfType(t);
          return (
            <span key={t} style={row}>
              <span style={{ ...box, border: `2px solid ${st.border}`, background: st.fill || "transparent" }} />
              {st.label}
            </span>
          );
        })}
      </div>
    </div>
  );
}
