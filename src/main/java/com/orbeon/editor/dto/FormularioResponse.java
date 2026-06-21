package com.orbeon.editor.dto;

import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.EstructuraFormulario;

import java.util.List;

public class FormularioResponse {

    private List<ComponenteFormulario> componentes;
    private String xml;
    private EstructuraFormulario estructura;

    public FormularioResponse() {
    }

    public FormularioResponse(List<ComponenteFormulario> componentes, String xml) {
        this.componentes = componentes;
        this.xml = xml;
    }

    public List<ComponenteFormulario> getComponentes() {
        return componentes;
    }

    public void setComponentes(List<ComponenteFormulario> componentes) {
        this.componentes = componentes;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public EstructuraFormulario getEstructura() {
        return estructura;
    }

    public void setEstructura(EstructuraFormulario estructura) {
        this.estructura = estructura;
    }
}
