package com.orbeon.editor.dto;

import com.orbeon.editor.model.EstructuraFormulario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModificacionRequest {

    private String xml;
    private List<Map<String, Object>> changes;

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public List<Map<String, Object>> getChanges() {
        return changes;
    }

    public void setChanges(List<Map<String, Object>> changes) {
        this.changes = changes;
    }
}
