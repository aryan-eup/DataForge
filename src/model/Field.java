package model;

import rules.GenerationRule;

// Model representing a single column field in a schema definition
public class Field {

    private final String name;
    private final String type;
    private final GenerationRule rule;

    public Field(String name, String type, GenerationRule rule) {
        this.name = name;
        this.type = type;
        this.rule = rule;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public GenerationRule getRule() {
        return rule;
    }
}
