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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GatewayApp extends Application {

    private final AnimalGateway animalGateway = new AnimalGateway();
    private final EventGateway eventGateway = new EventGateway();

    private final TableView<Animal> animalTable = new TableView<>();
    private final ComboBox<Animal> animalBox = new ComboBox<>();

    private final Label statusBar = new Label("Животных в базе: 0");

    private YearMonth currentMonth = YearMonth.now();

    private GridPane calendarGrid;
    private TableView<Event> dayEventTable;
    private Label dayLabel;
    private Label monthLabel;

    @Override
    public void start(Stage stage) {
        animalGateway.initDatabase();
        eventGateway.initDatabase();

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab animalsTab = new Tab("Животные", buildAnimalsTab());
        Tab calendarTab = new Tab("Планировщик", buildCalendarTab());

        tabs.getTabs().addAll(animalsTab, calendarTab);

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == calendarTab) {
                refreshAnimalComboBox();
            }
        });

        BorderPane root = new BorderPane();
        root.setCenter(tabs);
        root.setBottom(statusBar);
        BorderPane.setMargin(statusBar, new Insets(4, 8, 6, 8));

        Scene scene = new Scene(root, 980, 660);

        stage.setTitle("Terrarium Manager");
        stage.setScene(scene);
        stage.show();

        refreshAnimalTable();
    }

    private VBox buildAnimalsTab() {

        TextField nameField = new TextField();
        nameField.setPromptText("Имя *");

        TextField speciesField = new TextField();
        speciesField.setPromptText("Вид *");

        TextField ageField = new TextField();
        ageField.setPromptText("Возраст (лет) *");

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
                animalTable.setItems(FXCollections.observableArrayList(animalGateway.search(val)))
        );

        Button addBtn = new Button("Добавить");
        Button updateBtn = new Button("Сохранить изменения");
        Button deleteBtn = new Button("Удалить");
        Button clearBtn = new Button("Очистить");

        updateBtn.setDisable(true);
        deleteBtn.setDisable(true);

        TableColumn<Animal, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getId()).asObject());
        idCol.setPrefWidth(45);

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
            animalGateway.addAnimal(new Animal(0, nameField.getText().trim(), speciesField.getText().trim(), Integer.parseInt(ageField.getText().trim()), genderBox.getValue() != null ? genderBox.getValue() : "Неизвестно", notesArea.getText().trim()));
            refreshAnimalTable();
            clearAnimalForm(nameField, speciesField, ageField, genderBox, notesArea);
        });

        updateBtn.setOnAction(e -> {
            Animal sel = animalTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (!validateAnimal(nameField, speciesField, ageField)) return;
            sel.setName(nameField.getText().trim());
            sel.setSpecies(speciesField.getText().trim());
            sel.setAge(Integer.parseInt(ageField.getText().trim()));
            sel.setGender(genderBox.getValue() != null ? genderBox.getValue() : "Неизвестно");
            sel.setNotes(notesArea.getText().trim());
            animalGateway.updateAnimal(sel);
            refreshAnimalTable();
            clearAnimalForm(nameField, speciesField, ageField, genderBox, notesArea);
        });

        deleteBtn.setOnAction(e -> {
            Animal sel = animalTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            int evCount = eventGateway.countEventsForAnimal(sel.getId());
            String msg = "Удалить «" + sel.getName() + "»?";
            if (evCount > 0) {
                msg += "\nВместе с ним будут удалены " + evCount + " событий.";
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText(null);
            confirm.setContentText(msg);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    animalGateway.deleteAnimal(sel.getId());
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
        nextBtn.setOnAction(e -> { currentMonth = currentMonth.plusMonths(1); refreshCalendar(); });

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

        Button addEventBtn = new Button("Добавить событие");
        Button deleteEventBtn = new Button("Удалить выбранное");
        deleteEventBtn.setDisable(true);

        dayEventTable = new TableView<>();

        TableColumn<Event, String> typeCol = new TableColumn<>("Тип");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEventType()));
        typeCol.setPrefWidth(120);

        TableColumn<Event, String> animalNameCol = new TableColumn<>("Животное");
        animalNameCol.setCellValueFactory(d -> new SimpleStringProperty(animalGateway.getAllAnimals().stream().filter(a -> a.getId() == d.getValue().getAnimalId()).map(Animal::getName).findFirst().orElse("?")));
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
            if (typeBox.getValue() == null) { showAlert("Выберите тип события"); return; }
            eventGateway.addEvent(new Event(0, animalBox.getValue().getId(), typeBox.getValue(), selectedDate[0].toString(), eventNotes.getText().trim()));
            eventNotes.clear();
            loadDayEvents(selectedDate[0]);
            refreshCalendar();
        });

        deleteEventBtn.setOnAction(e -> {
            Event sel = dayEventTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            eventGateway.deleteEvent(sel.getId());
            loadDayEvents(selectedDate[0]);
            refreshCalendar();
        });

        this.calendarDayClickHandler = date -> {
            selectedDate[0] = date;
            dayLabel.setText("События на " + date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", new java.util.Locale("ru"))));
            loadDayEvents(date);
        };

        HBox formRow1 = new HBox(8, animalBox, typeBox);
        HBox btnRow = new HBox(8, addEventBtn, deleteEventBtn);

        VBox rightPane = new VBox(10, dayLabel, formRow1, eventNotes, btnRow, dayEventTable);
        rightPane.setPadding(new Insets(12));
        rightPane.setMinWidth(300);

        refreshCalendar();

        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.58);
        return split;
    }

    @FunctionalInterface
    interface DayClickHandler {
        void onClick(LocalDate date);
    }

    private DayClickHandler calendarDayClickHandler;

    private void refreshCalendar() {
        monthLabel.setText(currentMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", new java.util.Locale("ru"))));

        calendarGrid.getChildren().removeIf(n ->
                GridPane.getRowIndex(n) != null && GridPane.getRowIndex(n) > 0
        );

        String yearMonth = currentMonth.toString();
        List<Event> monthEvents = eventGateway.getEventsForMonth(yearMonth);
        Map<String, List<Event>> byDate = monthEvents.stream().collect(Collectors.groupingBy(Event::getEventDate));

        LocalDate first = currentMonth.atDay(1);
        int startCol = first.getDayOfWeek().getValue() - 1;
        int daysInMonth = currentMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        int col = startCol, row = 1;

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            String dateStr = date.toString();
            List<Event> events = byDate.getOrDefault(dateStr, List.of());

            VBox cell = new VBox(2);
            cell.setMinSize(58, 62);
            cell.setPrefSize(58, 62);
            cell.setPadding(new Insets(3));
            cell.setStyle(buildCellStyle(date, today, !events.isEmpty()));
            cell.setAlignment(Pos.TOP_CENTER);

            Label dayNum = new Label(String.valueOf(day));
            dayNum.setFont(Font.font("System", FontWeight.BOLD, 12));
            cell.getChildren().add(dayNum);

            for (int i = 0; i < Math.min(events.size(), 2); i++) {
                Label evLabel = new Label("• " + events.get(i).getEventType());
                evLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #1a6b3c;");
                evLabel.setMaxWidth(54);
                cell.getChildren().add(evLabel);
            }

            if (events.size() > 2) {
                Label more = new Label("+" + (events.size() - 2) + " ещё");
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
                ColumnConstraints cc = new ColumnConstraints(62);
                calendarGrid.getColumnConstraints().add(cc);
            }
        }
    }

    private String buildCellStyle(LocalDate date, LocalDate today, boolean hasEvents) {
        StringBuilder sb = new StringBuilder();
        sb.append("-fx-border-color: #cccccc; ");
        sb.append("-fx-border-radius: 4; ");
        sb.append("-fx-background-radius: 4; ");
        sb.append("-fx-cursor: hand;");
        if (date.equals(today)) {
            sb.append("-fx-background-color: #ddeeff;");
        } else if (hasEvents) {
            sb.append("-fx-background-color: #f0fff4;");
        } else {
            sb.append("-fx-background-color: white;");
        }
        int dow = date.getDayOfWeek().getValue();
        if (dow == 6 || dow == 7) sb.append("-fx-border-color: #bbbbbb;");
        return sb.toString();
    }

    private void loadDayEvents(LocalDate date) {
        dayEventTable.setItems(FXCollections.observableArrayList(eventGateway.getEventsForDate(date.toString())));
    }

    private void refreshAnimalTable() {
        animalTable.setItems(FXCollections.observableArrayList(animalGateway.getAllAnimals()));
        statusBar.setText("Животных в базе: " + animalGateway.countAll());
        refreshAnimalComboBox();
    }

    private void refreshAnimalComboBox() {
        Animal prev = animalBox.getValue();
        animalBox.setItems(FXCollections.observableArrayList(animalGateway.getAllAnimals()));
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

    private void clearAnimalForm(TextField name, TextField species, TextField age, ComboBox<String> gender, TextArea notes) {
        name.clear();
        species.clear();
        age.clear();
        gender.setValue(null);
        notes.clear();
    }

    private void showAlert(String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }
}