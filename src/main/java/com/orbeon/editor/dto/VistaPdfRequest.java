package com.orbeon.editor.dto;

import com.orbeon.editor.model.ComponenteFormulario;

import java.util.List;
import java.util.Map;

public class VistaPdfRequest {

    private String xml;
    private List<ComponenteFormulario> componentes;
    /** Si true, aplica el preset instrucciones-684 antes de generar. */
    private boolean cumplimentarEjemplo;
    private String presetInstancia;
    private Map<String, String> etiquetas;

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public List<ComponenteFormulario> getComponentes() {
        return componentes;
    }

    public void setComponentes(List<ComponenteFormulario> componentes) {
        this.componentes = componentes;
    }

    public boolean isCumplimentarEjemplo() {
        return cumplimentarEjemplo;
    }

    public void setCumplimentarEjemplo(boolean cumplimentarEjemplo) {
        this.cumplimentarEjemplo = cumplimentarEjemplo;
    }

    public String getPresetInstancia() {
        return presetInstancia;
    }

    public void setPresetInstancia(String presetInstancia) {
        this.presetInstancia = presetInstancia;
    }

    public Map<String, String> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(Map<String, String> etiquetas) {
        this.etiquetas = etiquetas;
    }
}
