// BE trả 2 kiểu: LocalDateTime "2026-07-15T14:37:07" (giờ địa phương, không Z)
// và Instant "...Z" (UTC). Hàm này hiển thị theo giờ VN nhất quán.
function toDate(iso) {
  if (!iso) return null;
  return new Date(iso); // JS tự hiểu Z là UTC; chuỗi không Z coi là local
}

const p = (n) => String(n).padStart(2, "0");

export function formatTime(iso) {
  const d = toDate(iso);
  if (!d) return "";
  return `${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function formatDateTime(iso) {
  const d = toDate(iso);
  if (!d) return "";
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
