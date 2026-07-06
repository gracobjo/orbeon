package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Campo del formulario afectado por una o varias instrucciones del PDF.
 */
public class CampoInstruccionPdf {

    private String fieldId;
    private String label;
    private String intencion;
    private String confianza;
    private boolean aplicableAutomaticamente;
    private Integer pagina;
    private List<String> propuestaIds = new ArrayList<>();

    public String getFieldId() {
        return fieldId;
    }

    public void setFieldId(String fieldId) {
        this.fieldId = fieldId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getIntencion() {
        return intencion;
    }

    public void setIntencion(String intencion) {
        this.intencion = intencion;
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

    public Integer getPagina() {
        return pagina;
    }

    public void setPagina(Integer pagina) {
        this.pagina = pagina;
    }

    public List<String> getPropuestaIds() {
        return propuestaIds;
    }

    public void setPropuestaIds(List<String> propuestaIds) {
        this.propuestaIds = propuestaIds != null ? propuestaIds : new ArrayList<>();
    }
}
