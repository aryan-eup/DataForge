package exception;

// CONCEPT: Custom Exception
// Custom checked exception thrown when schema validation fails (e.g., invalid row count or missing columns)
public class InvalidSchemaException extends Exception {
    public InvalidSchemaException(String message) {
        super(message);
    }

    public InvalidSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
