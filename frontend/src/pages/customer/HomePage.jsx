import { useEffect, useState } from "react";
import axiosClient from "@/api/client/axiosClient";
import MainLayout from "@/layouts/MainLayout";
import ShowtimeModal from "@/features/movie-showtime/components/ShowtimeModal";

export default function HomePage() {
  const [movies, setMovies] = useState([]);
  const [err, setErr] = useState(null);
  const [picked, setPicked] = useState(null); // {id, title}

  useEffect(() => {
    axiosClient.get("/movies").then(setMovies).catch((e) => setErr(e.message));
  }, []);

  return (
    <MainLayout>
      <div className="hero-banner">SUẤT CHIẾU ĐẶC BIỆT</div>

      <h2 className="section-title">MOVIE SELECTION</h2>

      {err && <p style={{ textAlign: "center", color: "#e4022b" }}>Lỗi tải phim: {err}</p>}

      <div className="movie-grid">
        {movies.map((m) => (
          <div className="movie-card" key={m.movieId}>
            {m.posterUrl
              ? <img className="movie-poster" src={m.posterUrl} alt={m.title} />
              : <div className="movie-poster" />}
            <div className="body">
              <div className="title">{m.title}</div>
              <button className="btn-buy" onClick={() => setPicked({ id: m.movieId, title: m.title, poster: m.posterUrl })}>
                MUA VÉ
              </button>
            </div>
          </div>
        ))}
      </div>

      {picked && (
        <ShowtimeModal
          movieId={picked.id}
          movieTitle={picked.title}
          moviePoster={picked.poster}
          onClose={() => setPicked(null)}
        />
      )}
    </MainLayout>
  );
}
