package org.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AnimalGateway {

    private static final String DB_URL = "jdbc:sqlite:terrarium.db";

    public void initDatabase() {
        String sql = """
                CREATE TABLE IF NOT EXISTS animals (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    name    TEXT NOT NULL,
                    species TEXT NOT NULL,
                    age     INTEGER,
                    gender  TEXT,
                    notes   TEXT
                );
                """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка инициализации таблицы animals", e);
        }
    }

    public void addAnimal(Animal animal) {
        String sql = "INSERT INTO animals (name, species, age, gender, notes) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, animal.getName());
            stmt.setString(2, animal.getSpecies());
            stmt.setInt(3, animal.getAge());
            stmt.setString(4, animal.getGender());
            stmt.setString(5, animal.getNotes());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка добавления животного", e);
        }
    }

    public List<Animal> getAllAnimals() {
        return search("");
    }

    public List<Animal> search(String query) {
        List<Animal> list = new ArrayList<>();
        String sql = "SELECT * FROM animals WHERE lower(name) LIKE lower(?) OR lower(species) LIKE lower(?) ORDER BY name";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String p = "%" + query + "%";
            stmt.setString(1, p);
            stmt.setString(2, p);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка поиска животных", e);
        }
        return list;
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM animals";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка подсчёта животных", e);
        }
        return 0;
    }

    public void updateAnimal(Animal animal) {
        String sql = "UPDATE animals SET name = ?, species = ?, age = ?, gender = ?, notes = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, animal.getName());
            stmt.setString(2, animal.getSpecies());
            stmt.setInt(3, animal.getAge());
            stmt.setString(4, animal.getGender());
            stmt.setString(5, animal.getNotes());
            stmt.setInt(6, animal.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка обновления животного", e);
        }
    }

    public void deleteAnimal(int id) {
        String delEvents = "DELETE FROM events WHERE animal_id = ?";
        String delAnimal  = "DELETE FROM animals WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (PreparedStatement s1 = conn.prepareStatement(delEvents);
                 PreparedStatement s2 = conn.prepareStatement(delAnimal)) {
                s1.setInt(1, id);
                s1.executeUpdate();
                s2.setInt(1, id);
                s2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Шлюз: ошибка удаления животного", e);
        }
    }

    private Animal mapRow(ResultSet rs) throws SQLException {
        return new Animal(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("species"),
                rs.getInt("age"),
                rs.getString("gender"),
                rs.getString("notes")
        );
    }
}