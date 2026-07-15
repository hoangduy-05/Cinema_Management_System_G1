/**
 * Màu ghế theo seatType do BE trả.
 * Nếu BE trả một seatType chưa biết (vd "PREMIUM"), KHÔNG rơi về xám —
 * hệ thống tự cấp một màu từ palette để các loại vẫn phân biệt được.
 */
const KNOWN = {
  STANDARD: { border: "#3aa856", fill: null, label: "Thường" },
  NORMAL:   { border: "#3aa856", fill: null, label: "Thường" },
  VIP:      { border: "#e24b4a", fill: null, label: "VIP" },
  COUPLE:   { border: "#ec4c8f", fill: "#ec4c8f", label: "Ghế đôi" },
  SWEETBOX: { border: "#ec4c8f", fill: "#ec4c8f", label: "Sweetbox" },
};

// Palette dự phòng cho seatType lạ
const PALETTE = ["#7e57c2", "#f0a500", "#26a69a", "#5c6bc0", "#8d6e63"];
const assigned = new Map();

export function styleOfType(seatType) {
  const key = String(seatType || "").toUpperCase();
  if (KNOWN[key]) return KNOWN[key];

  if (!assigned.has(key)) {
    assigned.set(key, PALETTE[assigned.size % PALETTE.length]);
  }
  return { border: assigned.get(key), fill: null, label: seatType || "Khác" };
}

export const UNAVAILABLE_BG = "#b8b8b8"; // đã giữ / đã bán
export const SELECTED_BG = "#8b1a1a";    // ghế mình chọn
