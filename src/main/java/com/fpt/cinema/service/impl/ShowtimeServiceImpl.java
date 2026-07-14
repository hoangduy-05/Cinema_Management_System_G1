package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.response.BranchShowtimesResponse;
import com.fpt.cinema.dto.response.QuickMovieShowtimesResponse;
import com.fpt.cinema.dto.response.ShowtimeDateResponse;
import com.fpt.cinema.dto.response.ShowtimeDetailResponse;
import com.fpt.cinema.dto.response.ShowtimeResponse;
import com.fpt.cinema.dto.response.ShowtimeSeatResponse;
import com.fpt.cinema.entity.Branch;
import com.fpt.cinema.entity.Movie;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.enums.ShowtimeSeatStatus;
import com.fpt.cinema.mapper.BranchMapper;
import com.fpt.cinema.mapper.MovieMapper;
import com.fpt.cinema.mapper.ShowtimeMapper;
import com.fpt.cinema.repository.BranchRepository;
import com.fpt.cinema.repository.MovieRepository;
import com.fpt.cinema.repository.ShowtimeRepository;
import com.fpt.cinema.repository.ShowtimeSeatRepository;
import com.fpt.cinema.service.ShowtimeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ShowtimeServiceImpl implements ShowtimeService {

    private static final String ACTIVE = "ACTIVE";

    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final MovieRepository movieRepository;
    private final BranchRepository branchRepository;
    private final ShowtimeMapper showtimeMapper;
    private final MovieMapper movieMapper;
    private final BranchMapper branchMapper;
    private final Clock clock;

    public ShowtimeServiceImpl(
            ShowtimeRepository showtimeRepository,
            ShowtimeSeatRepository showtimeSeatRepository,
            MovieRepository movieRepository,
            BranchRepository branchRepository,
            ShowtimeMapper showtimeMapper,
            MovieMapper movieMapper,
            BranchMapper branchMapper,
            Clock clock
    ) {
        this.showtimeRepository = showtimeRepository;
        this.showtimeSeatRepository = showtimeSeatRepository;
        this.movieRepository = movieRepository;
        this.branchRepository = branchRepository;
        this.showtimeMapper = showtimeMapper;
        this.movieMapper = movieMapper;
        this.branchMapper = branchMapper;
        this.clock = clock;
    }

    @Override
    public List<ShowtimeResponse> getShowtimes(Long movieId, LocalDate date, Long branchId) {
        if (movieId != null) {
            requireActiveMovie(movieId);
        }
        if (branchId != null) {
            requireActiveBranch(branchId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime rangeStart = now;
        LocalDateTime rangeEnd = null;
        if (date != null) {
            rangeEnd = date.plusDays(1).atStartOfDay();
            if (!rangeEnd.isAfter(now)) {
                return List.of();
            }
            LocalDateTime startOfDay = date.atStartOfDay();
            if (startOfDay.isAfter(now)) {
                rangeStart = startOfDay;
            }
        }

        return showtimeMapper.toResponses(showtimeRepository.findForBrowse(
                movieId,
                branchId,
                rangeStart,
                rangeEnd
        ));
    }

    @Override
    public ShowtimeDetailResponse getShowtime(Long showtimeId) {
        return showtimeMapper.toDetailResponse(requirePublicShowtime(showtimeId));
    }

    @Override
    public List<ShowtimeSeatResponse> getShowtimeSeats(Long showtimeId) {
        Showtime showtime = requirePublicShowtime(showtimeId);
        boolean showtimeIsFuture = showtime.getStartTime().isAfter(LocalDateTime.now(clock));

        return showtimeSeatRepository.findAllForSeatMap(showtimeId).stream()
                .map(showtimeSeat -> showtimeMapper.toSeatResponse(
                        showtimeSeat,
                        isSelectable(showtimeSeat, showtimeIsFuture)
                ))
                .toList();
    }

    @Override
    public List<ShowtimeDateResponse> getAvailableDates(Long movieId, Long branchId) {
        return showtimeRepository.findAvailableStartTimes(
                        movieId,
                        branchId,
                        LocalDateTime.now(clock)
                ).stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .map(ShowtimeDateResponse::new)
                .toList();
    }

    @Override
    public List<BranchShowtimesResponse> getMovieShowtimes(Long movieId, LocalDate date, Long branchId) {
        requireActiveMovie(movieId);
        if (branchId != null) {
            requireActiveBranch(branchId);
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime nextDay = date.plusDays(1).atStartOfDay();
        List<Showtime> showtimes = showtimeRepository.findAvailableByMovieAndDate(
                movieId,
                branchId,
                startOfDay,
                nextDay
        );

        Map<Long, List<Showtime>> groupedByBranch = new LinkedHashMap<>();
        for (Showtime showtime : showtimes) {
            Long currentBranchId = showtime.getRoom().getBranch().getBranchId();
            groupedByBranch.computeIfAbsent(currentBranchId, ignored -> new ArrayList<>()).add(showtime);
        }

        return groupedByBranch.values().stream()
                .map(group -> {
                    Branch branch = group.getFirst().getRoom().getBranch();
                    return new BranchShowtimesResponse(
                            branchMapper.toResponse(branch),
                            showtimeMapper.toResponses(group)
                    );
                })
                .toList();
    }

    @Override
    public List<QuickMovieShowtimesResponse> getQuickShowtimes(Long branchId, LocalDate date) {
        requireActiveBranch(branchId);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime nextDay = date.plusDays(1).atStartOfDay();
        List<Showtime> showtimes = showtimeRepository.findAvailableByBranchAndDate(
                branchId,
                startOfDay,
                nextDay
        );

        Map<Long, List<Showtime>> groupedByMovie = new LinkedHashMap<>();
        for (Showtime showtime : showtimes) {
            Long movieId = showtime.getMovie().getMovieId();
            groupedByMovie.computeIfAbsent(movieId, ignored -> new ArrayList<>()).add(showtime);
        }

        return groupedByMovie.values().stream()
                .map(group -> {
                    Movie movie = group.getFirst().getMovie();
                    return new QuickMovieShowtimesResponse(
                            movieMapper.toSummaryResponse(movie),
                            showtimeMapper.toResponses(group)
                    );
                })
                .toList();
    }

    private void requireActiveMovie(Long movieId) {
        if (!movieRepository.existsByMovieIdAndStatus(movieId, ACTIVE)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy phim đang hoạt động với mã " + movieId
            );
        }
    }

    private void requireActiveBranch(Long branchId) {
        if (!branchRepository.existsByBranchIdAndStatus(branchId, ACTIVE)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy chi nhánh đang hoạt động với mã " + branchId
            );
        }
    }

    private Showtime requirePublicShowtime(Long showtimeId) {
        return showtimeRepository.findPublicDetailById(showtimeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lịch chiếu đang hoạt động với mã " + showtimeId
                ));
    }

    private boolean isSelectable(ShowtimeSeat showtimeSeat, boolean showtimeIsFuture) {
        return showtimeIsFuture
                && showtimeSeat.getSeatStatus() == ShowtimeSeatStatus.AVAILABLE
                && ACTIVE.equalsIgnoreCase(showtimeSeat.getSeat().getStatus())
                && ACTIVE.equalsIgnoreCase(showtimeSeat.getSeat().getSeatType().getStatus());
    }
}
