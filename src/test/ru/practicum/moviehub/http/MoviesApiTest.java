package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static final Gson gson = new Gson();
    private static MoviesStore store;
    private static final int MAX_LENGTH_TITLE = 100;
    private static final int MIN_YEAR = 1888;
    private static final int MAX_YEAR = LocalDate.now().getYear() + 1;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        store.clearAll();
        store.setCurrentId(0);
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMovie_whenNotEmpty_returnAllMovie() throws IOException, InterruptedException {
        Movie movie1 = new Movie("The Gentlemen", 2019);
        Movie movie2 = new Movie("Dune", 2021);

        HttpRequest reqPost1 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie1)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpRequest reqPost2 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie2)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        client.send(reqPost1, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        client.send(reqPost2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest reqGet = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp =
                client.send(reqGet, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(2, movies.size());
    }

    @Test
    void addMovie_WhenAdded_returnsMovieWithId() throws IOException, InterruptedException {
        Movie movie = new Movie("The Gentlemen", 2019);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        Movie movieFromResp = gson.fromJson(resp.body(), Movie.class);
        assertEquals(movie.getTitle(), movieFromResp.getTitle());
        assertEquals(movie.getYear(), movieFromResp.getYear());
        assertEquals(1, movieFromResp.getId());
    }

    @Test
    void addMovie_whenEmptyTitleAndYearBefore1888_returnsValidationError() throws IOException, InterruptedException {
        Movie movie = new Movie("", 1887);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);
        List<String> details = errorResponse.getDetails();

        assertEquals("Ошибка валидации", errorResponse.getError());
        assertTrue(details.contains("Название не должно быть пустым")
                && details.contains("Год должен быть между " + MIN_YEAR + " и " + MAX_YEAR));
    }

    @Test
    void addMovie_whenTitleLengthMore100AndYearEqualNextYear_returnsValidationError() throws IOException, InterruptedException {
        Movie movie = new Movie(
                "Давным-давно в далекой-далекой галактике шли... Звездные войны. ЭПИЗОД 4 НОВАЯ НАДЕЖДА В Галактике бушует гражданская война.", +
                MAX_YEAR);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);
        List<String> details = errorResponse.getDetails();

        assertEquals("Ошибка валидации", errorResponse.getError());
        assertTrue(details.contains("Длина названия должна быть не более " + MAX_LENGTH_TITLE + " символов")
                && details.contains("Год должен быть между " + MIN_YEAR + " и " + MAX_YEAR));
    }

    @Test
    void addMovie_whenWrongContentType_returnUnsupportedMediaTypeError() throws IOException, InterruptedException {
        Movie movie = new Movie("The Gentlemen", 2019);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .header("Content-Type", "application/json;")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "POST /movies должен вернуть 415");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);
        List<String> details = errorResponse.getDetails();

        assertTrue(details.contains("Content-Type должен содержать application/json; charset=UTF-8"));
    }

    @Test
    void sendWithUnknownMethod_returnsNotAllowedResponse() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .HEAD()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode(), "HEAD /movies должен вернуть 405");
    }

    @Test
    void getMovieById_WhenAddedTwoMovies_returnsMovieById1() throws IOException, InterruptedException {
        Movie movie1 = new Movie("The Gentlemen", 2019);
        Movie movie2 = new Movie("Dune", 2021);

        HttpRequest reqPost1 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie1)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpRequest reqPost2 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie2)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        client.send(reqPost1, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        client.send(reqPost2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest reqGet = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp =
                client.send(reqGet, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies/{id} должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        Movie movieFromResp = gson.fromJson(resp.body(), Movie.class);
        assertEquals(1, movieFromResp.getId());
        assertEquals(movie1.getTitle(), movieFromResp.getTitle());
        assertEquals(movie1.getYear(), movieFromResp.getYear());
    }

    @Test
    void getMovieById_WhenEmptyStore_returnsNotFoundError() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(), "GET /movies/{id} должен вернуть 404");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);

        assertEquals("Фильм не найден", errorResponse.getError());
    }

    @Test
    void getMovieById_WhenSendNotNumberId_returnsBadRequestError() throws IOException, InterruptedException {
        HttpRequest reqGet = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp =
                client.send(reqGet, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "GET /movies/{id} должен вернуть 400");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);

        assertEquals("Некорректный id", errorResponse.getError());
    }

    @Test
    void deleteMovieById_returnsNoContent() throws IOException, InterruptedException {
        Movie movie = new Movie("Dune", 2021);

        HttpRequest reqPost = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> respPost = client.send(reqPost, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie movieFromResp = gson.fromJson(respPost.body(), Movie.class);

        HttpRequest reqDelete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movieFromResp.getId()))
                .DELETE()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> respDelete = client.send(reqDelete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(204, respDelete.statusCode(), "DELETE /movies/{id} должен вернуть 204");

        HttpRequest reqGet = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movieFromResp.getId()))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();
        HttpResponse<String> respGet = client.send(reqGet, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, respGet.statusCode(), "GET /movies/{id} должен вернуть 404");

        String contentTypeHeaderValue =
                respGet.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(respGet.body(), ErrorResponse.class);

        assertEquals("Фильм не найден", errorResponse.getError());
    }

    @Test
    void deleteMovieById_WhenNotExistMovie_returnsNotFoundError() throws IOException, InterruptedException {
        Movie movie = new Movie("Dune", 2021);

        HttpRequest reqPost = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        client.send(reqPost, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest reqDelete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/3"))
                .DELETE()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> respDelete = client.send(reqDelete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, respDelete.statusCode(), "GET /movies/{id} должен вернуть 404");

        String contentTypeHeaderValue =
                respDelete.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(respDelete.body(), ErrorResponse.class);

        assertEquals("Фильм не найден", errorResponse.getError());
    }

    @Test
    void deleteMovieById_WhenSendNotNumberId_returnsBadRequestError() throws IOException, InterruptedException {
        HttpRequest reqDelete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp =
                client.send(reqDelete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "GET /movies/{id} должен вернуть 400");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);

        assertEquals("Некорректный id", errorResponse.getError());
    }

    @Test
    void getMovieByYear_whenAddedThreeMovie_returnTwoMovies() throws IOException, InterruptedException {
        Movie movie1 = new Movie("The Gentlemen", 2019);
        Movie movie2 = new Movie("Dune", 2021);
        Movie movie3 = new Movie("No Time To Die", 2021);

        HttpRequest reqPost1 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie1)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpRequest reqPost2 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie2)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpRequest reqPost3 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie3)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        client.send(reqPost1, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        client.send(reqPost2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        client.send(reqPost3, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest reqGetByYear = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + 2021))
                .GET()
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp =
                client.send(reqGetByYear, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies?year= должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(movie -> movie.getYear() == 2021));
    }

    @Test
    void getMoviesByYear_WhenNotExist_returnsEmptyArray() throws IOException, InterruptedException {
        Movie movie = new Movie("No Time To Die", 2021);

        HttpRequest reqPost = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movie)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        client.send(reqPost, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest reqGetByYear = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + 2020))
                .GET()
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp =
                client.send(reqGetByYear, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies?year= должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMoviesByYear_WhenSendNotNumberYear_returnsBadRequestError() throws IOException, InterruptedException {
        HttpRequest reqGetByYear = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(2))
                .build();

        HttpResponse<String> resp = client.send(reqGetByYear, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        assertEquals(400, resp.statusCode(), "DELETE /movies/{id} должен вернуть 400");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);

        assertEquals("Некорректный год", errorResponse.getError());
    }
}