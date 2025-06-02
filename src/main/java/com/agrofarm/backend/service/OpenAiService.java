package com.agrofarm.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agrofarm.backend.AiChat.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class OpenAiService {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Value("${agro.api.key}")
    private String agroApiKey;

    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AgroMonitoringService agroMonitoringService;

    private final Map<String, List<ChatMessage>> conversationHistory = new ConcurrentHashMap<>();

    public String askWithSoilAndWeather(String polygonId, String userMessage) throws IOException {
        if (polygonId == null || polygonId.isBlank()) {
            return askChatOnly("anonymous", userMessage);
        }

        if (userMessage == null || userMessage.isBlank()) {
            userMessage = "Дай рекомендации по текущему состоянию поля";
        }

        return answerWithAgroData(polygonId, userMessage);
    }

    public String answerWithAgroData(String polygonId, String userMessage) throws IOException {
        String polygonsJson = agroMonitoringService.listPolygons();
        JsonNode polygons = mapper.readTree(polygonsJson);

        String polygonName = polygonId;
        for (JsonNode p : polygons) {
            if (polygonId.equals(p.path("id").asText())) {
                polygonName = p.has("name") ? p.get("name").asText() : polygonId;
                break;
            }
        }

        String soilText = formatSoilData(agroMonitoringService.getSoilData(polygonId));
        String weatherText = formatWeatherData(agroMonitoringService.getWeatherData(polygonId));

        String systemPrompt = String.format("""
            Ты — цифровой агроном. Помогаешь пользователю управлять полем.
            Полигон: '%s' (ID: %s)
            Данные почвы: %s
            Данные погоды: %s
            Отвечай просто и по делу.
            """, polygonName, polygonId, soilText, weatherText);

        return callOpenAiWithContext(polygonId, systemPrompt, userMessage);
    }

    public String askChatOnly(String sessionId, String userMessage) throws IOException {
        String systemPrompt = "Ты дружелюбный цифровой помощник. Общайся понятно и по-человечески.";
        return callOpenAiWithContext(sessionId, systemPrompt, userMessage);
    }

    public String getPolygonInfo(String polygonId) throws IOException {
        String apiUrl = "https://api.agromonitoring.com/agro/1.0/polygons/" + polygonId + "?appid=" + agroApiKey;
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
    
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            return "Ошибка получения данных полигона: HTTP " + responseCode;
        }
    
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
    
            JsonNode root = mapper.readTree(content.toString());
    
            String name = root.path("name").asText("Неизвестно");
            double area = root.path("area").asDouble(0.0);
            long createdUnix = root.path("created_at").asLong(0);
            String createdAt = createdUnix > 0 ? Instant.ofEpochSecond(createdUnix).toString() : "Неизвестно";
    
            return String.format(
                    "Название полигона: %s\nПлощадь: %.2f м²\nСоздан: %s",
                    name, area, createdAt
            );
        }
    }
    
    private String callOpenAiWithContext(String sessionId, String systemPrompt, String userMessage) throws IOException {
        List<ChatMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());

        ObjectNode requestJson = mapper.createObjectNode();
        requestJson.put("model", "gpt-4");
        ArrayNode messagesNode = requestJson.putArray("messages");

        messagesNode.add(createMessage("system", systemPrompt));
        for (ChatMessage msg : history) {
            messagesNode.add(createMessage(msg.getRole(), msg.getContent()));
        }
        messagesNode.add(createMessage("user", userMessage));

        Request request = new Request.Builder()
                .url(OPENAI_ENDPOINT)
                .addHeader("Authorization", "Bearer " + openAiApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestJson.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "Ошибка OpenAI: " + response.code() + " - " + (response.body() != null ? response.body().string() : "Пустой ответ");
            }

            String body = response.body().string();
            JsonNode jsonResponse = mapper.readTree(body);
            String reply = jsonResponse.path("choices").get(0).path("message").path("content").asText();

            history.add(new ChatMessage("user", userMessage));
            history.add(new ChatMessage("assistant", reply));

            return reply;
        }
    }

    private ObjectNode createMessage(String role, String content) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private String formatSoilData(String soilJson) throws IOException {
        JsonNode soil = mapper.readTree(soilJson);
        List<String> details = new ArrayList<>();

        if (soil.has("dt")) details.add("Дата: " + Instant.ofEpochSecond(soil.get("dt").asLong()));
        if (soil.has("t0")) details.add("Температура поверхности: " + soil.get("t0").asDouble() + " K");
        if (soil.has("t10")) details.add("Температура на 10 см: " + soil.get("t10").asDouble() + " K");
        if (soil.has("moisture")) details.add("Влажность почвы: " + soil.get("moisture").asDouble());

        return String.join(", ", details);
    }

    private String formatWeatherData(String weatherJson) throws IOException {
        JsonNode weather = mapper.readTree(weatherJson);
        List<String> details = new ArrayList<>();

        if (weather.has("dt")) details.add("Дата: " + Instant.ofEpochSecond(weather.get("dt").asLong()));

        JsonNode descNode = weather.path("weather");
        if (descNode.isArray() && descNode.size() > 0) {
            details.add("Погода: " + descNode.get(0).path("description").asText());
        }

        JsonNode main = weather.path("main");
        if (main.has("temp")) details.add("Температура: " + main.get("temp").asDouble() + " K");
        if (main.has("humidity")) details.add("Влажность: " + main.get("humidity").asInt() + "%");
        if (main.has("pressure")) details.add("Давление: " + main.get("pressure").asInt() + " гПа");

        JsonNode wind = weather.path("wind");
        if (wind.has("speed")) details.add("Ветер: " + wind.get("speed").asDouble() + " м/с");

        return String.join(", ", details);
    }

    private String callOpenAiApi(String prompt) {
        try {
            ObjectNode requestJson = mapper.createObjectNode();
            requestJson.put("model", "gpt-4");
    
            ArrayNode messagesNode = requestJson.putArray("messages");
            // Один системный запрос, который задаёт стиль и цель
            messagesNode.add(createMessage("system", "Ты — цифровой агроном. Отвечай просто и кратко."));
            // Пользовательский запрос — это наш prompt
            messagesNode.add(createMessage("user", prompt));
    
            Request request = new Request.Builder()
                    .url(OPENAI_ENDPOINT)
                    .addHeader("Authorization", "Bearer " + openAiApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestJson.toString(), MediaType.parse("application/json")))
                    .build();
    
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "Ошибка OpenAI: " + response.code() + " - " + (response.body() != null ? response.body().string() : "Пустой ответ");
                }
    
                String body = response.body().string();
                JsonNode jsonResponse = mapper.readTree(body);
                return jsonResponse.path("choices").get(0).path("message").path("content").asText();
            }
        } catch (IOException e) {
            return "Ошибка вызова OpenAI: " + e.getMessage();
        }
    }
    
    public String getAgronomicAdvice(String agroData) {
        String prompt = "На основе следующих данных о полигоне дай очень краткую статистику по ключевым параметрам: температура почвы, влажность почвы, температура воздуха, влажность воздуха, давление и скорость ветра. " +
                        "В конце добавь 2-3 короткие рекомендации для агронома. " +
                        "Пожалуйста, используй простой и лаконичный стиль.\n" + agroData;
    
        // вызов OpenAI с этим prompt
        return callOpenAiApi(prompt);
    }
    

    public Map<String, String> getPolygonsMap() throws IOException {
        String polygonsJson = agroMonitoringService.listPolygons();
        JsonNode polygons = mapper.readTree(polygonsJson);

        Map<String, String> map = new HashMap<>();
        if (polygons.isArray()) {
            for (JsonNode p : polygons) {
                String id = p.get("id").asText();
                String name = p.has("name") ? p.get("name").asText() : id;
                map.put(id, name);
            }
        }
        return map;
    }
}



