/**
 * MOCK BACKEND - SWD392 Cinema Management
 * Chạy: node server.js   (cổng 8081)
 *
 * ĐIỂM QUAN TRỌNG: mỗi phòng chiếu có LAYOUT KHÁC NHAU (số hàng/cột, vị trí lối đi,
 * phân bố loại ghế, ghế đôi, ghế hỏng). Nhờ vậy bạn kiểm tra được FE có render
 * THEO DỮ LIỆU hay không, thay vì vẽ cứng.
 */
import express from "express";
import cors from "cors";

const app = express();
app.use(cors());
app.use(express.json());

const ok = (data, message = "Success") => ({ code: 200, message, data });

const SEAT_TYPES = [
  { seat_type_id: 1, type_name: "Normal", price_multiplier: 1.0 },
  { seat_type_id: 2, type_name: "VIP", price_multiplier: 1.4 },
  { seat_type_id: 3, type_name: "Sweetbox", price_multiplier: 2.0 },
];
const TYPE_BY_ID = Object.fromEntries(SEAT_TYPES.map((t) => [t.seat_type_id, t]));

/* ========================================================================== */
/* ĐỊNH NGHĨA 4 PHÒNG - MỖI PHÒNG MỘT BỐ CỤC KHÁC NHAU                        */
/* ========================================================================== */
/**
 * Mỗi layout khai báo:
 *  rows        : danh sách chữ hàng
 *  seatsPerRow : số ghế mỗi hàng (đánh số giảm dần từ trái sang, kiểu CGV)
 *  aisleCols   : các cột (1-based) là LỐI ĐI dọc -> không đặt ghế
 *  aisleRows   : các hàng (index trong rows) mà SAU nó chèn 1 lối đi ngang
 *  typeOf(row) : trả seat_type_id theo hàng
 *  sweetboxRow : hàng ghế đôi (col_span = 2)
 *  broken      : mã ghế hỏng -> BLOCKED (dấu X)
 *  sold        : mã ghế đã bán -> xám
 */
const ROOM_LAYOUTS = {
  // ---- Phòng 10: nhỏ, 10 hàng, lối đi dọc 1 chỗ, có hàng Sweetbox K ----
  10: {
    room: { room_id: 10, room_name: "Cinema 10", room_type: "2D" },
    branch: { branch_id: 1, branch_name: "CGV Hùng Vương Plaza" },
    base_price: 90000,
    rows: ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K"],
    seatsPerRow: 13,
    aisleCols: [12],
    aisleRows: [2],
    typeOf: (r) => (["A", "B", "C"].includes(r) ? 1 : r === "K" ? 3 : 2),
    sweetboxRow: "K",
    sold: ["E9", "F9", "F10", "G9", "G10", "H7", "H10", "H11", "J1", "J2"],
    broken: ["A1"],
  },

  // ---- Phòng 20: RỘNG, 12 hàng x 20 ghế, HAI lối đi dọc, không có Sweetbox ----
  20: {
    room: { room_id: 20, room_name: "Cinema 2 (Rộng)", room_type: "2D" },
    branch: { branch_id: 2, branch_name: "CGV Menas Mall (CGV CT Plaza)" },
    base_price: 85000,
    rows: ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M"],
    seatsPerRow: 20,
    aisleCols: [5, 16],
    aisleRows: [3, 8],
    typeOf: (r) => (["A", "B", "C", "D"].includes(r) ? 1 : 2),
    sweetboxRow: null,
    sold: ["F8", "F9", "F10", "G8", "G9", "K3", "K4", "L1", "M20"],
    broken: ["C10", "C11"],
  },

  // ---- Phòng 30: IMAX, rất rộng 14 hàng x 24 ghế, 2 hàng Sweetbox cuối ----
  30: {
    room: { room_id: 30, room_name: "Cinema 3 (IMAX)", room_type: "IMAX" },
    branch: { branch_id: 3, branch_name: "CGV Crescent Mall" },
    base_price: 140000,
    rows: ["A","B","C","D","E","F","G","H","J","K","L","M","N","P"],
    seatsPerRow: 24,
    aisleCols: [6, 19],
    aisleRows: [4],
    typeOf: (r) => (["N", "P"].includes(r) ? 3 : ["A","B","C","D","E"].includes(r) ? 1 : 2),
    sweetboxRow: "ALL_TYPE_3",
    sold: ["H12","H13","J12","J13","K10","K11","L5","M20","N2","P4"],
    broken: ["A24", "B24"],
  },

  // ---- Phòng 40: Gold Class, NHỎ XÍU, toàn ghế đôi, 5 hàng x 6 ghế ----
  40: {
    room: { room_id: 40, room_name: "Cinema 1 (Gold Class)", room_type: "GOLD" },
    branch: { branch_id: 4, branch_name: "CGV Pandora City" },
    base_price: 200000,
    rows: ["A", "B", "C", "D", "E"],
    seatsPerRow: 6,
    aisleCols: [4],
    aisleRows: [],
    typeOf: () => 3, // tất cả là ghế đôi
    sweetboxRow: "ALL_TYPE_3",
    sold: ["B2", "C5"],
    broken: [],
  },
};

