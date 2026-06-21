package com.orbeon.editor.service;

import com.orbeon.editor.model.AnalisisDependencias;
import com.orbeon.editor.model.DependenciaVisibilidad;
import com.orbeon.editor.util.OrbeonXmlUtil;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analiza visibilidad condicional de secciones/grids vía {@code xf:bind @relevant}.
 */
@Service
public class OrbeonDependencyService {

    private static final Pattern REFERENCIA_BIND = Pattern.compile("\\$([a-zA-Z0-9_-]+)");

    private static final Map<String, String> GLOSARIO = Map.ofEntries(
            Map.entry("documentoIdent-nifSol", "NIF del solicitante (no vacío y válido)"),
            Map.entry("documentoIdent-tipodoc", "Tipo de documento calculado (dni / nie / cif)"),
            Map.entry("documentoIdent-autonomo", "Checkbox «Tiene condición de autónomo»"),
            Map.entry("tipoSolicitante", "Tipo de solicitante calculado (1 autónomo, 2 persona física, 3 empresa)"),
            Map.entry("otrosDatosEmpresa-vinculadas", "Checkbox «Empresas vinculadas» (solo CIF)"),
            Map.entry("control-spj", "SPJ calculado (1.ª letra CIF = E o H)"),
            Map.entry("conRepresentante", "Actuación mediante representante"),
            Map.entry("notifica-opcion", "Modo de notificación (electronica / papel)"),
            Map.entry("notificacion-destinatario", "Destinatario notificación electrónica"),
            Map.entry("opcionImprimir", "Opción imprimir / firmar en papel"),
            Map.entry("conceptoE1-V92790", "Pregunta sí/no en concepto E1"),
            Map.entry("certifico-integracionLaboral-opcion", "Situación integración laboral (sujeto / noSujeto / exento)"),
            Map.entry("declaracionesResponsables-ingresosFinanciadosOpc", "Declaración: ingresos financiados (SI/NO)"),
            Map.entry("declaracionesResponsables-concurrenciaAyudasOpc", "Declaración: concurrencia ayudas (SI/NO)"),
            Map.entry("declaracionesResponsables-concurrenciaAyudasMinimisOpc", "Declaración: ayudas de minimis (SI/NO)"),
            Map.entry("declaracionesResponsables-cumplimientoNormativaIntegracionLaboral",
                    "Checkbox normativa integración laboral"),
            Map.entry("permisosConsultaSufo-sufoid1", "Permiso consulta SUFO (afecta anexos representante)")
    );

