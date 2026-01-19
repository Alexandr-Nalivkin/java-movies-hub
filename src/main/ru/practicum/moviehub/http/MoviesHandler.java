package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.ListOfMoviesTypeToken;
import ru.practicum.moviehub.model.Endpoint;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class MoviesHandler extends BaseHttpHandler {
    private static final String TITLE_FIELD = "title";
    private static final String YEAR_FIELD = "year";

    private static final int MAX_LENGTH_TITLE = 100;
    private static final int MIN_YEAR = 1888;
    private static final int MAX_YEAR = LocalDate.now().getYear() + 1;

    MoviesStore moviesStore;
    Gson gson = new Gson();

    public MoviesHandler(MoviesStore moviesStore, Gson gson) {
        super(gson);
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String[] pathParts = ex.getRequestURI().getPath().split("/");
        URI uri = ex.getRequestURI();
        Map<String, String> params = parseQuery(uri.getQuery());

        Endpoint endpoint = getEndpoint(method, pathParts, params);

        switch (endpoint) {
            case Endpoint.GET -> handleGetAllMovies(ex);
            case Endpoint.POST -> handleAddMovie(ex);
            case Endpoint.GET_BY_ID -> handleGetMovieById(ex, pathParts);
            case Endpoint.DELETE -> handleDeleteMovieById(ex, pathParts);
            case Endpoint.GET_BY_YEAR -> handleGetMovieByYear(ex, params);
            case Endpoint.UNKNOWN_METHOD -> sendNotAllowed(ex);
        }
    }

    private Endpoint getEndpoint(String method, String[] pathParts, Map<String, String> params) {
        switch (method) {
            case "GET" -> {
                if (pathParts.length == 3 && params.isEmpty()) {
                    return Endpoint.GET_BY_ID;
                }

                if (!params.isEmpty() && params.containsKey("year")) {
                    return Endpoint.GET_BY_YEAR;
                }

                return Endpoint.GET;
            }
            case "POST" -> {
                return Endpoint.POST;
            }
            case "DELETE" -> {
                if (pathParts.length == 3) {
                    return Endpoint.DELETE;
                }
            }
            default -> {
                return Endpoint.UNKNOWN_METHOD;
            }
        }
        return Endpoint.UNKNOWN_METHOD;
    }

    private void handleGetAllMovies(HttpExchange ex) throws IOException {
        List<Movie> movies = moviesStore.getAllMovies();
        String jsonResp = gson.toJson(movies, new ListOfMoviesTypeToken().getType());
        sendJson(ex, 200, jsonResp);
    }

    private void handleAddMovie(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        List<String> contentTypeList = ex.getRequestHeaders().get(CT_KEY);
        boolean correctContentType = contentTypeList != null && contentTypeList.contains(CT_JSON);

        if (!correctContentType) {
            sendUnsupportedMediaTypeError(ex, List.of(CT_KEY + " должен содержать " + CT_JSON));
            return;
        }

        try {
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);

            if (Stream.of("title", "year").anyMatch(field -> !isValidField(jsonObject, field))) {
                sendValidationError(ex, List.of("Поля title и year не должны быть пустыми"));
                return;
            }

            String title = jsonObject.get(TITLE_FIELD).getAsString();
            int year = jsonObject.get(YEAR_FIELD).getAsInt();

            List<String> errorText = verifyBody(title, year);
            if (!errorText.isEmpty()) {
                sendValidationError(ex, errorText);
            }

            Movie addedMovie = moviesStore.addMovie(title, year);
            if (addedMovie == null) {
                System.out.println("Фильм уже добавлен");
            }

            sendJson(ex, 201, gson.toJson(addedMovie));
        } catch (JsonSyntaxException e) {
            sendValidationError(ex, List.of(e.getMessage()));
        } catch (NumberFormatException e) {
            sendValidationError(ex, List.of("Поле year не является числом"));
        }
    }

    private void handleGetMovieById(HttpExchange ex, String[] pathParts) throws IOException {
        String idStr = pathParts[2];

        try {
            int id = Integer.parseInt(idStr);

            Movie foundMovie = moviesStore.getMovieById(id);

            if (foundMovie == null) {
                sendJson(ex, 404, createJsonError("Фильм не найден", List.of()));
                return;
            }

            sendJson(ex, 200, gson.toJson(foundMovie));
        } catch (NumberFormatException e) {
            sendJson(ex, 400, createJsonError("Некорректный id", List.of("Поле '%s' не является числом", idStr)));
        }
    }

    private void handleDeleteMovieById(HttpExchange ex, String[] pathParts) throws IOException {
        String idStr = pathParts[2];

        try {
            int id = Integer.parseInt(idStr);

            if (moviesStore.removeMovieById(id)) {
                sendNoContent(ex);
            }

            sendJson(ex, 404, createJsonError("Фильм не найден", List.of()));
        } catch (NumberFormatException e) {
            sendJson(ex, 400, createJsonError("Некорректный id", List.of("Поле '%s' не является числом", idStr)));
        }
    }

    private void handleGetMovieByYear(HttpExchange ex, Map<String, String> params) throws IOException {
        String yearParam = params.get(YEAR_FIELD);

        if (yearParam == null || yearParam.isBlank()) {
            sendValidationError(ex, List.of("Некорректный параметр " + YEAR_FIELD));
            return;
        }
        try {
            int searchYear = Integer.parseInt(yearParam);
            List<Movie> moviesByYear = moviesStore.getMovieByYear(searchYear);
            String jsonResp = gson.toJson(moviesByYear, new ListOfMoviesTypeToken().getType());
            sendJson(ex, 200, jsonResp);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, createJsonError("Некорректный год", List.of("Поле '%s' не является числом", yearParam)));
        }
    }

    private List<String> verifyBody(String title, int year) {
        List<String> errors = new ArrayList<>();

        if (title.isBlank()) {
            errors.add("Название не должно быть пустым");
        } else if (title.length() > MAX_LENGTH_TITLE) {
            errors.add("Длина названия должна быть не более " + MAX_LENGTH_TITLE + " символов");
        }

        if (year < MIN_YEAR || year >= MAX_YEAR) {
            errors.add("Год должен быть между " + MIN_YEAR + " и " + MAX_YEAR);
        }

        return errors;
    }
}
