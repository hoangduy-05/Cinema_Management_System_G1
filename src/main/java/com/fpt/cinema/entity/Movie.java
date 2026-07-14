package com.fpt.cinema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "movie")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "synopsis", columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "cast_text", columnDefinition = "TEXT")
    private String castText;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "language", length = 100)
    private String language;

    @Column(name = "director", length = 255)
    private String director;

    @Column(name = "age_rating", length = 30)
    private String ageRating;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    private Set<MovieGenre> movieGenres = new LinkedHashSet<>();

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    private Set<MovieReview> reviews = new LinkedHashSet<>();
}
