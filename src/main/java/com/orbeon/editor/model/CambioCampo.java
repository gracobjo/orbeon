package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

public class CambioCampo {

    private String campo;
    private String valorAnterior;
    private String valorNuevo;

    public CambioCampo() {
    }

    public CambioCampo(String campo, String valorAnterior, String valorNuevo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public void setValorNuevo(String valorNuevo) {
        this.valorNuevo = valorNuevo;
    }
}
