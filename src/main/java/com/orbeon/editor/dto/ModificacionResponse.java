package com.orbeon.editor.dto;

import java.util.ArrayList;
import java.util.List;

public class ModificacionResponse {

    private String xml;
    private List<String> applied = new ArrayList<>();
    private List<String> changeLog = new ArrayList<>();

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public List<String> getApplied() {
        return applied;
    }

    public void setApplied(List<String> applied) {
        this.applied = applied != null ? applied : new ArrayList<>();
    }

    public List<String> getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(List<String> changeLog) {
        this.changeLog = changeLog != null ? changeLog : new ArrayList<>();
    }
}
