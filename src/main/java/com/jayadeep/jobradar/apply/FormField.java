package com.jayadeep.jobradar.apply;

import java.util.List;

/**
 * One question on an application form, as found by {@code extract-fields.js}.
 *
 * @param id        value of the element's {@code data-jr-id} attribute
 * @param kind      text, email, tel, number, url, date, textarea, select, radio, checkbox,
 *                  checkboxGroup, combobox, buttons or file
 * @param label     the question text
 * @param options   choices for select/radio/checkboxGroup/combobox/buttons
 */
public record FormField(String id, String kind, String label, boolean required, Integer maxLength,
                        String value, List<Option> options) {

    public FormField {
        label = label == null ? "" : label;
        value = value == null ? "" : value;
        options = options == null ? List.of() : List.copyOf(options);
    }

    public boolean isChoice() {
        return switch (kind) {
            case "select", "radio", "checkboxGroup", "combobox", "buttons" -> true;
            default -> false;
        };
    }

    public FormField withOptions(List<Option> newOptions) {
        return new FormField(id, kind, label, required, maxLength, value, newOptions);
    }

    /** @param id the option's data-jr-id (radio, buttons), option value (select), or null (combobox) */
    public record Option(String id, String label) {
    }
}
