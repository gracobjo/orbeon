package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Sección del formulario con campos tocados por el PDF de instrucciones.
 */
public class SeccionInstruccionesPdf {

    private String id;
    private String titulo;
    private int totalAnotaciones;
    private List<CampoInstruccionPdf> campos = new ArrayList<>();

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

    public int getTotalAnotaciones() {
        return totalAnotaciones;
    }

    public void setTotalAnotaciones(int totalAnotaciones) {
        this.totalAnotaciones = totalAnotaciones;
    }

    public List<CampoInstruccionPdf> getCampos() {
        return campos;
    }

    public void setCampos(List<CampoInstruccionPdf> campos) {
        this.campos = campos != null ? campos : new ArrayList<>();
    }
}
