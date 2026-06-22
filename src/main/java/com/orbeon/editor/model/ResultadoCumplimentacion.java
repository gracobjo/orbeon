package com.orbeon.editor.model;

import java.util.Collections;
import java.util.Map;

/**
 * XML con instancia cumplimentada y etiquetas legibles para desplegables (PDF).
 */
public class ResultadoCumplimentacion {

    private String xml;
    private Map<String, String> etiquetas = Collections.emptyMap();
    private int camposAplicados;

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public Map<String, String> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(Map<String, String> etiquetas) {
        this.etiquetas = etiquetas != null ? etiquetas : Collections.emptyMap();
    }

    public int getCamposAplicados() {
        return camposAplicados;
    }

    public void setCamposAplicados(int camposAplicados) {
        this.camposAplicados = camposAplicados;
    }
}
