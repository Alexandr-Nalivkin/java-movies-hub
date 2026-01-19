package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {
    private final Map<Integer, Movie> movies;
    private int currentId;

    public MoviesStore() {
        this.movies = new HashMap<>();
    }

    public List<Movie> getAllMovies() {
        return movies.values().stream().toList();
    }

    public Movie addMovie(String title, int year) {
        Movie newMovie = new Movie(title, year);

        if (!movies.containsValue(newMovie)) {
            newMovie.setId(++currentId);
            movies.put(newMovie.getId(), newMovie);
            return newMovie;
        }

        return null;
    }

    public Movie getMovieById(int id) {
        return movies.get(id);
    }

    public boolean removeMovieById(int id) {
        return movies.remove(id) != null;
    }

    public List<Movie> getMovieByYear(int searchYear) {
        return movies.values().stream().filter(movie -> movie.getYear() == searchYear).toList();
    }

    public void clearAll() {
        movies.clear();
    }

    public void setCurrentId(int currentId) {
        this.currentId = currentId;
    }
}