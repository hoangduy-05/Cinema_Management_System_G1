package com.fpt.cinema.integration;

import com.fpt.cinema.dto.request.HoldSeatsRequest;
import com.fpt.cinema.entity.Account;
import com.fpt.cinema.entity.Branch;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.Movie;
import com.fpt.cinema.entity.Room;
import com.fpt.cinema.entity.Seat;
import com.fpt.cinema.entity.SeatType;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.enums.ShowtimeSeatStatus;
import com.fpt.cinema.exception.SeatUnavailableException;
import com.fpt.cinema.repository.AccountRepository;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.repository.BranchRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.MovieRepository;
import com.fpt.cinema.repository.RoomRepository;
import com.fpt.cinema.repository.SeatRepository;
import com.fpt.cinema.repository.SeatTypeRepository;
import com.fpt.cinema.repository.ShowtimeRepository;
import com.fpt.cinema.repository.ShowtimeSeatRepository;
import com.fpt.cinema.service.SeatReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.jwt.secret=",
        "booking.payment.simulation-token=",
        "booking.expiration-scan-fixed-delay-ms=3600000",
        "booking.completion-scan-fixed-delay-ms=3600000"
})
class SeatReservationConcurrencyIntegrationTest {

    @Autowired
    private SeatReservationService seatReservationService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private SeatTypeRepository seatTypeRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private ShowtimeRepository showtimeRepository;
    @Autowired
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void exactlyOneConcurrentRequestCanHoldTheSameSeat() throws Exception {
        Fixture fixture = createFixture();
        HoldSeatsRequest request = new HoldSeatsRequest(
                fixture.showtimeId(),
                List.of(fixture.showtimeSeatId())
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AttemptResult> first = executor.submit(() -> attemptHold(request, ready, start));
            Future<AttemptResult> second = executor.submit(() -> attemptHold(request, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<AttemptResult> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );

            assertThat(results).containsExactlyInAnyOrder(AttemptResult.SUCCESS, AttemptResult.CONFLICT);
            assertThat(bookingRepository.count()).isEqualTo(1L);

            ShowtimeSeat persistedSeat = showtimeSeatRepository.findById(fixture.showtimeSeatId()).orElseThrow();
            assertThat(persistedSeat.getSeatStatus()).isEqualTo(ShowtimeSeatStatus.HELD);
            assertThat(persistedSeat.getBooking()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private AttemptResult attemptHold(
            HoldSeatsRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent start barrier timed out");
        }
        try {
            seatReservationService.holdSeats(request, "concurrent.customer");
            return AttemptResult.SUCCESS;
        } catch (SeatUnavailableException exception) {
            return AttemptResult.CONFLICT;
        }
    }

    private Fixture createFixture() {
        Account account = new Account();
        account.setUsername("concurrent.customer");
        account.setEmail("concurrent.customer@example.com");
        account.setPasswordHash("test-only-password-hash");
        account.setStatus("ACTIVE");
        account.setCreatedAt(LocalDateTime.now());
        account = accountRepository.save(account);

        Customer customer = new Customer();
        customer.setAccount(account);
        customer.setFullName("Concurrent Customer");
        customerRepository.save(customer);

        Branch branch = new Branch();
        branch.setBranchName("Concurrency Test Branch");
        branch.setAddress("Test address");
        branch.setStatus("ACTIVE");
        branch = branchRepository.save(branch);

        Movie movie = new Movie();
        movie.setTitle("Concurrency Test Movie");
        movie.setDuration(120);
        movie.setStatus("ACTIVE");
        movie = movieRepository.save(movie);

        Room room = new Room();
        room.setBranch(branch);
        room.setRoomName("Room 1");
        room.setRoomType("2D");
        room.setCapacity(1);
        room.setStatus("ACTIVE");
        room = roomRepository.save(room);

        SeatType seatType = new SeatType();
        seatType.setTypeName("STANDARD-CONCURRENCY");
        seatType.setPriceMultiplier(new BigDecimal("1.00"));
        seatType.setStatus("ACTIVE");
        seatType = seatTypeRepository.save(seatType);

        Seat seat = new Seat();
        seat.setRoom(room);
        seat.setSeatType(seatType);
        seat.setSeatRow("A");
        seat.setSeatNumber("1");
        seat.setGridRow(1);
        seat.setGridCol(1);
        seat.setRowSpan(1);
        seat.setColSpan(1);
        seat.setStatus("ACTIVE");
        seat = seatRepository.save(seat);

        LocalDateTime startTime = LocalDateTime.now().plusHours(2);
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setStartTime(startTime);
        showtime.setEndTime(startTime.plusHours(2));
        showtime.setPrice(new BigDecimal("90000.00"));
        showtime.setStatus("AVAILABLE");
        showtime = showtimeRepository.save(showtime);

        ShowtimeSeat showtimeSeat = new ShowtimeSeat();
        showtimeSeat.setShowtime(showtime);
        showtimeSeat.setSeat(seat);
        showtimeSeat.setSeatStatus(ShowtimeSeatStatus.AVAILABLE);
        showtimeSeat = showtimeSeatRepository.save(showtimeSeat);

        return new Fixture(showtime.getShowtimeId(), showtimeSeat.getShowtimeSeatId());
    }

    private record Fixture(Long showtimeId, Long showtimeSeatId) {
    }

    private enum AttemptResult {
        SUCCESS,
        CONFLICT
    }
}
