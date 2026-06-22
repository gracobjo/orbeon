package com.orbeon.editor.dto;

import java.util.Map;

public class CumplimentarInstanciaRequest {

    private String xml;
    /** Preset empaquetado, p. ej. {@code instrucciones-684}. */
    private String preset;
    /** Valores adicionales o sustitutos del preset. */
    private Map<String, String> valores;
    private Map<String, String> etiquetas;

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public Map<String, String> getValores() {
        return valores;
    }

    public void setValores(Map<String, String> valores) {
        this.valores = valores;
    }

    public Map<String, String> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(Map<String, String> etiquetas) {
        this.etiquetas = etiquetas;
    }
}
