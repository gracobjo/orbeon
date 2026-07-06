package com.orbeon.editor.service;

import com.orbeon.editor.model.AnotacionInstruccionPdf;
import com.orbeon.editor.model.CampoInstruccionPdf;
import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.EstructuraFormulario;
import com.orbeon.editor.model.EstructuraInstruccionesPdf;
import com.orbeon.editor.model.PropuestaCambioXml;
import com.orbeon.editor.model.SeccionFormulario;
import com.orbeon.editor.model.SeccionInstruccionesPdf;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Construye la vista estructural del impacto de un PDF de instrucciones sobre el XML.
 */
@Service
public class OrbeonInstructionsStructureService {

    private final OrbeonStructureService structureService;

    public OrbeonInstructionsStructureService(OrbeonStructureService structureService) {
        this.structureService = structureService;
    }

    public EstructuraInstruccionesPdf construir(String xml, String nombreFormulario,
                                                List<AnotacionInstruccionPdf> anotaciones,
                                                List<PropuestaCambioXml> propuestas) {
        EstructuraFormulario estructura = structureService.parsearEstructuraCompleta(xml);
        Map<String, String> campoASeccion = indexarCamposPorSeccion(estructura);
        Map<String, String> labels = indexarLabels(estructura);

        Map<String, SeccionInstruccionesPdf> seccionesPorId = new LinkedHashMap<>();
        Map<String, CampoInstruccionPdf> camposPorId = new LinkedHashMap<>();
        Set<String> anotacionesMapeadas = new HashSet<>();

        for (PropuestaCambioXml propuesta : propuestas) {
            List<String> campos = propuesta.getCamposAfectados();
            if (campos == null || campos.isEmpty()) {
                continue;
            }
            for (String fieldId : campos) {
                anotacionesMapeadas.add(normalizar(propuesta.getTextoInstruccion()));
                CampoInstruccionPdf campo = camposPorId.computeIfAbsent(fieldId, id -> {
                    CampoInstruccionPdf c = new CampoInstruccionPdf();
                    c.setFieldId(id);
                    c.setLabel(labels.getOrDefault(id, id));
                    return c;
                });
                if (propuesta.getId() != null && !campo.getPropuestaIds().contains(propuesta.getId())) {
                    campo.getPropuestaIds().add(propuesta.getId());
                }
                campo.setIntencion(propuesta.getIntencion());
                campo.setConfianza(propuesta.getConfianza());
                campo.setAplicableAutomaticamente(propuesta.isAplicableAutomaticamente());
                if (propuesta.getPagina() != null) {
                    campo.setPagina(propuesta.getPagina());
                }

                String seccionId = resolverSeccion(fieldId, campoASeccion);
                SeccionInstruccionesPdf seccion = seccionesPorId.computeIfAbsent(seccionId, id -> {
                    SeccionInstruccionesPdf s = new SeccionInstruccionesPdf();
                    s.setId(id);
                    s.setTitulo(resolverTituloSeccion(id, estructura));
                    return s;
                });
                if (seccion.getCampos().stream().noneMatch(c -> fieldId.equals(c.getFieldId()))) {
                    seccion.getCampos().add(campo);
                }
            }
        }

        List<AnotacionInstruccionPdf> sinMapear = new ArrayList<>();
        for (AnotacionInstruccionPdf anot : anotaciones) {
            String clave = normalizar(anot.getContenido());
            if (clave.isBlank()) {
                continue;
            }
            boolean mapeada = propuestas.stream().anyMatch(p ->
                    coincideTexto(clave, p.getTextoInstruccion())
                            || (p.getCamposAfectados() != null && !p.getCamposAfectados().isEmpty()));
            if (!mapeada) {
                sinMapear.add(anot);
            }
        }

        EstructuraInstruccionesPdf resultado = new EstructuraInstruccionesPdf();
        resultado.setFormulario(nombreFormulario);
        resultado.setSecciones(new ArrayList<>(seccionesPorId.values()));
        resultado.setTotalSeccionesAfectadas(seccionesPorId.size());
        resultado.setTotalCamposAfectados(camposPorId.size());
        resultado.setAnotacionesSinMapear(sinMapear);
        return resultado;
    }

    private Map<String, String> indexarCamposPorSeccion(EstructuraFormulario estructura) {
        Map<String, String> mapa = new HashMap<>();
        if (estructura.getSecciones() == null) {
            return mapa;
        }
        for (SeccionFormulario seccion : estructura.getSecciones()) {
            if (seccion.getComponentes() == null) {
                continue;
            }
            for (ComponenteFormulario comp : seccion.getComponentes()) {
                if (comp.getId() != null) {
                    mapa.put(comp.getId(), seccion.getId());
                }
            }
        }
        return mapa;
    }

    private Map<String, String> indexarLabels(EstructuraFormulario estructura) {
        Map<String, String> mapa = new HashMap<>();
        if (estructura.getSecciones() == null) {
            return mapa;
        }
        for (SeccionFormulario seccion : estructura.getSecciones()) {
            if (seccion.getComponentes() == null) {
                continue;
            }
            for (ComponenteFormulario comp : seccion.getComponentes()) {
                if (comp.getId() != null) {
                    mapa.put(comp.getId(), comp.getLabel() != null ? comp.getLabel() : comp.getId());
                }
            }
        }
        return mapa;
    }

    private String resolverSeccion(String fieldId, Map<String, String> campoASeccion) {
        if (campoASeccion.containsKey(fieldId)) {
            return campoASeccion.get(fieldId);
        }
        String prefijo = fieldId.contains("-") ? fieldId.substring(0, fieldId.indexOf('-')) : fieldId;
        for (Map.Entry<String, String> e : campoASeccion.entrySet()) {
            if (e.getKey().startsWith(prefijo + "-")) {
                return e.getValue();
            }
        }
        return "_sin_seccion";
    }

    private String resolverTituloSeccion(String seccionId, EstructuraFormulario estructura) {
        if ("_sin_seccion".equals(seccionId)) {
            return "Sin sección / transversal";
        }
        if (estructura.getSecciones() == null) {
            return seccionId;
        }
        for (SeccionFormulario s : estructura.getSecciones()) {
            if (seccionId.equals(s.getId())) {
                return s.getTitulo() != null ? s.getTitulo() : seccionId;
            }
        }
        return seccionId;
    }

    private boolean coincideTexto(String a, String b) {
        if (b == null || b.isBlank()) {
            return false;
        }
        String nb = normalizar(b);
        return a.contains(nb) || nb.contains(a);
    }

    private String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
