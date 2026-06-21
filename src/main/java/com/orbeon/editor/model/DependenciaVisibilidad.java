package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Visibilidad condicional de una sección o grid Orbeon (atributo {@code relevant} del bind).
 */
public class DependenciaVisibilidad {

    private String id;
    private String bindId;
    private String titulo;
    private String tipoElemento;
    private String expresionRelevant;
    private String tipoVisibilidad;
    private List<String> dependeDe = new ArrayList<>();
    private List<String> descripcionesDependencias = new ArrayList<>();
    private String seccionPadreId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBindId() {
        return bindId;
    }

    public void setBindId(String bindId) {
        this.bindId = bindId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTipoElemento() {
        return tipoElemento;
    }

    public void setTipoElemento(String tipoElemento) {
        this.tipoElemento = tipoElemento;
    }

    public String getExpresionRelevant() {
        return expresionRelevant;
    }

    public void setExpresionRelevant(String expresionRelevant) {
        this.expresionRelevant = expresionRelevant;
    }

    public String getTipoVisibilidad() {
        return tipoVisibilidad;
    }

    public void setTipoVisibilidad(String tipoVisibilidad) {
        this.tipoVisibilidad = tipoVisibilidad;
    }

    public List<String> getDependeDe() {
        return dependeDe;
    }

    public void setDependeDe(List<String> dependeDe) {
        this.dependeDe = dependeDe != null ? dependeDe : new ArrayList<>();
    }

    public List<String> getDescripcionesDependencias() {
        return descripcionesDependencias;
    }

    public void setDescripcionesDependencias(List<String> descripcionesDependencias) {
        this.descripcionesDependencias = descripcionesDependencias != null
                ? descripcionesDependencias : new ArrayList<>();
    }

    public String getSeccionPadreId() {
        return seccionPadreId;
    }

    public void setSeccionPadreId(String seccionPadreId) {
        this.seccionPadreId = seccionPadreId;
    }
}
