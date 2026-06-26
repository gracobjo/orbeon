package com.orbeon.editor.dto;

import com.orbeon.editor.model.AnalisisCalculadoras;
import com.orbeon.editor.model.AnalisisDependencias;
import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.EstructuraFormulario;
import com.orbeon.editor.model.EtiquetaControlNumerico;

import java.util.ArrayList;
import java.util.List;

public class FormularioResponse {

    private List<ComponenteFormulario> componentes;
    private String xml;
    private EstructuraFormulario estructura;
    private AnalisisDependencias dependencias;
    private AnalisisCalculadoras calculadoras;
    private List<String> etiquetasControlNumerico = new ArrayList<>();
    private List<EtiquetaControlNumerico> controlesGenericos = new ArrayList<>();

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

    public AnalisisDependencias getDependencias() {
        return dependencias;
    }

    public void setDependencias(AnalisisDependencias dependencias) {
        this.dependencias = dependencias;
    }

    public AnalisisCalculadoras getCalculadoras() {
        return calculadoras;
    }

    public void setCalculadoras(AnalisisCalculadoras calculadoras) {
        this.calculadoras = calculadoras;
    }

    public List<String> getEtiquetasControlNumerico() {
        return etiquetasControlNumerico;
    }

    public void setEtiquetasControlNumerico(List<String> etiquetasControlNumerico) {
        this.etiquetasControlNumerico = etiquetasControlNumerico != null
                ? etiquetasControlNumerico : new ArrayList<>();
    }

    public List<EtiquetaControlNumerico> getControlesGenericos() {
        return controlesGenericos;
    }

    public void setControlesGenericos(List<EtiquetaControlNumerico> controlesGenericos) {
        this.controlesGenericos = controlesGenericos != null ? controlesGenericos : new ArrayList<>();
    }
}
