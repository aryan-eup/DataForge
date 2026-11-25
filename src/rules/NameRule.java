package rules;

import java.util.List;

// CONCEPT: Inheritance
// Generates realistic full names using predefined first and last name collections.
public class NameRule extends AbstractRule {

    // CONCEPT: Collections (List)
    private static final List<String> FIRST_NAMES = List.of(
            "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
            "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
            "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Nancy", "Daniel", "Lisa",
            "Matthew", "Margaret", "Anthony", "Betty", "Donald", "Sandra", "Alex", "Emma"
    );

    private static final List<String> LAST_NAMES = List.of(
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
            "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White"
    );

    public NameRule(String columnName) {
        super(columnName);
    }

    @Override
    public String generate() {
        String first = FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size()));
        String last = LAST_NAMES.get(random.nextInt(LAST_NAMES.size()));
        return first + " " + last;
    }
}
