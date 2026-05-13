package org.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventGateway {

    private static final String DB_URL = "jdbc:sqlite:terrarium.db";

    public void initDatabase() {
        String sql = """
                CREATE TABLE IF NOT EXISTS events (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    animal_id   INTEGER NOT NULL,
                    event_type  TEXT NOT NULL,
                    event_date  TEXT NOT NULL,
                    notes       TEXT,
                    FOREIGN KEY (animal_id) REFERENCES animals(id)
                );
                """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка инициализации таблицы events", e);
        }
    }

    public void addEvent(Event event) {
        String sql = "INSERT INTO events (animal_id, event_type, event_date, notes) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, event.getAnimalId());
            stmt.setString(2, event.getEventType());
            stmt.setString(3, event.getEventDate());
            stmt.setString(4, event.getNotes());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка добавления события", e);
        }
    }

    public List<Event> getEventsForAnimal(int animalId) {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM events WHERE animal_id = ? ORDER BY event_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, animalId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка получения событий по животному", e);
        }
        return list;
    }

    public List<Event> getEventsForDate(String date) {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM events WHERE event_date = ? ORDER BY event_type";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка получения событий по дате", e);
        }
        return list;
    }

    public List<Event> getEventsForMonth(String yearMonth) {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM events WHERE event_date LIKE ? ORDER BY event_date";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, yearMonth + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка получения событий по месяцу", e);
        }
        return list;
    }

    public int countEventsForAnimal(int animalId) {
        String sql = "SELECT COUNT(*) FROM events WHERE animal_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, animalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка подсчёта событий", e);
        }
        return 0;
    }

    public void deleteEvent(int id) {
        String sql = "DELETE FROM events WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка удаления события", e);
        }
    }

    private Event mapRow(ResultSet rs) throws SQLException {
        return new Event(
                rs.getInt("id"),
                rs.getInt("animal_id"),
                rs.getString("event_type"),
                rs.getString("event_date"),
                rs.getString("notes")
        );
    }
}