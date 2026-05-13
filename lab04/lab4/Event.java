package org.example;

public class Event {
    private int id;
    private int animalId;
    private String eventType;
    private String eventDate;
    private String notes;

    public Event(int id, int animalId, String eventType, String eventDate, String notes) {
        this.id = id;
        this.animalId = animalId;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.notes = notes;
    }

    public int getId() { return id; }
    public int getAnimalId() { return animalId; }
    public String getEventType() { return eventType; }
    public String getEventDate() { return eventDate; }
    public String getNotes() { return notes; }
}