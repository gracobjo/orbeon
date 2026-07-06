package com.orbeon.editor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.EstructuraFormulario;
import com.orbeon.editor.model.SeccionFormulario;
import com.orbeon.editor.util.OrbeonXmlUtil;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Construye un catálogo de reglas a partir del XML Orbeon cargado,
 * para que el intérprete de PDF funcione con cualquier formulario.
 */
@Service
public class OrbeonInstructionsCatalogBuilder {

    private static final Pattern TAG_TEXTO = Pattern.compile("texto([A-Za-z0-9].*)", Pattern.CASE_INSENSITIVE);
    private static final int FRAGMENTO_MIN = 18;

    private final OrbeonStructureService structureService;
    private final ObjectMapper objectMapper;

    public OrbeonInstructionsCatalogBuilder(OrbeonStructureService structureService,
                                            ObjectMapper objectMapper) {
        this.structureService = structureService;
        this.objectMapper = objectMapper;
    }

    public ObjectNode construirDesdeXml(String xml) {
        ObjectNode catalogo = objectMapper.createObjectNode();
        catalogo.put("formulario", detectarNombreFormulario(xml));
        catalogo.put("origen", "xml-cargado");
        catalogo.set("reglasDeclaracion", construirReglasDeclaracion(xml));
        catalogo.set("reglasAnexo", construirReglasAnexo(xml));
        catalogo.set("reglasTexto", construirReglasTexto(xml));
        catalogo.set("reglasApartado", construirReglasApartado(xml));
        return catalogo;
    }

    private String detectarNombreFormulario(String xml) {
        int idx = xml.indexOf("application-name");
        if (idx < 0) {
            return "formulario";
        }
        int start = xml.indexOf('>', idx);
        int end = xml.indexOf('<', start + 1);
        if (start > 0 && end > start) {
            String nombre = xml.substring(start + 1, end).trim();
            if (!nombre.isBlank()) {
                return nombre;
            }
        }
        return "formulario";
    }

    private ArrayNode construirReglasDeclaracion(String xml) {
        ArrayNode reglas = objectMapper.createArrayNode();
        for (RecursoTexto recurso : indexarRecursosTexto(xml)) {
            if (!esRecursoDeclaracion(recurso.id)) {
                continue;
            }
            List<String> campos = resolverControlesEliminacion(recurso, xml);
            if (campos.isEmpty() || recurso.textoPlano.length() < FRAGMENTO_MIN) {
                continue;
            }
            ObjectNode regla = objectMapper.createObjectNode();
            regla.put("id", "decl-" + sanitizarId(recurso.id));
            regla.put("fragmentoTexto", truncarFragmento(recurso.textoPlano));
            ArrayNode eliminar = regla.putArray("camposEliminar");
            campos.forEach(eliminar::add);
            reglas.add(regla);
        }
        return reglas;
    }

    private ArrayNode construirReglasAnexo(String xml) {
        ArrayNode reglas = objectMapper.createArrayNode();
        for (RecursoTexto recurso : indexarRecursosTexto(xml)) {
            if (!esRecursoAnexo(recurso.id)) {
                continue;
            }
            List<String> campos = resolverControlesEliminacion(recurso, xml);
            if (campos.isEmpty() || recurso.textoPlano.length() < FRAGMENTO_MIN) {
                continue;
            }
            ObjectNode regla = objectMapper.createObjectNode();
            regla.put("id", "anexo-" + sanitizarId(recurso.id));
            regla.put("fragmentoTexto", truncarFragmento(recurso.textoPlano));
            ArrayNode eliminar = regla.putArray("camposEliminar");
            campos.forEach(eliminar::add);
            reglas.add(regla);
        }
        return reglas;
    }

    private ArrayNode construirReglasTexto(String xml) {
        ArrayNode reglas = objectMapper.createArrayNode();
        for (RecursoTexto recurso : indexarRecursosTexto(xml)) {
            if (recurso.textoPlano.length() < FRAGMENTO_MIN) {
                continue;
            }
            if (esRecursoDeclaracion(recurso.id) || esRecursoAnexo(recurso.id)) {
                continue;
            }
            if (!recurso.id.contains("-texto") && !recurso.id.contains("texto")) {
                continue;
            }
            ObjectNode regla = objectMapper.createObjectNode();
            regla.put("id", "texto-" + sanitizarId(recurso.id));
            regla.put("fieldId", recurso.id);
            regla.put("descripcion", "Eliminar o revisar texto: " + truncarFragmento(recurso.textoPlano));
            regla.put("fragmentoDeteccion", truncarFragmento(recurso.textoPlano));
            reglas.add(regla);
        }
        return reglas;
    }

