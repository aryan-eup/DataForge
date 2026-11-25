package rules;

// CONCEPT: Interface
// The common contract for all data generation rules in the system.
public interface GenerationRule {
    // Generates a single fake data value as a String
    String generate();

    // Returns the column name associated with this rule
    String columnName();
}
