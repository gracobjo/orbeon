package com.orbeon.editor.dto;

import com.orbeon.editor.model.AnotacionInstruccionPdf;
import com.orbeon.editor.model.PropuestaCambioXml;

import java.util.ArrayList;
import java.util.List;

/**
 * Comparación entre dos PDFs de instrucciones sobre el mismo XML base.
 */
public class ComparacionInstruccionesResponse {

    private AnalisisInstruccionesResponse analisisBase;
    private AnalisisInstruccionesResponse analisisNuevo;
    private String resumen;
    private List<AnotacionInstruccionPdf> anotacionesSoloBase = new ArrayList<>();
    private List<AnotacionInstruccionPdf> anotacionesSoloNuevo = new ArrayList<>();
    private List<AnotacionInstruccionPdf> anotacionesComunes = new ArrayList<>();
    private List<PropuestaCambioXml> propuestasSoloBase = new ArrayList<>();
    private List<PropuestaCambioXml> propuestasSoloNuevo = new ArrayList<>();
    private List<String> camposSoloBase = new ArrayList<>();
    private List<String> camposSoloNuevo = new ArrayList<>();
    private List<String> camposComunes = new ArrayList<>();

    public AnalisisInstruccionesResponse getAnalisisBase() {
        return analisisBase;
    }

    public void setAnalisisBase(AnalisisInstruccionesResponse analisisBase) {
        this.analisisBase = analisisBase;
    }

    public AnalisisInstruccionesResponse getAnalisisNuevo() {
        return analisisNuevo;
    }

    public void setAnalisisNuevo(AnalisisInstruccionesResponse analisisNuevo) {
        this.analisisNuevo = analisisNuevo;
    }

    public String getResumen() {
        return resumen;
    }

    public void setResumen(String resumen) {
        this.resumen = resumen;
    }

    public List<AnotacionInstruccionPdf> getAnotacionesSoloBase() {
        return anotacionesSoloBase;
    }

    public void setAnotacionesSoloBase(List<AnotacionInstruccionPdf> anotacionesSoloBase) {
        this.anotacionesSoloBase = anotacionesSoloBase != null ? anotacionesSoloBase : new ArrayList<>();
    }

    public List<AnotacionInstruccionPdf> getAnotacionesSoloNuevo() {
        return anotacionesSoloNuevo;
    }

    public void setAnotacionesSoloNuevo(List<AnotacionInstruccionPdf> anotacionesSoloNuevo) {
        this.anotacionesSoloNuevo = anotacionesSoloNuevo != null ? anotacionesSoloNuevo : new ArrayList<>();
    }

    public List<AnotacionInstruccionPdf> getAnotacionesComunes() {
        return anotacionesComunes;
    }

    public void setAnotacionesComunes(List<AnotacionInstruccionPdf> anotacionesComunes) {
        this.anotacionesComunes = anotacionesComunes != null ? anotacionesComunes : new ArrayList<>();
    }

    public List<PropuestaCambioXml> getPropuestasSoloBase() {
        return propuestasSoloBase;
    }

    public void setPropuestasSoloBase(List<PropuestaCambioXml> propuestasSoloBase) {
        this.propuestasSoloBase = propuestasSoloBase != null ? propuestasSoloBase : new ArrayList<>();
    }

    public List<PropuestaCambioXml> getPropuestasSoloNuevo() {
        return propuestasSoloNuevo;
    }

    public void setPropuestasSoloNuevo(List<PropuestaCambioXml> propuestasSoloNuevo) {
        this.propuestasSoloNuevo = propuestasSoloNuevo != null ? propuestasSoloNuevo : new ArrayList<>();
    }

    public List<String> getCamposSoloBase() {
        return camposSoloBase;
    }

    public void setCamposSoloBase(List<String> camposSoloBase) {
        this.camposSoloBase = camposSoloBase != null ? camposSoloBase : new ArrayList<>();
    }

    public List<String> getCamposSoloNuevo() {
        return camposSoloNuevo;
    }

    public void setCamposSoloNuevo(List<String> camposSoloNuevo) {
        this.camposSoloNuevo = camposSoloNuevo != null ? camposSoloNuevo : new ArrayList<>();
    }

    public List<String> getCamposComunes() {
        return camposComunes;
    }

    public void setCamposComunes(List<String> camposComunes) {
        this.camposComunes = camposComunes != null ? camposComunes : new ArrayList<>();
    }
}
