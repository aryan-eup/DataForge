package util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// CONCEPT: Generics
// Generic in-memory repository providing type-safe storage and querying using Predicate<T>
public class Repository<T> {

    private final List<T> items = Collections.synchronizedList(new ArrayList<>());

    // CONCEPT: Generics - type-safe add
    public void add(T item) {
        if (item != null) {
            items.add(item);
        }
    }

    // CONCEPT: Generics & Lambda Expressions (Predicate<T>) & Stream API
    public List<T> findAll(Predicate<T> filter) {
        synchronized (items) {
            return items.stream()
                    .filter(filter)
                    .collect(Collectors.toList());
        }
    }

    // Return a shallow copy of all items
    public List<T> getAll() {
        synchronized (items) {
            return new ArrayList<>(items);
        }
    }

    public int size() {
        return items.size();
    }

    public void clear() {
        items.clear();
    }
}
