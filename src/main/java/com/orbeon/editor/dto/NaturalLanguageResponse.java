package com.orbeon.editor.dto;

import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.EstructuraFormulario;
import com.orbeon.editor.model.LogoEnFormulario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NaturalLanguageResponse {

    private String intencion;
    private String respuesta;
    private boolean ejecutado;
    private String xml;
    private List<Map<String, Object>> cambiosPropuestos = new ArrayList<>();
    private List<String> log = new ArrayList<>();
    private List<LogoEnFormulario> logos = new ArrayList<>();
    private List<ComponenteFormulario> componentes = new ArrayList<>();
    private EstructuraFormulario estructura;

    public String getIntencion() {
        return intencion;
    }

    public void setIntencion(String intencion) {
        this.intencion = intencion;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }

    public boolean isEjecutado() {
        return ejecutado;
    }

    public void setEjecutado(boolean ejecutado) {
        this.ejecutado = ejecutado;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public List<Map<String, Object>> getCambiosPropuestos() {
        return cambiosPropuestos;
    }

    public void setCambiosPropuestos(List<Map<String, Object>> cambiosPropuestos) {
        this.cambiosPropuestos = cambiosPropuestos != null ? cambiosPropuestos : new ArrayList<>();
    }

    public List<String> getLog() {
        return log;
    }

    public void setLog(List<String> log) {
        this.log = log != null ? log : new ArrayList<>();
    }

    public List<LogoEnFormulario> getLogos() {
        return logos;
    }

    public void setLogos(List<LogoEnFormulario> logos) {
        this.logos = logos != null ? logos : new ArrayList<>();
    }

    public List<ComponenteFormulario> getComponentes() {
        return componentes;
    }

    public void setComponentes(List<ComponenteFormulario> componentes) {
        this.componentes = componentes != null ? componentes : new ArrayList<>();
    }

    public EstructuraFormulario getEstructura() {
        return estructura;
    }

    public void setEstructura(EstructuraFormulario estructura) {
        this.estructura = estructura;
    }
}
