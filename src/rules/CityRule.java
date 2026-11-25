package rules;

import java.util.List;

// CONCEPT: Inheritance
// Generates major global city names.
public class CityRule extends AbstractRule {

    // CONCEPT: Collections (List)
    private static final List<String> CITIES = List.of(
            "New York", "San Francisco", "Chicago", "Boston", "Austin", "Seattle",
            "London", "Toronto", "Sydney", "Berlin", "Tokyo", "Mumbai", "Singapore",
            "Denver", "Atlanta", "Miami", "San Diego", "Portland", "Dallas", "Phoenix"
    );

    public CityRule(String columnName) {
        super(columnName);
    }

    @Override
    public String generate() {
        return CITIES.get(random.nextInt(CITIES.size()));
    }
}
