package com.orbeon.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista jerárquica completa del formulario Orbeon (secciones, imágenes, instancias).
 */
public class EstructuraFormulario {

    private String titulo;
    private List<SeccionFormulario> secciones = new ArrayList<>();
    private List<ImagenFormulario> imagenes = new ArrayList<>();
    private List<String> instancias = new ArrayList<>();

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public List<SeccionFormulario> getSecciones() {
        return secciones;
    }

    public void setSecciones(List<SeccionFormulario> secciones) {
        this.secciones = secciones != null ? secciones : new ArrayList<>();
    }

    public List<ImagenFormulario> getImagenes() {
        return imagenes;
    }

    public void setImagenes(List<ImagenFormulario> imagenes) {
        this.imagenes = imagenes != null ? imagenes : new ArrayList<>();
    }

    public List<String> getInstancias() {
        return instancias;
    }

    public void setInstancias(List<String> instancias) {
        this.instancias = instancias != null ? instancias : new ArrayList<>();
    }
}
