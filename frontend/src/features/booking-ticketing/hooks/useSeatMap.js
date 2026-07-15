import { useCallback, useEffect, useState } from "react";
import { showtimeApi } from "@/api/services/showtimeApi";
import { buildSeatMap } from "@/mappers/seatMapMapper";

export function useSeatMap(showtimeId, { pollMs = 10000 } = {}) {
  const [seatMap, setSeatMap] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      // BE tách 2 endpoint: detail (phim/phòng/giờ) + seats (danh sách ghế)
      const [detail, seats] = await Promise.all([
        showtimeApi.getShowtimeDetail(showtimeId),
        showtimeApi.getShowtimeSeats(showtimeId),
      ]);
      setSeatMap(buildSeatMap(detail, seats));
      setError(null);
    } catch (e) {
      console.error("[useSeatMap]", e);
      setError(e.message || "Không tải được sơ đồ ghế");
    } finally {
      setLoading(false);
    }
  }, [showtimeId]);

  useEffect(() => {
    load();
    const t = setInterval(load, pollMs); // thấy ghế người khác vừa giữ
    return () => clearInterval(t);
  }, [load, pollMs]);

  return { seatMap, loading, error, reload: load };
}
