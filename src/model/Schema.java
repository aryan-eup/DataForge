package model;

import rules.*;

import java.util.*;

// CONCEPT: Collections (List, Map)
// Schema defines the available fields and rule factories for standard domain schemas.
public class Schema {

    private final String name;
    private final List<Field> fields;

    public Schema(String name, List<Field> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public List<Field> getFields() {
        return Collections.unmodifiableList(fields);
    }

    // Factory method to obtain predefined schemas: Students, Employees, Products
    public static Map<String, Schema> getPredefinedSchemas() {
        Map<String, Schema> schemas = new LinkedHashMap<>();

        // 1. Students Schema
        List<Field> studentFields = List.of(
                new Field("id", "INTEGER", new IntRangeRule("id", 1000, 9999)),
                new Field("name", "STRING", new NameRule("name")),
                new Field("email", "STRING", new EmailRule("email")),
                new Field("age", "INTEGER", new IntRangeRule("age", 18, 26)),
                new Field("phone", "STRING", new PhoneRule("phone")),
                new Field("city", "STRING", new CityRule("city")),
                new Field("enrollment_date", "DATE", new DateRule("enrollment_date", 2021, 2026)),
                new Field("gpa", "INTEGER", new IntRangeRule("gpa", 60, 100)) // Scaled GPA / Score out of 100
        );
        schemas.put("students", new Schema("students", studentFields));

        // 2. Employees Schema
        List<Field> employeeFields = List.of(
                new Field("id", "INTEGER", new IntRangeRule("id", 10000, 99999)),
                new Field("name", "STRING", new NameRule("name")),
                new Field("email", "STRING", new EmailRule("email")),
                new Field("department", "STRING", new CategoryRule("department",
                        List.of("Engineering", "Human Resources", "Marketing", "Finance", "Sales", "Operations"))),
                new Field("salary", "INTEGER", new IntRangeRule("salary", 45000, 140000)),
                new Field("phone", "STRING", new PhoneRule("phone")),
                new Field("city", "STRING", new CityRule("city")),
                new Field("hire_date", "DATE", new DateRule("hire_date", 2018, 2026))
        );
        schemas.put("employees", new Schema("employees", employeeFields));

        // 3. Products Schema
        List<Field> productFields = List.of(
                new Field("id", "INTEGER", new IntRangeRule("id", 500, 5000)),
                new Field("product_name", "STRING", new CategoryRule("product_name",
                        List.of("Quantum Laptop", "Echo Headset", "Aero Keyboard", "Pixel Monitor", "Nova Mouse",
                                "HyperDrive SSD", "Core Processor", "SoundWave Speaker", "PowerGrid Hub", "AirFlow Stand"))),
                new Field("category", "STRING", new CategoryRule("category",
                        List.of("Electronics", "Accessories", "Audio", "Hardware", "Peripherals"))),
                new Field("price", "INTEGER", new IntRangeRule("price", 15, 1200)),
                new Field("stock", "INTEGER", new IntRangeRule("stock", 0, 500)),
                new Field("sku", "INTEGER", new IntRangeRule("sku", 100000, 999999)),
                new Field("created_date", "DATE", new DateRule("created_date", 2022, 2026))
        );
        schemas.put("products", new Schema("products", productFields));

        return schemas;
    }
}
