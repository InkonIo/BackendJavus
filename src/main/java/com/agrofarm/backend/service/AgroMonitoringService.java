package com.agrofarm.backend.service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class AgroMonitoringService {

    private final String apiKey = "e38bbe663df68e5f56afbc969a1b9176";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    // Получить список полигонов
    public String listPolygons() throws IOException {
        String url = "https://api.agromonitoring.com/agro/1.0/polygons?appid=" + apiKey;
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ошибка получения полигонов: " + response.code());
            }
            return response.body().string();
        }
    }

    // Получить данные погоды для полигона
    public String getWeatherData(String polygonId) throws IOException {
        String url = "https://api.agromonitoring.com/agro/1.0/weather?polyid=" + polygonId + "&appid=" + apiKey;
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ошибка получения погоды: " + response.code());
            }
            return response.body().string();
        }
    }

    // Получить данные почвы для полигона
    public String getSoilData(String polygonId) throws IOException {
        String url = "https://api.agromonitoring.com/agro/1.0/soil?polyid=" + polygonId + "&appid=" + apiKey;
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ошибка получения данных почвы: " + response.code());
            }
            return response.body().string();
        }
    }

    // Для дебага: выводит список полигонов с ID и именами
    public void printPolygonNamesAndIds() throws IOException {
        String polygonsJson = listPolygons();
        JsonNode root = mapper.readTree(polygonsJson);

        System.out.println("Список полигонов:");
        for (JsonNode polygon : root) {
            String id = polygon.path("id").asText();
            String name = polygon.path("name").asText();
            System.out.println("Название: " + name + ", ID: " + id);
        }
    }

    // Безопасное получение и форматирование данных по полигону
    public String getAgroDataSafe(String polygonId) {
        String soilJson = "{}";
        String weatherJson = "{}";

        try {
            soilJson = getSoilData(polygonId);
        } catch (IOException e) {
            if (!e.getMessage().contains("404")) {
                e.printStackTrace();
            }
        }

        try {
            weatherJson = getWeatherData(polygonId);
        } catch (IOException e) {
            if (!e.getMessage().contains("404")) {
                e.printStackTrace();
            }
        }

        // Форматируем JSON в удобочитаемый вид
        String soilInfo = formatSoilData(soilJson);
        String weatherInfo = formatWeatherData(weatherJson);

        return "Данные почвы: " + soilInfo + "\n" +
               "Данные погоды: " + weatherInfo;
    }

    // Метод форматирования почвенных данных — просто красиво печатаем JSON
    private String formatSoilData(String soilJson) {
        try {
            Object json = mapper.readValue(soilJson, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return "Ошибка форматирования почвенных данных";
        }
    }

    // Метод форматирования погодных данных — просто красиво печатаем JSON
    private String formatWeatherData(String weatherJson) {
        try {
            Object json = mapper.readValue(weatherJson, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return "Ошибка форматирования погодных данных";
        }
    }
}
