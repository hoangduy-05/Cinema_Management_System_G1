import { useState, useCallback } from "react";
import { MAX_SEATS_PER_ORDER } from "@/config/constants";

export function useSeatSelection() {
  const [selected, setSelected] = useState([]);
  // BE nhận showtimeSeatIds -> key theo showtimeSeatId
  const selectedIds = new Set(selected.map((s) => s.showtimeSeatId));

  const toggle = useCallback((seat) => {
    setSelected((prev) => {
      if (prev.some((s) => s.showtimeSeatId === seat.showtimeSeatId)) {
        return prev.filter((s) => s.showtimeSeatId !== seat.showtimeSeatId);
      }
      // rule FE: chỉ chọn cùng loại ghế (BE vẫn phải validate lại)
      if (prev.length && prev[0].seatType !== seat.seatType) {
        alert("Chỉ được chọn các ghế cùng loại.");
        return prev;
      }
      if (prev.length >= MAX_SEATS_PER_ORDER) {
        alert(`Tối đa ${MAX_SEATS_PER_ORDER} ghế mỗi lần đặt.`);
        return prev;
      }
      return [...prev, seat];
    });
  }, []);

  const total = selected.reduce((s, x) => s + x.price, 0);
  return { selected, selectedIds, toggle, total, clear: () => setSelected([]) };
}
