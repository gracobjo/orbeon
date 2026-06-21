package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalisisDependencias {

    private List<DependenciaVisibilidad> elementos = new ArrayList<>();
    private Map<String, String> glosarioDisparadores = new LinkedHashMap<>();
    private int totalSecciones;
    private int totalCondicionales;
    private int totalOcultas;

    public List<DependenciaVisibilidad> getElementos() {
        return elementos;
    }

    public void setElementos(List<DependenciaVisibilidad> elementos) {
        this.elementos = elementos != null ? elementos : new ArrayList<>();
    }

    public Map<String, String> getGlosarioDisparadores() {
        return glosarioDisparadores;
    }

    public void setGlosarioDisparadores(Map<String, String> glosarioDisparadores) {
        this.glosarioDisparadores = glosarioDisparadores != null ? glosarioDisparadores : new LinkedHashMap<>();
    }

    public int getTotalSecciones() {
        return totalSecciones;
    }

    public void setTotalSecciones(int totalSecciones) {
        this.totalSecciones = totalSecciones;
    }

    public int getTotalCondicionales() {
        return totalCondicionales;
    }

    public void setTotalCondicionales(int totalCondicionales) {
        this.totalCondicionales = totalCondicionales;
    }

    public int getTotalOcultas() {
        return totalOcultas;
    }

    public void setTotalOcultas(int totalOcultas) {
        this.totalOcultas = totalOcultas;
    }
}
