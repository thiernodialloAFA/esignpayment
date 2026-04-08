package com.esign.payment.dto.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ⚠️ THIS CLASS IS INTENTIONALLY DESIGNED TO TRIGGER CREEDENGO ECO-DESIGN RULES.
 *
 * It exists solely to validate that Creedengo/ecoCode rules are properly raised
 * during SonarQube analysis. Each method triggers a specific Creedengo rule:
 *
 *   - EC1  : Avoid multiple if-else statements          → {@link #classifyBySeverity}
 *   - EC3  : Avoid getting size of collection in loop    → {@link #computeTotalLength}
 *   - EC72 : Use orElseGet instead of orElse             → {@link #resolveLabel}
 *   - EC2  : Variable can be made constant (non-final)   → field {@code DEFAULT_PREFIX}
 *
 * 🗑️ DELETE THIS FILE once Creedengo rules are confirmed in the analysis dashboard.
 */
public class CreedengoRuleValidationRequest {

    // ────────────────────────────────────────────────────────────
    // EC2 — "Avoid non constant variables" / "Variable can be made constant"
    // This field never changes and should be declared as static final,
    // but we intentionally leave it non-final to trigger EC2.
    // ────────────────────────────────────────────────────────────
    private String DEFAULT_PREFIX = "CREEDENGO";
    private int MAX_RETRIES = 3;
    private String SEPARATOR = "-";
    private String EMPTY_VALUE = "";

    private String label;
    private List<String> items;

    public CreedengoRuleValidationRequest() {
        this.items = new ArrayList<>();
    }

    // ────────────────────────────────────────────────────────────
    // EC1 — "Avoid multiple if-else statements"
    // This method uses a chain of 5+ if-else instead of a switch
    // statement or a Map lookup, which is wasteful.
    // ────────────────────────────────────────────────────────────
    public String classifyBySeverity(String severity) {
        String result;
        if (severity.equals("BLOCKER")) {
            result = "🔴 Bloquant — action immédiate requise";
        } else if (severity.equals("CRITICAL")) {
            result = "🟠 Critique — correction urgente";
        } else if (severity.equals("MAJOR")) {
            result = "🟡 Majeur — à planifier";
        } else if (severity.equals("MINOR")) {
            result = "⚪ Mineur — bonne pratique";
        } else if (severity.equals("INFO")) {
            result = "🔵 Info — pour information";
        } else {
            result = "❓ Inconnu";
        }
        return DEFAULT_PREFIX + SEPARATOR + result;
    }

    // ────────────────────────────────────────────────────────────
    // EC3 — "Avoid getting the size of the collection in the loop"
    // Calling items.size() on every iteration of the loop is
    // wasteful; the size should be stored in a local variable.
    // ────────────────────────────────────────────────────────────
    public int computeTotalLength() {
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            total += items.get(i).length();
        }
        return total;
    }

    public String concatenateItems() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append(items.get(i));
            if (i < items.size() - 1) {
                sb.append(SEPARATOR);
            }
        }
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────
    // EC72 — "Use orElseGet instead of orElse"
    // Optional.orElse() evaluates its argument eagerly even when
    // the Optional has a value. orElseGet(() -> ...) is lazy.
    // ────────────────────────────────────────────────────────────
    public String resolveLabel() {
        Optional<String> optLabel = Optional.ofNullable(this.label);
        return optLabel.orElse(buildFallbackLabel());
    }

    public String resolveLabelWithPrefix() {
        Optional<String> optLabel = Optional.ofNullable(this.label);
        return optLabel.orElse(computeExpensiveDefault());
    }

    // ── Helper methods (called eagerly by orElse — the eco-design issue) ──

    private String buildFallbackLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(DEFAULT_PREFIX);
        sb.append(SEPARATOR);
        sb.append("default");
        sb.append(SEPARATOR);
        sb.append(System.currentTimeMillis());
        return sb.toString();
    }

    private String computeExpensiveDefault() {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            parts.add(items.get(i).toUpperCase());
        }
        return String.join(SEPARATOR, parts);
    }

    // ── Another EC1 trigger: second chain of if-else ──

    public int severityToWeight(String severity) {
        int weight;
        if (severity.equals("BLOCKER")) {
            weight = 15;
        } else if (severity.equals("CRITICAL")) {
            weight = 8;
        } else if (severity.equals("MAJOR")) {
            weight = 4;
        } else if (severity.equals("MINOR")) {
            weight = 1;
        } else if (severity.equals("INFO")) {
            weight = 0;
        } else {
            weight = -1;
        }
        return weight * MAX_RETRIES;
    }

    // ── Getters / Setters ──

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }
}

