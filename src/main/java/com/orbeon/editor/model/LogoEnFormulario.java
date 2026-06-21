package com.orbeon.editor.model;

/**
 * Logo/imagen detectada en el formulario con su posición en la vista.
 */
public class LogoEnFormulario {

    private int posicionGlobal;
    private int posicionEnSeccion;
    private String tag;
    private String controlId;
    private String bind;
    private String sectionId;
    private String sectionTitulo;
    private String cssClass;
    private String filename;
    private String mediatype;
    private String src;
    private String label;

    public int getPosicionGlobal() {
        return posicionGlobal;
    }

    public void setPosicionGlobal(int posicionGlobal) {
        this.posicionGlobal = posicionGlobal;
    }

    public int getPosicionEnSeccion() {
        return posicionEnSeccion;
    }

    public void setPosicionEnSeccion(int posicionEnSeccion) {
        this.posicionEnSeccion = posicionEnSeccion;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getControlId() {
        return controlId;
    }

    public void setControlId(String controlId) {
        this.controlId = controlId;
    }

    public String getBind() {
        return bind;
    }

    public void setBind(String bind) {
        this.bind = bind;
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }

    public String getSectionTitulo() {
        return sectionTitulo;
    }

    public void setSectionTitulo(String sectionTitulo) {
        this.sectionTitulo = sectionTitulo;
    }

    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMediatype() {
        return mediatype;
    }

    public void setMediatype(String mediatype) {
        this.mediatype = mediatype;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
