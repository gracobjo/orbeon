package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista estructural del impacto de un PDF de instrucciones sobre el XML cargado.
 */
public class EstructuraInstruccionesPdf {

    private String formulario;
    private int totalSeccionesAfectadas;
    private int totalCamposAfectados;
    private List<SeccionInstruccionesPdf> secciones = new ArrayList<>();
    private List<AnotacionInstruccionPdf> anotacionesSinMapear = new ArrayList<>();

    public String getFormulario() {
        return formulario;
    }

    public void setFormulario(String formulario) {
        this.formulario = formulario;
    }

    public int getTotalSeccionesAfectadas() {
        return totalSeccionesAfectadas;
    }

    public void setTotalSeccionesAfectadas(int totalSeccionesAfectadas) {
        this.totalSeccionesAfectadas = totalSeccionesAfectadas;
    }

    public int getTotalCamposAfectados() {
        return totalCamposAfectados;
    }

    public void setTotalCamposAfectados(int totalCamposAfectados) {
        this.totalCamposAfectados = totalCamposAfectados;
    }

    public List<SeccionInstruccionesPdf> getSecciones() {
        return secciones;
    }

    public void setSecciones(List<SeccionInstruccionesPdf> secciones) {
        this.secciones = secciones != null ? secciones : new ArrayList<>();
    }

    public List<AnotacionInstruccionPdf> getAnotacionesSinMapear() {
        return anotacionesSinMapear;
    }

    public void setAnotacionesSinMapear(List<AnotacionInstruccionPdf> anotacionesSinMapear) {
        this.anotacionesSinMapear = anotacionesSinMapear != null ? anotacionesSinMapear : new ArrayList<>();
    }
}
