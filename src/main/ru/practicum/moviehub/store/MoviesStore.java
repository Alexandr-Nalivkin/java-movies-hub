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

    public Movie addMovie(Movie movie) {
        if (!movies.containsValue(movie)) {
            movie.setId(++currentId);
            movies.put(movie.getId(), movie);
            return movie;
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