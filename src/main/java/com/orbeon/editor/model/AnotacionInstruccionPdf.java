package com.orbeon.editor.model;

import java.util.List;

/**
 * Anotación extraída de un PDF de instrucciones (margen o sobre el contenido).
 */
public class AnotacionInstruccionPdf {

    private int pagina;
    private String subtipo;
    private String contenido;
    private List<Float> rect;
    private float posicionVertical;

    public int getPagina() {
        return pagina;
    }

    public void setPagina(int pagina) {
        this.pagina = pagina;
    }

    public String getSubtipo() {
        return subtipo;
    }

    public void setSubtipo(String subtipo) {
        this.subtipo = subtipo;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public List<Float> getRect() {
        return rect;
    }

    public void setRect(List<Float> rect) {
        this.rect = rect;
    }

    public float getPosicionVertical() {
        return posicionVertical;
    }

    public void setPosicionVertical(float posicionVertical) {
        this.posicionVertical = posicionVertical;
    }
}
