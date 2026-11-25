package rules;

import java.time.LocalDate;

// CONCEPT: Inheritance
// Generates dates within a year span formatted as YYYY-MM-DD.
public class DateRule extends AbstractRule {

    private final int startYear;
    private final int endYear;

    public DateRule(String columnName, int startYear, int endYear) {
        super(columnName);
        this.startYear = startYear;
        this.endYear = endYear;
    }

    @Override
    public String generate() {
        int year = startYear + random.nextInt((endYear - startYear) + 1);
        int dayOfYear = 1 + random.nextInt(365);
        LocalDate date = LocalDate.ofYearDay(year, dayOfYear);
        return date.toString();
    }
}
