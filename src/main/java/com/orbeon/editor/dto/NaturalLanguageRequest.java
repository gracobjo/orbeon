package com.orbeon.editor.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NaturalLanguageRequest {

    private String xml;
    private String instruccion;
    private boolean aplicarCambios = true;

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getInstruccion() {
        return instruccion;
    }

    public void setInstruccion(String instruccion) {
        this.instruccion = instruccion;
    }

    public boolean isAplicarCambios() {
        return aplicarCambios;
    }

    public void setAplicarCambios(boolean aplicarCambios) {
        this.aplicarCambios = aplicarCambios;
    }
}
