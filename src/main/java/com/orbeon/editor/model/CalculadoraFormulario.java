package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Campo calculado Orbeon ({@code xf:bind @calculate}).
 */
public class CalculadoraFormulario {

    private String bindId;
    private String ref;
    private String nombre;
    private String label;
    private String controlId;
    private String expresionCalculate;
    private String tipoCalculo;
    private boolean soloLectura;
    private List<String> fuentesDatos = new ArrayList<>();
    private List<String> descripcionesFuentes = new ArrayList<>();
    private List<String> urlsExternas = new ArrayList<>();

    public String getBindId() {
        return bindId;
    }

    public void setBindId(String bindId) {
        this.bindId = bindId;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getControlId() {
        return controlId;
    }

    public void setControlId(String controlId) {
        this.controlId = controlId;
    }

    public String getExpresionCalculate() {
        return expresionCalculate;
    }

    public void setExpresionCalculate(String expresionCalculate) {
        this.expresionCalculate = expresionCalculate;
    }

    public String getTipoCalculo() {
        return tipoCalculo;
    }

    public void setTipoCalculo(String tipoCalculo) {
        this.tipoCalculo = tipoCalculo;
    }

    public boolean isSoloLectura() {
        return soloLectura;
    }

    public void setSoloLectura(boolean soloLectura) {
        this.soloLectura = soloLectura;
    }

    public List<String> getFuentesDatos() {
        return fuentesDatos;
    }

    public void setFuentesDatos(List<String> fuentesDatos) {
        this.fuentesDatos = fuentesDatos != null ? fuentesDatos : new ArrayList<>();
    }

    public List<String> getDescripcionesFuentes() {
        return descripcionesFuentes;
    }

    public void setDescripcionesFuentes(List<String> descripcionesFuentes) {
        this.descripcionesFuentes = descripcionesFuentes != null ? descripcionesFuentes : new ArrayList<>();
    }

    public List<String> getUrlsExternas() {
        return urlsExternas;
    }

    public void setUrlsExternas(List<String> urlsExternas) {
        this.urlsExternas = urlsExternas != null ? urlsExternas : new ArrayList<>();
    }
}