    private ArrayNode construirReglasApartado(String xml) {
        ArrayNode reglas = objectMapper.createArrayNode();
        try {
            EstructuraFormulario estructura = structureService.parsearEstructuraCompleta(xml);
            for (SeccionFormulario seccion : estructura.getSecciones()) {
                if (seccion.getTitulo() == null || seccion.getTitulo().isBlank()) {
                    continue;
                }
                String titulo = normalizar(seccion.getTitulo());
                if (titulo.length() < 8) {
                    continue;
                }
                List<String> campos = new ArrayList<>();
                if (seccion.getComponentes() != null) {
                    for (ComponenteFormulario c : seccion.getComponentes()) {
                        if (c.getId() != null && !c.getId().isBlank()) {
                            campos.add(c.getId());
                        }
                    }
                }
                if (campos.isEmpty()) {
                    continue;
                }
                ObjectNode regla = objectMapper.createObjectNode();
                regla.put("id", "apartado-" + sanitizarId(seccion.getId()));
                regla.put("descripcion", "Eliminar apartado: " + seccion.getTitulo());
                regla.put("intencion", "eliminar-apartado");
                ArrayNode patrones = regla.putArray("patrones");
                patrones.add(titulo);
                if (titulo.length() > 20) {
                    patrones.add(titulo.substring(0, Math.min(40, titulo.length())));
                }
                ArrayNode paginas = regla.putArray("paginas");
                paginas.add(1);
                ArrayNode eliminar = regla.putArray("camposEliminar");
                campos.forEach(eliminar::add);
                reglas.add(regla);
            }
        } catch (Exception ignored) {
            // estructura no parseable
        }
        return reglas;
    }

    private List<RecursoTexto> indexarRecursosTexto(String xml) {
        List<RecursoTexto> resultado = new ArrayList<>();
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            NodeList instances = doc.getElementsByTagNameNS("*", "instance");
            for (int i = 0; i < instances.getLength(); i++) {
                Element inst = (Element) instances.item(i);
                if (!"fr-form-resources".equals(inst.getAttribute("id"))) {
                    continue;
                }
                NodeList all = inst.getElementsByTagNameNS("*", "*");
                for (int j = 0; j < all.getLength(); j++) {
                    if (!(all.item(j) instanceof Element el)) {
                        continue;
                    }
                    Element textEl = primerHijo(el, "text");
                    if (textEl == null) {
                        continue;
                    }
                    String texto = desescaparHtml(textEl.getTextContent());
                    if (texto.isBlank()) {
                        continue;
                    }
                    RecursoTexto r = new RecursoTexto();
                    r.id = el.getLocalName();
                    r.textoPlano = texto;
                    resultado.add(r);
                }
            }
        } catch (Exception ignored) {
            // sin resources
        }
        return resultado;
    }

    private List<String> resolverControlesEliminacion(RecursoTexto recurso, String xml) {
        Set<String> campos = new LinkedHashSet<>();
        agregarSiExiste(xml, campos, recurso.id + "-control");
        agregarSiExiste(xml, campos, recurso.id.replace("-texto", "-") + "-control");

        var matcher = TAG_TEXTO.matcher(recurso.id);
        if (matcher.find()) {
            String sufijo = matcher.group(1);
            int dash = recurso.id.indexOf("-texto");
            if (dash > 0) {
                String prefijo = recurso.id.substring(0, dash);
                agregarSiExiste(xml, campos, prefijo + "-" + sufijo + "-control");
                agregarSiExiste(xml, campos, prefijo + "-texto" + sufijo + "-control");
                if (!sufijo.isEmpty()) {
                    String s0 = sufijo.substring(0, 1).toLowerCase(Locale.ROOT) + sufijo.substring(1);
                    agregarSiExiste(xml, campos, prefijo + "-" + s0 + "-control");
                }
            }
        }

        if (recurso.id.startsWith("anexos-texto")) {
            String base = recurso.id.substring("anexos-texto".length());
            agregarSiExiste(xml, campos, "anexos-" + base + "-control");
            agregarSiExiste(xml, campos, "anexos-texto" + base + "-control");
        }
        return new ArrayList<>(campos);
    }

    private void agregarSiExiste(String xml, Set<String> campos, String fieldId) {
        if (fieldId != null && !fieldId.isBlank() && xml.contains("id=\"" + fieldId + "\"")) {
            campos.add(fieldId);
        }
    }

    private boolean esRecursoDeclaracion(String id) {
        String n = id.toLowerCase(Locale.ROOT);
        return n.contains("declaracion") || n.contains("declaracionesresponsables");
    }

    private boolean esRecursoAnexo(String id) {
        String n = id.toLowerCase(Locale.ROOT);
        return n.startsWith("anexos-") || n.contains("-anexo") || n.contains("anexo-");
    }

    private String truncarFragmento(String texto) {
        String limpio = desescaparHtml(texto).replaceAll("\\s+", " ").trim();
        if (limpio.length() <= 120) {
            return limpio;
        }
        return limpio.substring(0, 120);
    }

    private String sanitizarId(String id) {
        return id.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private Element primerHijo(Element padre, String localName) {
        NodeList children = padre.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    private String desescaparHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replaceAll("<[^>]+>", " ");
    }

    private String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static final class RecursoTexto {
        private String id;
        private String textoPlano;
    }
}
