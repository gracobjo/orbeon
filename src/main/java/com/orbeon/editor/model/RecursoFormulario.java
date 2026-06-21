package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursos de un campo en fr-form-resources (label, hint, alert e items de selects).
 */
public class RecursoFormulario {

    private String label = "";
    private String hint = "";
    private String alert = "";
    private List<ItemSelect> items = new ArrayList<>();

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint != null ? hint : "";
    }

    public String getAlert() {
        return alert;
    }

    public void setAlert(String alert) {
        this.alert = alert != null ? alert : "";
    }

    public List<ItemSelect> getItems() {
        return items;
    }

    public void setItems(List<ItemSelect> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
