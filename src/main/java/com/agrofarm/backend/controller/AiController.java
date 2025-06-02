package com.agrofarm.backend.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agrofarm.backend.dto.ChatRequest;
import com.agrofarm.backend.service.AgroMonitoringService;
import com.agrofarm.backend.service.OpenAiService;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:5173")  // или адрес твоего фронтенда
public class AiController {

    private final AgroMonitoringService agroMonitoringService;
    private final OpenAiService openAiService;

    public AiController(AgroMonitoringService agroMonitoringService, OpenAiService openAiService) {
        this.agroMonitoringService = agroMonitoringService;
        this.openAiService = openAiService;
    }

    @GetMapping("/polygons")
    public Map<String, String> getPolygons() throws IOException {
        return openAiService.getPolygonsMap();
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        try {
            String answer = openAiService.askWithSoilAndWeather(request.getPolygonId(), request.getMessage());
            return Map.of("advice", answer);
        } catch (Exception e) {
            return Map.of("error", "Ошибка: " + e.getMessage());
        }
    }

    @PostMapping("/polygon-info")
    public ResponseEntity<?> getPolygonInfo(@RequestBody Map<String, String> request) throws IOException {
        String polygonId = request.get("polygonId");
        if (polygonId == null || polygonId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "polygonId is required"));
        }

        String agroData = agroMonitoringService.getAgroDataSafe(polygonId);

        String prompt = "Дай агрономический анализ и рекомендации по следующей информации:\n" + agroData;

        String aiResponse = openAiService.getAgronomicAdvice(prompt);

        return ResponseEntity.ok(Map.of("advice", aiResponse));
    }
}
