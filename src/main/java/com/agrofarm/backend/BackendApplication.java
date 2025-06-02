package com.agrofarm.backend;

import java.util.Scanner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.agrofarm.backend.service.AgroMonitoringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }

  @Bean
    public CommandLineRunner run(AgroMonitoringService agroService) {
        return args -> {
            try {
                // Получаем список полигонов в JSON
                String polygonsJson = agroService.listPolygons();

                // Парсим JSON
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(polygonsJson);

                if (root.isEmpty()) {
                    System.out.println("Список полигонов пуст.");
                    return;
                }

                // Выводим список полигонов с индексами
                System.out.println("Список полигонов:");
                int index = 1;
                for (JsonNode polygon : root) {
                    String id = polygon.path("id").asText();
                    String name = polygon.path("name").asText();
                    System.out.println(index + ". Название: " + name + ", ID: " + id);
                    index++;
                }

                // Запрашиваем у пользователя выбор
                System.out.println("Введите номер полигона для получения агроданных:");
                Scanner scanner = new Scanner(System.in);
                int choice = scanner.nextInt();

                if (choice < 1 || choice > root.size()) {
                    System.out.println("Неверный номер полигона.");
                    return;
                }

                // Берем polygonId выбранного полигона
                JsonNode selectedPolygon = root.get(choice - 1);
                String polygonId = selectedPolygon.path("id").asText();

                // Получаем агроданные по polygonId
                String result = agroService.getAgroDataSafe(polygonId);

                System.out.println("Агрономические данные для полигона " + polygonId + ":");
                System.out.println(result);

            } catch (Exception e) {
                System.err.println("Ошибка при выполнении: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}