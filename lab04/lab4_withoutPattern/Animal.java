package org.example;

public class Animal {
    private int id;
    private String name;
    private String species;
    private int age;
    private String gender;
    private String notes;

    public Animal(int id, String name, String species, int age, String gender, String notes) {
        this.id = id;
        this.name = name;
        this.species = species;
        this.age = age;
        this.gender = gender;
        this.notes = notes;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getSpecies() { return species; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public String getNotes() { return notes; }

    public void setName(String name) { this.name = name; }
    public void setSpecies(String s) { this.species = s; }
    public void setAge(int age) { this.age = age; }
    public void setGender(String g) { this.gender = g; }
    public void setNotes(String n) { this.notes = n; }

    @Override
    public String toString() { return name + " (" + species + ")"; }
}