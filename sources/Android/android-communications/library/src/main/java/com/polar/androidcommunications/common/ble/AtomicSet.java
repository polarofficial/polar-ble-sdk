package com.polar.androidcommunications.common.ble;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AtomicSet<E> {

    private final List<E> items = new ArrayList<>();

    public interface ObjectAccess<E> {
        void access(E object);
    }

    public interface CompareFunction<E> {
        boolean compare(E object);
    }

    public synchronized void clear() {
        items.clear();
    }

    public synchronized boolean add(E object) {
        if (object != null && !items.contains(object)) {
            items.add(object);
            return true;
        }
        return false;
    }

    public synchronized void remove(E object) {
        if (object != null) {
            items.remove(object);
        }
    }

    public synchronized void accessAll(ObjectAccess<E> objectAccess) {
        for (int i = items.size() - 1; i != -1; --i) {
            objectAccess.access(items.get(i));
        }
    }

    @Nullable
    public synchronized E fetch(CompareFunction<E> compareFunction) {
        for (int i = items.size() - 1; i != -1; --i) {
            if (compareFunction.compare(items.get(i))) {
                return items.get(i);
            }
        }
        return null;
    }

    public synchronized Set<E> objects() {
        return new HashSet<>(items);
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized boolean contains(E object) {
        return items.contains(object);
    }
}
