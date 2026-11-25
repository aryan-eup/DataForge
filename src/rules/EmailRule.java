package rules;

import java.util.List;

// CONCEPT: Inheritance
// Generates realistic email addresses with randomized usernames and popular domains.
public class EmailRule extends AbstractRule {

    // CONCEPT: Collections (List)
    private static final List<String> USER_PREFIXES = List.of(
            "alex", "jordan", "taylor", "morgan", "sam", "chris", "pat", "casey",
            "riley", "dakota", "cameron", "devon", "jamie", "kendall", "quinn", "reese"
    );

    private static final List<String> DOMAINS = List.of(
            "example.com", "gmail.com", "yahoo.com", "outlook.com", "dataforge.io", "college.edu"
    );

    public EmailRule(String columnName) {
        super(columnName);
    }

    @Override
    public String generate() {
        String user = USER_PREFIXES.get(random.nextInt(USER_PREFIXES.size()));
        int num = 100 + random.nextInt(900);
        String domain = DOMAINS.get(random.nextInt(DOMAINS.size()));
        return user + num + "@" + domain;
    }
}
