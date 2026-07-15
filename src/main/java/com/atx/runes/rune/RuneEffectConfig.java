package com.atx.runes.rune;

import java.util.List;
import java.util.Map;

public final class RuneEffectConfig {
    private final String id;
    private final Map<String, Object> args;
    private final List<String> triggers;
    private final List<RuneMechanicStep> conditions;
    private final List<RuneMechanicStep> mutators;
    private final List<RuneMechanicStep> filters;

    public RuneEffectConfig(String id, Map<String, Object> args, List<String> triggers,
                            List<RuneMechanicStep> conditions, List<RuneMechanicStep> mutators,
                            List<RuneMechanicStep> filters) {
        this.id = id;
        this.args = Map.copyOf(args);
        this.triggers = List.copyOf(triggers);
        this.conditions = List.copyOf(conditions);
        this.mutators = List.copyOf(mutators);
        this.filters = List.copyOf(filters);
    }

    public String id() {
        return id;
    }

    public Map<String, Object> args() {
        return args;
    }

    public List<String> triggers() {
        return triggers;
    }

    public List<RuneMechanicStep> conditions() {
        return conditions;
    }

    public List<RuneMechanicStep> mutators() {
        return mutators;
    }

    public List<RuneMechanicStep> filters() {
        return filters;
    }

    public boolean permanent() {
        return triggers.isEmpty();
    }
}
