package rules;

// CONCEPT: Inheritance
// Generates realistic North American phone numbers formatted as +1-XXX-XXX-XXXX.
public class PhoneRule extends AbstractRule {

    public PhoneRule(String columnName) {
        super(columnName);
    }

    @Override
    public String generate() {
        int areaCode = 200 + random.nextInt(800);
        int central = 200 + random.nextInt(800);
        int line = 1000 + random.nextInt(9000);
        return String.format("+1-%03d-%03d-%04d", areaCode, central, line);
    }
}
