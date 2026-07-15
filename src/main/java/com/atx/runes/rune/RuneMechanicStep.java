package com.atx.runes.rune;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuneMechanicStep {
    private final String id;
    private final Map<String, Object> data;

    public RuneMechanicStep(String id, Map<String, Object> data) {
        this.id = id;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public String id() {
        return id;
    }

    public Map<String, Object> data() {
        return data;
    }
}
