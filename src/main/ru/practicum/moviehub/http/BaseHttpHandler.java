package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected static final String CT_KEY = "Content-Type";
    protected final Gson gson;

    protected BaseHttpHandler(Gson gson) {
        this.gson = gson;
    }

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set(CT_KEY, CT_JSON);
        ex.sendResponseHeaders(status, json.getBytes(StandardCharsets.UTF_8).length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set(CT_KEY, CT_JSON);
        ex.sendResponseHeaders(204, -1);
        ex.getResponseBody().close();
    }

    protected void sendNotAllowed(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set(CT_KEY, CT_JSON);
        ex.sendResponseHeaders(405, -1);
    }

    protected void sendValidationError(HttpExchange ex, List<String> details) throws IOException {
        sendJson(ex, 422, createJsonError("Ошибка валидации", details));
    }

    protected void sendUnsupportedMediaTypeError(HttpExchange ex, List<String> details) throws IOException {
        sendJson(ex, 415, createJsonError("Неверное значение заголовка " + CT_KEY, details));
    }

    protected String createJsonError(String error, List<String> details) {
        ErrorResponse errorResponse = new ErrorResponse(error, details);
        return gson.toJson(errorResponse);
    }

    protected Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) {
            return new HashMap<>();
        }

        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank())
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
    }

    protected boolean isValidField(JsonObject jsonObject, String field) {
        return jsonObject.has(field) && !jsonObject.get(field).isJsonNull();
    }
}