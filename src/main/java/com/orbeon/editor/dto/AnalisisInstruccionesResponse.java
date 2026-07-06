package com.orbeon.editor.dto;

import com.orbeon.editor.model.AnotacionInstruccionPdf;
import com.orbeon.editor.model.EstructuraFormulario;
import com.orbeon.editor.model.EstructuraInstruccionesPdf;
import com.orbeon.editor.model.PropuestaCambioXml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnalisisInstruccionesResponse {

    private String nombrePdf;
    private int totalPaginas;
    private int totalAnotaciones;
    private List<AnotacionInstruccionPdf> anotaciones = new ArrayList<>();
    private List<PropuestaCambioXml> propuestas = new ArrayList<>();
    private List<Map<String, Object>> cambiosAgregados = new ArrayList<>();
    private String resumen;
    private String xml;
    private String nombreFormulario;
    private EstructuraFormulario estructura;
    private EstructuraInstruccionesPdf estructuraInstrucciones;
    private List<String> logAplicados = new ArrayList<>();

    public String getNombrePdf() {
        return nombrePdf;
    }

    public void setNombrePdf(String nombrePdf) {
        this.nombrePdf = nombrePdf;
    }

    public int getTotalPaginas() {
        return totalPaginas;
    }

    public void setTotalPaginas(int totalPaginas) {
        this.totalPaginas = totalPaginas;
    }

    public int getTotalAnotaciones() {
        return totalAnotaciones;
    }

    public void setTotalAnotaciones(int totalAnotaciones) {
        this.totalAnotaciones = totalAnotaciones;
    }

    public List<AnotacionInstruccionPdf> getAnotaciones() {
        return anotaciones;
    }

    public void setAnotaciones(List<AnotacionInstruccionPdf> anotaciones) {
        this.anotaciones = anotaciones != null ? anotaciones : new ArrayList<>();
    }

    public List<PropuestaCambioXml> getPropuestas() {
        return propuestas;
    }

    public void setPropuestas(List<PropuestaCambioXml> propuestas) {
        this.propuestas = propuestas != null ? propuestas : new ArrayList<>();
    }

    public List<Map<String, Object>> getCambiosAgregados() {
        return cambiosAgregados;
    }

    public void setCambiosAgregados(List<Map<String, Object>> cambiosAgregados) {
        this.cambiosAgregados = cambiosAgregados != null ? cambiosAgregados : new ArrayList<>();
    }

    public String getResumen() {
        return resumen;
    }

    public void setResumen(String resumen) {
        this.resumen = resumen;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getNombreFormulario() {
        return nombreFormulario;
    }

    public void setNombreFormulario(String nombreFormulario) {
        this.nombreFormulario = nombreFormulario;
    }

    public EstructuraFormulario getEstructura() {
        return estructura;
    }

    public void setEstructura(EstructuraFormulario estructura) {
        this.estructura = estructura;
    }

    public EstructuraInstruccionesPdf getEstructuraInstrucciones() {
        return estructuraInstrucciones;
    }

    public void setEstructuraInstrucciones(EstructuraInstruccionesPdf estructuraInstrucciones) {
        this.estructuraInstrucciones = estructuraInstrucciones;
    }

    public List<String> getLogAplicados() {
        return logAplicados;
    }

    public void setLogAplicados(List<String> logAplicados) {
        this.logAplicados = logAplicados != null ? logAplicados : new ArrayList<>();
    }
}
