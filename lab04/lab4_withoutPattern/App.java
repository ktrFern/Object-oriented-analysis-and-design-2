package org.example;

import javafx.application.Application;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class App extends Application {
    private static final String DB_URL = "jdbc:sqlite:terrarium.db";

    private final TableView<Animal> animalTable  = new TableView<>();
    private final ComboBox<Animal>  animalBox    = new ComboBox<>();
    private final Label             statusBar    = new Label("Животных в базе: 0");

    private YearMonth currentMonth = YearMonth.now();

    private GridPane          calendarGrid;
    private TableView<Event>  dayEventTable;
    private Label             dayLabel;
    private Label             monthLabel;

    private void initDatabase() {
        String createAnimals = """
                CREATE TABLE IF NOT EXISTS animals (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    name    TEXT NOT NULL,
                    species TEXT NOT NULL,
                    age     INTEGER,
                    gender  TEXT,
                    notes   TEXT
                );
                """;
        String createEvents = """
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
            stmt.execute(createAnimals);
            stmt.execute(createEvents);
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка инициализации БД", e);
        }
    }

    private void addAnimal(Animal animal) {
        String sql = "INSERT INTO animals (name, species, age, gender, notes) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, animal.getName());
            stmt.setString(2, animal.getSpecies());
            stmt.setInt   (3, animal.getAge());
            stmt.setString(4, animal.getGender());
            stmt.setString(5, animal.getNotes());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка добавления животного", e);
        }
    }

    private List<Animal> searchAnimals(String query) {
        List<Animal> list = new ArrayList<>();
        String sql = "SELECT * FROM animals WHERE lower(name) LIKE lower(?) OR lower(species) LIKE lower(?) ORDER BY name";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String p = "%" + query + "%";
            stmt.setString(1, p);
            stmt.setString(2, p);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapAnimal(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка поиска животных", e);
        }
        return list;
    }

    private List<Animal> getAllAnimals() {
        return searchAnimals("");
    }

    private int countAnimals() {
        String sql = "SELECT COUNT(*) FROM animals";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs  = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка подсчёта животных", e);
        }
        return 0;
    }

    private void updateAnimal(Animal animal) {
        String sql = "UPDATE animals SET name = ?, species = ?, age = ?, gender = ?, notes = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, animal.getName());
            stmt.setString(2, animal.getSpecies());
            stmt.setInt   (3, animal.getAge());
            stmt.setString(4, animal.getGender());
            stmt.setString(5, animal.getNotes());
            stmt.setInt   (6, animal.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка обновления животного", e);
        }
    }

    private void deleteAnimal(int id) {
        String delEvents = "DELETE FROM events WHERE animal_id = ?";
        String delAnimal  = "DELETE FROM animals WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (PreparedStatement s1 = conn.prepareStatement(delEvents);
                 PreparedStatement s2 = conn.prepareStatement(delAnimal)) {
                s1.setInt(1, id); s1.executeUpdate();
                s2.setInt(1, id); s2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка удаления животного", e);
        }
    }

    private Animal mapAnimal(ResultSet rs) throws SQLException {
        return new Animal(
                rs.getInt   ("id"),
                rs.getString("name"),
                rs.getString("species"),
                rs.getInt   ("age"),
                rs.getString("gender"),
                rs.getString("notes")
        );
    }

    private void addEvent(Event event) {
        String sql = "INSERT INTO events (animal_id, event_type, event_date, notes) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt   (1, event.getAnimalId());
            stmt.setString(2, event.getEventType());
            stmt.setString(3, event.getEventDate());
            stmt.setString(4, event.getNotes());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка добавления события", e);
        }
    }

    private List<Event> getEventsForDate(String date) {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM events WHERE event_date = ? ORDER BY event_type";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapEvent(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения событий по дате", e);
        }
        return list;
    }

    private List<Event> getEventsForMonth(String yearMonth) {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM events WHERE event_date LIKE ? ORDER BY event_date";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, yearMonth + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapEvent(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения событий по месяцу", e);
        }
        return list;
    }

    private int countEventsForAnimal(int animalId) {
        String sql = "SELECT COUNT(*) FROM events WHERE animal_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, animalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка подсчёта событий", e);
        }
        return 0;
    }

    private void deleteEvent(int id) {
        String sql = "DELETE FROM events WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка удаления события", e);
        }
    }

    private Event mapEvent(ResultSet rs) throws SQLException {
        return new Event(
                rs.getInt   ("id"),
                rs.getInt   ("animal_id"),
                rs.getString("event_type"),
                rs.getString("event_date"),
                rs.getString("notes")
        );
    }

    @Override
    public void start(Stage stage) {
        initDatabase();

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab animalsTab  = new Tab("Животные",   buildAnimalsTab());
        Tab calendarTab = new Tab("Планировщик", buildCalendarTab());
        tabs.getTabs().addAll(animalsTab, calendarTab);

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == calendarTab) refreshAnimalComboBox();
        });

        BorderPane root = new BorderPane();
        root.setCenter(tabs);
        root.setBottom(statusBar);
        BorderPane.setMargin(statusBar, new Insets(4, 8, 6, 8));

        stage.setTitle("Terrarium Manager");
        stage.setScene(new Scene(root, 980, 660));
        stage.show();

        refreshAnimalTable();
    }

    private VBox buildAnimalsTab() {
        TextField nameField    = new TextField(); nameField.setPromptText("Имя *");
        TextField speciesField = new TextField(); speciesField.setPromptText("Вид *");
        TextField ageField     = new TextField(); ageField.setPromptText("Возраст (лет) *");

        ComboBox<String> genderBox = new ComboBox<>();
        genderBox.getItems().addAll("Самец", "Самка", "Неизвестно");
        genderBox.setPromptText("Пол");
        genderBox.setPrefWidth(130);

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Заметки");
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        nameField.setPrefWidth(160);
        speciesField.setPrefWidth(160);
        ageField.setPrefWidth(120);

        TextField searchField = new TextField();
        searchField.setPromptText("Поиск по имени или виду...");
        searchField.textProperty().addListener((obs, old, val) ->
                animalTable.setItems(FXCollections.observableArrayList(searchAnimals(val)))
        );

        Button addBtn    = new Button("Добавить");
        Button updateBtn = new Button("Сохранить изменения");
        Button deleteBtn = new Button("Удалить");
        Button clearBtn  = new Button("Очистить");

        updateBtn.setDisable(true);
        deleteBtn.setDisable(true);

        TableColumn<Animal, String> nameCol = new TableColumn<>("Имя");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        nameCol.setPrefWidth(150);

        TableColumn<Animal, String> speciesCol = new TableColumn<>("Вид");
        speciesCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSpecies()));
        speciesCol.setPrefWidth(170);

        TableColumn<Animal, Integer> ageCol = new TableColumn<>("Возраст (лет)");
        ageCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getAge()).asObject());
        ageCol.setPrefWidth(110);

        TableColumn<Animal, String> genderCol = new TableColumn<>("Пол");
        genderCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGender()));
        genderCol.setPrefWidth(100);

        TableColumn<Animal, String> notesCol = new TableColumn<>("Заметки");
        notesCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNotes()));
        notesCol.setPrefWidth(200);

        animalTable.getColumns().addAll(nameCol, speciesCol, ageCol, genderCol, notesCol);
        animalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        animalTable.setPlaceholder(new Label("Нет животных. Добавьте первое!"));
        VBox.setVgrow(animalTable, Priority.ALWAYS);

        animalTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean has = sel != null;
            updateBtn.setDisable(!has);
            deleteBtn.setDisable(!has);
            if (has) {
                nameField.setText(sel.getName());
                speciesField.setText(sel.getSpecies());
                ageField.setText(String.valueOf(sel.getAge()));
                genderBox.setValue(sel.getGender());
                notesArea.setText(sel.getNotes());
            }
        });

        addBtn.setOnAction(e -> {
            if (!validateAnimal(nameField, speciesField, ageField)) return;
            addAnimal(new Animal(0,
                    nameField.getText().trim(),
                    speciesField.getText().trim(),
                    Integer.parseInt(ageField.getText().trim()),
                    genderBox.getValue() != null ? genderBox.getValue() : "Неизвестно",
                    notesArea.getText().trim()));
            refreshAnimalTable();
            clearAnimalForm(nameField, speciesField, ageField, genderBox, notesArea);
        });

        updateBtn.setOnAction(e -> {
            Animal sel = animalTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (!validateAnimal(nameField, speciesField, ageField)) return;
            sel.setName   (nameField.getText().trim());
            sel.setSpecies(speciesField.getText().trim());
            sel.setAge    (Integer.parseInt(ageField.getText().trim()));
            sel.setGender (genderBox.getValue() != null ? genderBox.getValue() : "Неизвестно");
            sel.setNotes  (notesArea.getText().trim());
            updateAnimal(sel);
            refreshAnimalTable();
            clearAnimalForm(nameField, speciesField, ageField, genderBox, notesArea);
        });

        deleteBtn.setOnAction(e -> {
            Animal sel = animalTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            int evCount = countEventsForAnimal(sel.getId());
            String msg = "Удалить «" + sel.getName() + "»?";
            if (evCount > 0) msg += "\nВместе с ним будут удалены " + evCount + " событий.";
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText(null);
            confirm.setContentText(msg);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    deleteAnimal(sel.getId());
                    refreshAnimalTable();
                    clearAnimalForm(nameField, speciesField, ageField, genderBox, notesArea);
                }
            });
        });

        clearBtn.setOnAction(e -> {
            animalTable.getSelectionModel().clearSelection();
            clearAnimalForm(nameField, speciesField, ageField, genderBox, notesArea);
        });

        HBox row1 = new HBox(8, nameField, speciesField, ageField, genderBox);
        HBox row2 = new HBox(8, notesArea);
        HBox btns = new HBox(8, addBtn, updateBtn, deleteBtn, clearBtn);

        VBox tab = new VBox(10, searchField, row1, row2, btns, animalTable);
        tab.setPadding(new Insets(12));
        return tab;
    }

    private SplitPane buildCalendarTab() {
        monthLabel = new Label();
        monthLabel.setFont(Font.font("System", FontWeight.BOLD, 15));

        Button prevBtn = new Button("◀");
        Button nextBtn = new Button("▶");
        prevBtn.setOnAction(e -> { currentMonth = currentMonth.minusMonths(1); refreshCalendar(); });
        nextBtn.setOnAction(e -> { currentMonth = currentMonth.plusMonths(1);  refreshCalendar(); });

        HBox navBar = new HBox(10, prevBtn, monthLabel, nextBtn);
        navBar.setAlignment(Pos.CENTER);

        calendarGrid = new GridPane();
        calendarGrid.setHgap(4);
        calendarGrid.setVgap(4);
        calendarGrid.setPadding(new Insets(8, 0, 0, 0));

        String[] dayNames = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (int i = 0; i < 7; i++) {
            Label lbl = new Label(dayNames[i]);
            lbl.setFont(Font.font("System", FontWeight.BOLD, 12));
            lbl.setMinWidth(58);
            lbl.setAlignment(Pos.CENTER);
            calendarGrid.add(lbl, i, 0);
        }

        VBox leftPane = new VBox(10, navBar, calendarGrid);
        leftPane.setPadding(new Insets(12));
        leftPane.setMinWidth(460);

        dayLabel = new Label("Выберите день");
        dayLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        animalBox.setPromptText("Животное");
        animalBox.setPrefWidth(180);

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Кормление", "Уборка", "Линька", "Ветеринар", "Взвешивание", "Другое");
        typeBox.setPromptText("Тип события *");
        typeBox.setPrefWidth(160);

        TextArea eventNotes = new TextArea();
        eventNotes.setPromptText("Заметки");
        eventNotes.setPrefRowCount(3);
        eventNotes.setWrapText(true);

        final LocalDate[] selectedDate = {LocalDate.now()};

        Button addEventBtn    = new Button("Добавить событие");
        Button deleteEventBtn = new Button("Удалить выбранное");
        deleteEventBtn.setDisable(true);

        dayEventTable = new TableView<>();

        TableColumn<Event, String> typeCol = new TableColumn<>("Тип");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEventType()));
        typeCol.setPrefWidth(120);

        TableColumn<Event, String> animalNameCol = new TableColumn<>("Животное");
        animalNameCol.setCellValueFactory(d -> new SimpleStringProperty(
                getAllAnimals().stream()
                        .filter(a -> a.getId() == d.getValue().getAnimalId())
                        .map(Animal::getName)
                        .findFirst().orElse("?")));
        animalNameCol.setPrefWidth(120);

        TableColumn<Event, String> eventNotesCol = new TableColumn<>("Заметки");
        eventNotesCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNotes()));
        eventNotesCol.setPrefWidth(160);

        dayEventTable.getColumns().addAll(typeCol, animalNameCol, eventNotesCol);
        dayEventTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        dayEventTable.setPlaceholder(new Label("Нет событий на этот день"));
        VBox.setVgrow(dayEventTable, Priority.ALWAYS);

        dayEventTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) ->
                deleteEventBtn.setDisable(sel == null)
        );

        addEventBtn.setOnAction(e -> {
            if (animalBox.getValue() == null) { showAlert("Выберите животное"); return; }
            if (typeBox.getValue()   == null) { showAlert("Выберите тип события"); return; }
            addEvent(new Event(0,
                    animalBox.getValue().getId(),
                    typeBox.getValue(),
                    selectedDate[0].toString(),
                    eventNotes.getText().trim()));
            eventNotes.clear();
            loadDayEvents(selectedDate[0]);
            refreshCalendar();
        });

        deleteEventBtn.setOnAction(e -> {
            Event sel = dayEventTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            deleteEvent(sel.getId());
            loadDayEvents(selectedDate[0]);
            refreshCalendar();
        });

        this.calendarDayClickHandler = date -> {
            selectedDate[0] = date;
            dayLabel.setText("События на " + date.format(
                    DateTimeFormatter.ofPattern("d MMMM yyyy", new java.util.Locale("ru"))));
            loadDayEvents(date);
        };

        HBox formRow1 = new HBox(8, animalBox, typeBox);
        HBox btnRow   = new HBox(8, addEventBtn, deleteEventBtn);

        VBox rightPane = new VBox(10, dayLabel, formRow1, eventNotes, btnRow, dayEventTable);
        rightPane.setPadding(new Insets(12));
        rightPane.setMinWidth(300);

        refreshCalendar();

        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.58);
        return split;
    }

    @FunctionalInterface
    interface DayClickHandler { void onClick(LocalDate date); }
    private DayClickHandler calendarDayClickHandler;

    private void refreshCalendar() {
        monthLabel.setText(currentMonth.format(
                DateTimeFormatter.ofPattern("LLLL yyyy", new java.util.Locale("ru"))));

        calendarGrid.getChildren().removeIf(n ->
                GridPane.getRowIndex(n) != null && GridPane.getRowIndex(n) > 0);

        List<Event> monthEvents = getEventsForMonth(currentMonth.toString());
        Map<String, List<Event>> byDate = monthEvents.stream()
                .collect(Collectors.groupingBy(Event::getEventDate));

        LocalDate first       = currentMonth.atDay(1);
        int       startCol    = first.getDayOfWeek().getValue() - 1;
        int       daysInMonth = currentMonth.lengthOfMonth();
        LocalDate today       = LocalDate.now();

        int col = startCol, row = 1;
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date   = currentMonth.atDay(day);
            List<Event> evs  = byDate.getOrDefault(date.toString(), List.of());

            VBox cell = new VBox(2);
            cell.setMinSize(58, 62);
            cell.setPrefSize(58, 62);
            cell.setPadding(new Insets(3));
            cell.setStyle(buildCellStyle(date, today, !evs.isEmpty()));
            cell.setAlignment(Pos.TOP_CENTER);

            Label dayNum = new Label(String.valueOf(day));
            dayNum.setFont(Font.font("System", FontWeight.BOLD, 12));
            cell.getChildren().add(dayNum);

            for (int i = 0; i < Math.min(evs.size(), 2); i++) {
                Label evLabel = new Label("• " + evs.get(i).getEventType());
                evLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #1a6b3c;");
                evLabel.setMaxWidth(54);
                cell.getChildren().add(evLabel);
            }
            if (evs.size() > 2) {
                Label more = new Label("+" + (evs.size() - 2) + " ещё");
                more.setStyle("-fx-font-size: 9px; -fx-text-fill: #888;");
                cell.getChildren().add(more);
            }

            final LocalDate clickDate = date;
            cell.setOnMouseClicked(e -> calendarDayClickHandler.onClick(clickDate));
            calendarGrid.add(cell, col, row);

            col++;
            if (col == 7) { col = 0; row++; }
        }

        for (int i = 0; i < 7; i++) {
            if (calendarGrid.getColumnConstraints().size() <= i) {
                calendarGrid.getColumnConstraints().add(new ColumnConstraints(62));
            }
        }
    }

    private String buildCellStyle(LocalDate date, LocalDate today, boolean hasEvents) {
        StringBuilder sb = new StringBuilder();
        sb.append("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
        if      (date.equals(today)) sb.append("-fx-background-color: #ddeeff;");
        else if (hasEvents)          sb.append("-fx-background-color: #f0fff4;");
        else                         sb.append("-fx-background-color: white;");
        int dow = date.getDayOfWeek().getValue();
        if (dow == 6 || dow == 7) sb.append("-fx-border-color: #bbbbbb;");
        return sb.toString();
    }

    private void loadDayEvents(LocalDate date) {
        dayEventTable.setItems(FXCollections.observableArrayList(getEventsForDate(date.toString())));
    }

    private void refreshAnimalTable() {
        animalTable.setItems(FXCollections.observableArrayList(getAllAnimals()));
        statusBar.setText("Животных в базе: " + countAnimals());
        refreshAnimalComboBox();
    }

    private void refreshAnimalComboBox() {
        Animal prev = animalBox.getValue();
        animalBox.setItems(FXCollections.observableArrayList(getAllAnimals()));
        if (prev != null) {
            animalBox.getItems().stream()
                    .filter(a -> a.getId() == prev.getId())
                    .findFirst()
                    .ifPresent(animalBox::setValue);
        }
    }

    private boolean validateAnimal(TextField name, TextField species, TextField age) {
        if (name.getText().trim().isEmpty() || species.getText().trim().isEmpty() || age.getText().trim().isEmpty()) {
            showAlert("Заполните обязательные поля: Имя, Вид, Возраст");
            return false;
        }
        try {
            int v = Integer.parseInt(age.getText().trim());
            if (v < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showAlert("Возраст должен быть положительным числом");
            return false;
        }
        return true;
    }

    private void clearAnimalForm(TextField name, TextField species, TextField age,
                                 ComboBox<String> gender, TextArea notes) {
        name.clear(); species.clear(); age.clear();
        gender.setValue(null); notes.clear();
    }

    private void showAlert(String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }
}