package ru.practicum.moviehub.model;

import java.util.Objects;

public class Movie {
    private int id;
    private final String title;
    private final int year;

    public Movie(String title, int year) {
        this.title = title;
        this.year = year;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public int getYear() {
        return year;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Movie movie = (Movie) o;
        return year == movie.year && Objects.equals(title, movie.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, year);
    }
}