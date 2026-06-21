package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

public class DiferenciaComponente {

    /** ANADIDO | ELIMINADO | MODIFICADO */
    private String tipoCambio;
    private ComponenteFormulario componenteBase;
    private ComponenteFormulario componenteNuevo;
    private List<CambioCampo> cambios = new ArrayList<>();

    public DiferenciaComponente() {
    }

    public String getTipoCambio() {
        return tipoCambio;
    }

    public void setTipoCambio(String tipoCambio) {
        this.tipoCambio = tipoCambio;
    }

    public ComponenteFormulario getComponenteBase() {
        return componenteBase;
    }

    public void setComponenteBase(ComponenteFormulario componenteBase) {
        this.componenteBase = componenteBase;
    }

    public ComponenteFormulario getComponenteNuevo() {
        return componenteNuevo;
    }

    public void setComponenteNuevo(ComponenteFormulario componenteNuevo) {
        this.componenteNuevo = componenteNuevo;
    }

    public List<CambioCampo> getCambios() {
        return cambios;
    }

    public void setCambios(List<CambioCampo> cambios) {
        this.cambios = cambios != null ? cambios : new ArrayList<>();
    }
}
