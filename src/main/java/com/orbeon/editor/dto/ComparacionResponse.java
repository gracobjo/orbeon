package com.orbeon.editor.dto;

import com.orbeon.editor.model.DiferenciaComponente;

import java.util.ArrayList;
import java.util.List;

public class ComparacionResponse {

    private String nombreBase;
    private String nombreNuevo;
    private int totalBase;
    private int totalNuevo;
    private int anadidos;
    private int eliminados;
    private int modificados;
    private int sinCambios;
    private List<DiferenciaComponente> diferencias = new ArrayList<>();

    public String getNombreBase() {
        return nombreBase;
    }

    public void setNombreBase(String nombreBase) {
        this.nombreBase = nombreBase;
    }

    public String getNombreNuevo() {
        return nombreNuevo;
    }

    public void setNombreNuevo(String nombreNuevo) {
        this.nombreNuevo = nombreNuevo;
    }

    public int getTotalBase() {
        return totalBase;
    }

    public void setTotalBase(int totalBase) {
        this.totalBase = totalBase;
    }

    public int getTotalNuevo() {
        return totalNuevo;
    }

    public void setTotalNuevo(int totalNuevo) {
        this.totalNuevo = totalNuevo;
    }

    public int getAnadidos() {
        return anadidos;
    }

    public void setAnadidos(int anadidos) {
        this.anadidos = anadidos;
    }

    public int getEliminados() {
        return eliminados;
    }

    public void setEliminados(int eliminados) {
        this.eliminados = eliminados;
    }

    public int getModificados() {
        return modificados;
    }

    public void setModificados(int modificados) {
        this.modificados = modificados;
    }

    public int getSinCambios() {
        return sinCambios;
    }

    public void setSinCambios(int sinCambios) {
        this.sinCambios = sinCambios;
    }

    public List<DiferenciaComponente> getDiferencias() {
        return diferencias;
    }

    public void setDiferencias(List<DiferenciaComponente> diferencias) {
        this.diferencias = diferencias != null ? diferencias : new ArrayList<>();
    }
}
