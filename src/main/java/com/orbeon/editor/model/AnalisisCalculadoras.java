package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalisisCalculadoras {

    private List<CalculadoraFormulario> elementos = new ArrayList<>();
    private Map<String, String> glosarioFuentes = new LinkedHashMap<>();
    private int total;
    private int totalConApiExterna;
    private int totalSoloLectura;

    public List<CalculadoraFormulario> getElementos() {
        return elementos;
    }

    public void setElementos(List<CalculadoraFormulario> elementos) {
        this.elementos = elementos != null ? elementos : new ArrayList<>();
    }

    public Map<String, String> getGlosarioFuentes() {
        return glosarioFuentes;
    }

    public void setGlosarioFuentes(Map<String, String> glosarioFuentes) {
        this.glosarioFuentes = glosarioFuentes != null ? glosarioFuentes : new LinkedHashMap<>();
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getTotalConApiExterna() {
        return totalConApiExterna;
    }

    public void setTotalConApiExterna(int totalConApiExterna) {
        this.totalConApiExterna = totalConApiExterna;
    }

    public int getTotalSoloLectura() {
        return totalSoloLectura;
    }

    public void setTotalSoloLectura(int totalSoloLectura) {
        this.totalSoloLectura = totalSoloLectura;
    }
}