    public AnalisisDependencias analizar(String xml) {
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Map<String, String> bindRelevant = construirMapaRelevant(doc, xpath);
            Map<String, String> titulosSeccion = construirTitulosSeccion(doc, xpath);

            AnalisisDependencias analisis = new AnalisisDependencias();
            List<DependenciaVisibilidad> elementos = new ArrayList<>();
            Set<String> disparadoresUsados = new LinkedHashSet<>();

            NodeList secciones = (NodeList) xpath.evaluate(
                    "//*[local-name()='view']//*[local-name()='section' and @id and @bind]",
                    doc,
                    XPathConstants.NODESET
            );
            for (int i = 0; i < secciones.getLength(); i++) {
                Element seccion = (Element) secciones.item(i);
                String sectionId = seccion.getAttribute("id");
                String bindId = seccion.getAttribute("bind");
                elementos.add(construirElemento(
                        sectionId, bindId, "section",
                        titulosSeccion.getOrDefault(sectionId, sectionId),
                        bindRelevant.get(bindId),
                        null,
                        disparadoresUsados
                ));
                agregarGridsCondicionales(seccion, sectionId, bindRelevant, titulosSeccion, elementos, disparadoresUsados);
            }

            analisis.setElementos(elementos);
            analisis.setTotalSecciones((int) secciones.getLength());
            analisis.setTotalCondicionales((int) elementos.stream()
                    .filter(e -> "condicional".equals(e.getTipoVisibilidad())).count());
            analisis.setTotalOcultas((int) elementos.stream()
                    .filter(e -> "oculta_fija".equals(e.getTipoVisibilidad())).count());

            Map<String, String> glosario = new LinkedHashMap<>();
            for (String ref : disparadoresUsados) {
                glosario.put(ref, GLOSARIO.getOrDefault(ref, "Variable bind: $" + ref));
            }
            analisis.setGlosarioDisparadores(glosario);
            return analisis;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al analizar dependencias: " + e.getMessage(), e);
        }
    }

    private void agregarGridsCondicionales(Element seccion, String sectionId,
                                           Map<String, String> bindRelevant,
                                           Map<String, String> titulosSeccion,
                                           List<DependenciaVisibilidad> elementos,
                                           Set<String> disparadoresUsados) {
        NodeList grids = seccion.getElementsByTagNameNS("*", "grid");
        for (int g = 0; g < grids.getLength(); g++) {
            Element grid = (Element) grids.item(g);
            String gridBind = grid.getAttribute("bind");
            if (gridBind.isBlank()) {
                continue;
            }
            String gridId = grid.getAttribute("id");
            if (gridId.isBlank()) {
                gridId = gridBind.replace("-bind", "-grid");
            }
            String expr = bindRelevant.get(gridBind);
            if (expr == null || expr.isBlank()) {
                continue;
            }
            elementos.add(construirElemento(
                    gridId,
                    gridBind,
                    "grid",
                    "Grid en " + titulosSeccion.getOrDefault(sectionId, sectionId),
                    expr,
                    sectionId,
                    disparadoresUsados
            ));
        }
    }

    private DependenciaVisibilidad construirElemento(String id, String bindId, String tipoElemento,
                                                     String titulo, String expresion,
                                                     String seccionPadreId,
                                                     Set<String> disparadoresUsados) {
        DependenciaVisibilidad dep = new DependenciaVisibilidad();
        dep.setId(id);
        dep.setBindId(bindId);
        dep.setTipoElemento(tipoElemento);
        dep.setTitulo(titulo);
        dep.setExpresionRelevant(expresion);
        dep.setSeccionPadreId(seccionPadreId);
        dep.setTipoVisibilidad(clasificar(expresion));
        List<String> refs = extraerReferencias(expresion);
        dep.setDependeDe(refs);
        List<String> desc = new ArrayList<>();
        for (String ref : refs) {
            disparadoresUsados.add(ref);
            desc.add(GLOSARIO.getOrDefault(ref, "$" + ref));
        }
        dep.setDescripcionesDependencias(desc);
        return dep;
    }

    private Map<String, String> construirMapaRelevant(Document doc, XPath xpath) throws Exception {
        Map<String, String> mapa = new LinkedHashMap<>();
        NodeList binds = (NodeList) xpath.evaluate("//*[local-name()='bind' and @id]", doc, XPathConstants.NODESET);
        for (int i = 0; i < binds.getLength(); i++) {
            Element bind = (Element) binds.item(i);
            if (bind.hasAttribute("relevant")) {
                mapa.put(bind.getAttribute("id"), bind.getAttribute("relevant"));
            }
        }
        return mapa;
    }

    private Map<String, String> construirTitulosSeccion(Document doc, XPath xpath) throws Exception {
        Map<String, String> titulos = new LinkedHashMap<>();
        NodeList labels = (NodeList) xpath.evaluate(
                "//*[local-name()='section']/*[local-name()='label' and @ref]",
                doc,
                XPathConstants.NODESET
        );
        for (int i = 0; i < labels.getLength(); i++) {
            Element label = (Element) labels.item(i);
            Element section = (Element) label.getParentNode();
            String ref = label.getAttribute("ref");
            String key = ref.replace("$form-resources/", "").replace("/label", "");
            titulos.put(section.getAttribute("id"), formatearTitulo(key));
        }
        return titulos;
    }

    private String formatearTitulo(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return key.replace("-section", "").replace("-", " ");
    }

    String clasificar(String expresion) {
        if (expresion == null || expresion.isBlank()) {
            return "siempre_visible";
        }
        String norm = expresion.replaceAll("\\s+", "");
        if ("false()".equals(norm)) {
            return "oculta_fija";
        }
        if ("true()".equals(norm)) {
            return "visible_fija";
        }
        if (norm.contains("fr:mode()") && norm.contains("pdf")) {
            return "solo_pdf";
        }
        return "condicional";
    }

    List<String> extraerReferencias(String expresion) {
        List<String> refs = new ArrayList<>();
        if (expresion == null || expresion.isBlank()) {
            return refs;
        }
        Matcher m = REFERENCIA_BIND.matcher(expresion);
        Set<String> vistos = new LinkedHashSet<>();
        while (m.find()) {
            String ref = m.group(1);
            if (!vistos.contains(ref)) {
                vistos.add(ref);
                refs.add(ref);
            }
        }
        return refs;
    }

    /**
     * Resuelve el bind de una sección o grid y devuelve su expresión relevant actual.
     */
    public String obtenerRelevantActual(String xml, String bindId, String sectionId) {
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            if (bindId == null || bindId.isBlank()) {
                Element section = OrbeonXmlUtil.buscarPorId(doc, sectionId);
                if (section == null) {
                    throw new IllegalArgumentException("Sección no encontrada: " + sectionId);
                }
                bindId = section.getAttribute("bind");
            }
            Element bind = OrbeonXmlUtil.buscarPorId(doc, bindId);
            if (bind == null) {
                throw new IllegalArgumentException("Bind no encontrado: " + bindId);
            }
            return bind.hasAttribute("relevant") ? bind.getAttribute("relevant") : "";
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
