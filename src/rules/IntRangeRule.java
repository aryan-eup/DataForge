package rules;

// CONCEPT: Inheritance
// Generates integer numbers uniformly distributed in [min, max].
public class IntRangeRule extends AbstractRule {

    private final int min;
    private final int max;

    public IntRangeRule(String columnName, int min, int max) {
        super(columnName);
        this.min = min;
        this.max = max;
    }

    @Override
    public String generate() {
        int val = min + random.nextInt((max - min) + 1);
        return String.valueOf(val);
    }
}
