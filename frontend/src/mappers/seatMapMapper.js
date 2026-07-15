/**
 * BE trả seat-map dạng LIST PHẲNG (ShowtimeSeatResponse), không kèm room/layout.
 * Mapper này ghép List ghế + ShowtimeDetailResponse -> view model FE dùng để render.
 *
 * ShowtimeSeatResponse: { showtimeSeatId, seatId, seatLabel, seatRow, seatNumber,
 *                         gridRow, gridColumn, seatType, price, status, selectable }
 */
// Các loại ghế đôi -> chiếm 2 ô. Thêm tên loại vào đây nếu BE dùng key khác.
const COUPLE_TYPES = ["COUPLE", "SWEETBOX", "DOUBLE"];
function isCoupleType(seatType) {
  return COUPLE_TYPES.includes(String(seatType || "").toUpperCase());
}

export function buildSeatMap(detail, seatList) {
  const seats = (seatList || []).map((s) => ({
    showtimeSeatId: s.showtimeSeatId,
    seatId: s.seatId,
    label: s.seatLabel,
    row: s.seatRow,
    number: s.seatNumber,
    gridRow: s.gridRow,
    gridCol: s.gridColumn,      // BE dùng gridColumn
    // Ghế đôi chiếm 2 ô. Ưu tiên giá trị BE (colSpan/rowSpan);
    // nếu BE chưa trả thì tự suy theo loại ghế COUPLE/SWEETBOX.
    colSpan: s.colSpan ?? (isCoupleType(s.seatType) ? 2 : 1),
    rowSpan: s.rowSpan ?? 1,
    seatType: s.seatType,       // STANDARD | VIP | COUPLE...
    price: Number(s.price) || 0,
    status: s.status,           // AVAILABLE | HELD | BOOKED | ...
    selectable: s.selectable === true, // <-- nguồn sự thật để cho phép click
  }));

  // Nếu CÓ ghế đôi mà BE vẫn đánh gridColumn liền nhau (1,2,3...),
  // các ghế đôi sẽ đè lên nhau. Giãn lại vị trí cột theo từng hàng:
  // đi từ trái sang phải, ghế sau đặt ngay sau ghế trước cộng span.
  const hasCouple = seats.some((s) => s.colSpan > 1);
  if (hasCouple) {
    const byRow = new Map();
    for (const s of seats) {
      if (!byRow.has(s.gridRow)) byRow.set(s.gridRow, []);
      byRow.get(s.gridRow).push(s);
    }
    for (const rowSeats of byRow.values()) {
      rowSeats.sort((a, b) => a.gridCol - b.gridCol); // giữ đúng thứ tự BE
      let cursor = 1;
      for (const s of rowSeats) {
        s.gridCol = cursor;
        cursor += s.colSpan;
      }
    }
  }

  const totalRows = seats.length ? Math.max(...seats.map((s) => s.gridRow)) : 0;
  // totalCols phải tính cả span của ghế đôi
  const totalCols = seats.length
    ? Math.max(...seats.map((s) => s.gridCol + s.colSpan - 1))
    : 0;

  // seat types tự suy ra từ danh sách ghế (BE không trả bảng seat_type)
  const seatTypes = [...new Set(seats.map((s) => s.seatType))];

  return {
    showtimeId: detail?.showtimeId,
    movie: {
      movieId: detail?.movieId,
      title: detail?.movieTitle,
      posterUrl: detail?.moviePosterUrl,
      duration: detail?.movieDuration,
    },
    branch: {
      branchId: detail?.branchId,
      branchName: detail?.branchName,
      address: detail?.branchAddress,
    },
    room: {
      roomId: detail?.roomId,
      roomName: detail?.roomName,
      roomType: detail?.roomType,
    },
    startTime: detail?.startTime,
    endTime: detail?.endTime,
    basePrice: Number(detail?.price) || 0,
    layout: { totalRows, totalCols },
    seatTypes,
    totalSeats: seats.length,
    availableSeats: seats.filter((s) => s.selectable).length,
    seats,
  };
}