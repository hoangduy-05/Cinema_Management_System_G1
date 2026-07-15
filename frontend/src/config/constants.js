// Giá trị status ghế do BE trả (ShowtimeSeatResponse.status)
export const SEAT_STATUS = {
  AVAILABLE: "AVAILABLE",
  HELD: "HELD",       // đang bị người khác giữ
  BOOKED: "BOOKED",   // đã bán
  BLOCKED: "BLOCKED", // ghế hỏng (nếu BE có)
};

// Loại ghế do BE trả (ShowtimeSeatResponse.seatType)
export const SEAT_TYPE = {
  STANDARD: "STANDARD",
  VIP: "VIP",
  COUPLE: "COUPLE",
  SWEETBOX: "SWEETBOX",
};

export const MAX_SEATS_PER_ORDER = 20; // BE giới hạn 20 (HoldSeatsRequest @Size(max=20))