// showtime_id -> room_id  (BE thật thì JOIN showtime.room_id)
const SHOWTIME_TO_ROOM = {};
[1001, 1002, 1003, 1004].forEach((id) => (SHOWTIME_TO_ROOM[id] = 10));
[2001, 2002, 2003, 2004, 2005].forEach((id) => (SHOWTIME_TO_ROOM[id] = 20));
[3001, 3002, 3003, 3004, 3005, 3006].forEach((id) => (SHOWTIME_TO_ROOM[id] = 30));
[4001, 4002, 4003, 4004].forEach((id) => (SHOWTIME_TO_ROOM[id] = 40));

/* ========================================================================== */
/* SINH GHẾ TỪ LAYOUT                                                         */
/* ========================================================================== */
function buildSeats(cfg) {
  const seats = [];
  let seatId = 1;
  const soldSet = new Set(cfg.sold || []);
  const brokenSet = new Set(cfg.broken || []);

  cfg.rows.forEach((row, rIdx) => {
    // chèn lối đi ngang: mỗi aisleRow đứng trước rIdx thì đẩy grid_row xuống 1
    const shift = (cfg.aisleRows || []).filter((a) => rIdx > a).length;
    const gridRow = rIdx + 1 + shift;

    const typeId = cfg.typeOf(row);
    const isSweetbox = typeId === 3;
    const step = isSweetbox ? 2 : 1; // ghế đôi chiếm 2 cột

    // Ghế đôi chiếm 2 cột -> số lượng ghế trên hàng đó chỉ bằng ~1/2
    const count = isSweetbox ? Math.floor(cfg.seatsPerRow / 2) : cfg.seatsPerRow;

    // đánh số ghế giảm dần: count ... 1 (giống CGV)
    let num = count;
    let gridCol = 1;

    while (num >= 1) {
      // bỏ qua cột là lối đi
      if ((cfg.aisleCols || []).includes(gridCol)) {
        gridCol++;
        continue;
      }
      // ghế đôi cần 2 cột liền, không được đè lối đi
      if (isSweetbox && (cfg.aisleCols || []).includes(gridCol + 1)) {
        gridCol++;
        continue;
      }

      const code = `${row}${num}`;
      seats.push({
        seat_id: seatId++,
        seat_row: row,
        seat_number: String(num),
        grid_row: gridRow,
        grid_col: gridCol,
        row_span: 1,
        col_span: step,
        seat_type_id: typeId,
        seat_status: soldSet.has(code) ? "SOLD" : "AVAILABLE",
        physical_status: brokenSet.has(code) ? "MAINTENANCE" : "ACTIVE",
      });

      gridCol += step;
      num--;
    }
  });
  return seats;
}

// build sẵn cho mỗi phòng
const ROOM_SEATS = {};
Object.entries(ROOM_LAYOUTS).forEach(([roomId, cfg]) => {
  ROOM_SEATS[roomId] = buildSeats(cfg);
});

