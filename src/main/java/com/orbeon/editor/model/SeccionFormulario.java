package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupa componentes bajo una sección fr:section para el layout del PDF y la UI.
 */
public class SeccionFormulario {

    private String id;
    private String titulo;
    private String bind;
    private String cssClass;
    private boolean noPrintInPdf;
    private int gridCount;
    private List<ComponenteFormulario> componentes = new ArrayList<>();

    public SeccionFormulario() {
    }

    public SeccionFormulario(String id, String titulo) {
        this.id = id;
        this.titulo = titulo;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getBind() {
        return bind;
    }

    public void setBind(String bind) {
        this.bind = bind;
    }

    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    public boolean isNoPrintInPdf() {
        return noPrintInPdf;
    }

    public void setNoPrintInPdf(boolean noPrintInPdf) {
        this.noPrintInPdf = noPrintInPdf;
    }

    public int getGridCount() {
        return gridCount;
    }

    public void setGridCount(int gridCount) {
        this.gridCount = gridCount;
    }

    public List<ComponenteFormulario> getComponentes() {
        return componentes;
    }

    public void setComponentes(List<ComponenteFormulario> componentes) {
        this.componentes = componentes != null ? componentes : new ArrayList<>();
    }
}
