package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa un componente visual detectado en la plantilla Orbeon Form Runner.
 */
public class ComponenteFormulario {

    private String id;
    private String tipo;
    private String label;
    private String hint;
    private String alert;
    private String appearance;
    private List<ItemSelect> items = new ArrayList<>();
    private Map<String, String> metadatos = new HashMap<>();

    public ComponenteFormulario() {
    }

    public ComponenteFormulario(String id, String tipo, String label, String hint) {
        this.id = id;
        this.tipo = tipo;
        this.label = label;
        this.hint = hint;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getAlert() {
        return alert;
    }

    public void setAlert(String alert) {
        this.alert = alert;
    }

    public String getAppearance() {
        return appearance;
    }

    public void setAppearance(String appearance) {
        this.appearance = appearance;
    }

    public List<ItemSelect> getItems() {
        return items;
    }

    public void setItems(List<ItemSelect> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public Map<String, String> getMetadatos() {
        return metadatos;
    }

    public void setMetadatos(Map<String, String> metadatos) {
        this.metadatos = metadatos != null ? metadatos : new HashMap<>();
    }
}
