package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cambio XML propuesto a partir de una o varias anotaciones del PDF de instrucciones.
 */
public class PropuestaCambioXml {

    private String id;
    private String intencion;
    private String descripcion;
    private String textoInstruccion;
    private Integer pagina;
    private String confianza;
    private boolean aplicableAutomaticamente;
    private List<String> camposAfectados = new ArrayList<>();
    private List<Map<String, Object>> cambios = new ArrayList<>();
    private String nota;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIntencion() {
        return intencion;
    }

    public void setIntencion(String intencion) {
        this.intencion = intencion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTextoInstruccion() {
        return textoInstruccion;
    }

    public void setTextoInstruccion(String textoInstruccion) {
        this.textoInstruccion = textoInstruccion;
    }

    public Integer getPagina() {
        return pagina;
    }

    public void setPagina(Integer pagina) {
        this.pagina = pagina;
    }

    public String getConfianza() {
        return confianza;
    }

    public void setConfianza(String confianza) {
        this.confianza = confianza;
    }

    public boolean isAplicableAutomaticamente() {
        return aplicableAutomaticamente;
    }

    public void setAplicableAutomaticamente(boolean aplicableAutomaticamente) {
        this.aplicableAutomaticamente = aplicableAutomaticamente;
    }

    public List<String> getCamposAfectados() {
        return camposAfectados;
    }

    public void setCamposAfectados(List<String> camposAfectados) {
        this.camposAfectados = camposAfectados != null ? camposAfectados : new ArrayList<>();
    }

    public List<Map<String, Object>> getCambios() {
        return cambios;
    }

    public void setCambios(List<Map<String, Object>> cambios) {
        this.cambios = cambios != null ? cambios : new ArrayList<>();
    }

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }
}
