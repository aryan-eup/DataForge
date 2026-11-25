package rules;

import java.util.List;

// CONCEPT: Inheritance
// Generates category/department values chosen randomly from a provided list.
public class CategoryRule extends AbstractRule {

    private final List<String> categories;

    public CategoryRule(String columnName, List<String> categories) {
        super(columnName);
        this.categories = categories;
    }

    @Override
    public String generate() {
        return categories.get(random.nextInt(categories.size()));
    }
}