/* ========================================================================== */
/* 1) LỊCH CHIẾU                                                              */
/* ========================================================================== */
app.get("/api/v1/movies/:movieId/showtimes", (req, res) => {
  const movieId = Number(req.params.movieId);
  const date = req.query.date || "2026-07-10";
  const mk = (id, t, status = "Available") => ({
    showtime_id: id,
    start_time: `${date}T${t}:00`,
    end_time: `${date}T${t}:00`,
    status,
  });

  const roomOf = (rid, showtimes) => {
    const cfg = ROOM_LAYOUTS[rid];
    return {
      room_id: rid,
      room_name: cfg.room.room_name,
      room_type: cfg.room.room_type,
      capacity: ROOM_SEATS[rid].length,
      showtimes,
    };
  };

  const data = [
    {
      city: "Hồ Chí Minh",
      vincom_cinemas: [
        {
          branch_id: 1,
          branch_name: "CGV Hùng Vương Plaza",
          address: "126 Hùng Vương, Quận 5, TP.HCM",
          rooms: [roomOf(10, [mk(1001, "18:20"), mk(1002, "19:20"), mk(1003, "20:50"), mk(1004, "21:40", "Filling Fast")])],
        },
        {
          branch_id: 2,
          branch_name: "CGV Menas Mall (CGV CT Plaza)",
          address: "60A Trường Sơn, Tân Bình, TP.HCM",
          rooms: [roomOf(20, [mk(2001, "18:00"), mk(2002, "19:30"), mk(2003, "20:30"), mk(2004, "22:00"), mk(2005, "23:00")])],
        },
        {
          branch_id: 3,
          branch_name: "CGV Crescent Mall",
          address: "101 Tôn Dật Tiên, Quận 7, TP.HCM",
          rooms: [roomOf(30, [mk(3001, "18:00"), mk(3002, "18:40"), mk(3003, "19:40"), mk(3004, "20:30"), mk(3005, "22:10"), mk(3006, "23:00", "Sold Out")])],
        },
        {
          branch_id: 4,
          branch_name: "CGV Pandora City",
          address: "1/1 Trường Chinh, Tân Phú, TP.HCM",
          rooms: [roomOf(40, [mk(4001, "18:10"), mk(4002, "19:30"), mk(4003, "20:40"), mk(4004, "22:00")])],
        },
      ],
    },
    { city: "Hà Nội", vincom_cinemas: [] },
    { city: "Đà Nẵng", vincom_cinemas: [] },
  ];

  res.json(ok({ movie_id: movieId, query_date: date, data }));
});

/* ========================================================================== */
/* 2) SEAT-MAP - trả layout ĐÚNG THEO PHÒNG của showtime                      */
/* ========================================================================== */
app.get("/api/v1/showtimes/:showtimeId/seat-map", (req, res) => {
  const showtimeId = Number(req.params.showtimeId);
  const roomId = SHOWTIME_TO_ROOM[showtimeId];

  if (!roomId) {
    return res.status(404).json({ code: 404, message: "Showtime not found", data: null });
  }

  const cfg = ROOM_LAYOUTS[roomId];
  const raw = ROOM_SEATS[roomId];

  const seats = raw.map((s) => {
    const t = TYPE_BY_ID[s.seat_type_id];
    let effective = s.seat_status;
    if (s.physical_status !== "ACTIVE") effective = "BLOCKED";
    return {
      showtime_seat_id: showtimeId * 1000 + s.seat_id,
      seat_id: s.seat_id,
      row_label: s.seat_row,
      number_label: s.seat_number,
      grid_row: s.grid_row,
      grid_col: s.grid_col,
      row_span: s.row_span,
      col_span: s.col_span,
      seat_type_id: s.seat_type_id,
      type_name: t.type_name,
      price: Math.round(cfg.base_price * t.price_multiplier),
      effective_status: effective,
      locked_until: null,
    };
  });

  const totalCols = Math.max(...seats.map((s) => s.grid_col + s.col_span - 1));
  const totalRows = Math.max(...seats.map((s) => s.grid_row));

  res.json(
    ok({
      showtime_id: showtimeId,
      movie: { title: "RUNNING MAN VIỆT NAM 2026 - CHÚA TỂ THỜI GIAN", age_rating: "P", format: cfg.room.room_type },
      room: cfg.room,
      branch: cfg.branch,
      start_time: "2026-07-10T20:00:00",
      end_time: "2026-07-10T22:25:00",
      base_price: cfg.base_price,
      seat_types: SEAT_TYPES,
      layout: { total_rows: totalRows, total_cols: totalCols },
      total_seats: seats.length,
      available_seats: seats.filter((s) => s.effective_status === "AVAILABLE").length,
      seats,
    })
  );
});

/* ========================================================================== */
/* 3) LOCK                                                                    */
/* ========================================================================== */
app.post("/api/v1/showtimes/:showtimeId/seats/lock", (req, res) => {
  const { seatIds = [] } = req.body || {};
  res.json(
    ok({
      lock_id: "mock-lock-" + Date.now(),
      locked_until: new Date(Date.now() + 7 * 60 * 1000).toISOString(),
      seat_ids: seatIds,
    })
  );
});

app.delete("/api/v1/showtimes/:showtimeId/seats/lock", (req, res) => {
  res.json(ok({ released: true }));
});

const PORT = 8081;
app.listen(PORT, () => {
  console.log(`🎬 Mock backend: http://localhost:${PORT}`);
  console.log("Các phòng (mỗi phòng layout khác nhau):");
  Object.entries(ROOM_LAYOUTS).forEach(([rid, c]) => {
    console.log(`  room ${rid} - ${c.room.room_name.padEnd(24)} ${ROOM_SEATS[rid].length} ghế`);
  });
});
