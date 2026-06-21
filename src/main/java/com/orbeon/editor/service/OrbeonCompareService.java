package com.orbeon.editor.service;

import com.orbeon.editor.dto.ComparacionResponse;
import com.orbeon.editor.model.CambioCampo;
import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.DiferenciaComponente;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compara dos plantillas Orbeon parseadas y detecta altas, bajas y cambios por componente.
 */
@Service
public class OrbeonCompareService {

    private final OrbeonFormService orbeonFormService;

    public OrbeonCompareService(OrbeonFormService orbeonFormService) {
        this.orbeonFormService = orbeonFormService;
    }

    public ComparacionResponse comparar(String xmlBase, String xmlNuevo,
                                        String nombreBase, String nombreNuevo) {
        List<ComponenteFormulario> base = orbeonFormService.parsearEstructuraDesdeString(xmlBase);
        List<ComponenteFormulario> nuevo = orbeonFormService.parsearEstructuraDesdeString(xmlNuevo);

        Map<String, ComponenteFormulario> mapaBase = indexarPorId(base);
        Map<String, ComponenteFormulario> mapaNuevo = indexarPorId(nuevo);

        Set<String> todosIds = new TreeSet<>();
        todosIds.addAll(mapaBase.keySet());
        todosIds.addAll(mapaNuevo.keySet());

        List<DiferenciaComponente> diferencias = new ArrayList<>();
        int anadidos = 0;
        int eliminados = 0;
        int modificados = 0;
        int sinCambios = 0;

        for (String id : todosIds) {
            ComponenteFormulario compBase = mapaBase.get(id);
            ComponenteFormulario compNuevo = mapaNuevo.get(id);

            if (compBase == null) {
                DiferenciaComponente diff = new DiferenciaComponente();
                diff.setTipoCambio("ANADIDO");
                diff.setComponenteNuevo(compNuevo);
                diferencias.add(diff);
                anadidos++;
            } else if (compNuevo == null) {
                DiferenciaComponente diff = new DiferenciaComponente();
                diff.setTipoCambio("ELIMINADO");
                diff.setComponenteBase(compBase);
                diferencias.add(diff);
                eliminados++;
            } else {
                List<CambioCampo> cambios = detectarCambios(compBase, compNuevo);
                if (cambios.isEmpty()) {
                    sinCambios++;
                } else {
                    DiferenciaComponente diff = new DiferenciaComponente();
                    diff.setTipoCambio("MODIFICADO");
                    diff.setComponenteBase(compBase);
                    diff.setComponenteNuevo(compNuevo);
                    diff.setCambios(cambios);
                    diferencias.add(diff);
                    modificados++;
                }
            }
        }

        ComparacionResponse respuesta = new ComparacionResponse();
        respuesta.setNombreBase(nombreBase);
        respuesta.setNombreNuevo(nombreNuevo);
        respuesta.setTotalBase(base.size());
        respuesta.setTotalNuevo(nuevo.size());
        respuesta.setAnadidos(anadidos);
        respuesta.setEliminados(eliminados);
        respuesta.setModificados(modificados);
        respuesta.setSinCambios(sinCambios);
        respuesta.setDiferencias(diferencias);
        return respuesta;
    }

    private Map<String, ComponenteFormulario> indexarPorId(List<ComponenteFormulario> componentes) {
        Map<String, ComponenteFormulario> mapa = new LinkedHashMap<>();
        for (ComponenteFormulario c : componentes) {
            if (c.getId() != null) {
                mapa.put(c.getId(), c);
            }
        }
        return mapa;
    }

    private List<CambioCampo> detectarCambios(ComponenteFormulario base, ComponenteFormulario nuevo) {
        List<CambioCampo> cambios = new ArrayList<>();

        if (!Objects.equals(base.getTipo(), nuevo.getTipo())) {
            cambios.add(new CambioCampo("tipo", base.getTipo(), nuevo.getTipo()));
        }
        if (!Objects.equals(base.getLabel(), nuevo.getLabel())) {
            cambios.add(new CambioCampo("label", base.getLabel(), nuevo.getLabel()));
        }
        if (!Objects.equals(base.getHint(), nuevo.getHint())) {
            cambios.add(new CambioCampo("hint", base.getHint(), nuevo.getHint()));
        }
        if (!Objects.equals(base.getAlert(), nuevo.getAlert())) {
            cambios.add(new CambioCampo("alert", base.getAlert(), nuevo.getAlert()));
        }

        String refBase = obtenerRef(base);
        String refNuevo = obtenerRef(nuevo);
        if (!Objects.equals(refBase, refNuevo)) {
            cambios.add(new CambioCampo("ref", refBase, refNuevo));
        }

        return cambios;
    }

    private String obtenerRef(ComponenteFormulario comp) {
        if (comp.getMetadatos() == null) {
            return "";
        }
        return comp.getMetadatos().getOrDefault("ref", "");
    }
}
