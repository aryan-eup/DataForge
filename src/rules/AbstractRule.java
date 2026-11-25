package rules;

import java.util.Random;

// CONCEPT: Abstract Class & Inheritance
// Provides common state (columnName) and shared utility (Random instance) for all rule implementations.
public abstract class AbstractRule implements GenerationRule {

    protected final String columnName;
    protected static final Random random = new Random();

    public AbstractRule(String columnName) {
        this.columnName = columnName;
    }

    @Override
    public String columnName() {
        return this.columnName;
    }

    // CONCEPT: Abstraction
    // Subclasses must implement generate() to provide domain-specific generation logic.
    @Override
    public abstract String generate();
}
