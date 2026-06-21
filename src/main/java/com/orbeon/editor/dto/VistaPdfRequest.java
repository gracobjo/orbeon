package com.orbeon.editor.dto;

import com.orbeon.editor.model.ComponenteFormulario;

import java.util.List;

public class VistaPdfRequest {

    private String xml;
    private List<ComponenteFormulario> componentes;

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
}
