package com.orbeon.editor.service;

import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.EstructuraFormulario;
import com.orbeon.editor.model.ImagenFormulario;
import com.orbeon.editor.model.ItemSelect;
import com.orbeon.editor.model.SeccionFormulario;
import com.orbeon.editor.model.RecursoFormulario;
import com.orbeon.editor.util.OrbeonResourceParser;
import com.orbeon.editor.util.OrbeonXmlUtil;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parseo jerárquico del XML Orbeon: secciones, campos enriquecidos, imágenes e instancias.
 */
@Service
public class OrbeonStructureService {

    private static final Pattern REF_RECURSO = Pattern.compile("\\$form-resources/([^/]+)/");

    private static final String[][] TIPOS_CAMPO = {
            {"input", "input"}, {"select1", "select1"}, {"select", "select"},
            {"textarea", "textarea"}, {"number", "number"}, {"date", "date"},
            {"checkbox-input", "checkbox"}, {"databound-select1", "databound-select1"},
            {"explanation", "explanation"}, {"text", "text"}, {"image", "image"},
            {"attachment", "attachment"}, {"image-attachment", "image-attachment"},
            {"yesno-input", "yesno-input"}
    };

    public EstructuraFormulario parsearEstructuraCompleta(String xml) {
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Map<String, RecursoFormulario> resources = OrbeonResourceParser.extraerRecursos(doc, xpath);
            Map<String, String> bindToRef = construirMapaBindARef(doc, xpath);

            EstructuraFormulario estructura = new EstructuraFormulario();
            estructura.setTitulo(obtenerTitulo(doc, xpath));
            estructura.setInstancias(obtenerInstancias(doc, xpath));
            estructura.setImagenes(extraerImagenes(doc, xpath));
            estructura.setSecciones(extraerSecciones(doc, xpath, resources, bindToRef));
            return estructura;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al parsear estructura: " + e.getMessage(), e);
        }
    }

    private List<SeccionFormulario> extraerSecciones(Document doc, XPath xpath,
                                                      Map<String, RecursoFormulario> resources,
                                                      Map<String, String> bindToRef) throws Exception {
        List<SeccionFormulario> secciones = new ArrayList<>();
        NodeList sectionNodes = (NodeList) xpath.evaluate(
                "//*[local-name()='view']//*[local-name()='section' and @id]",
                doc, XPathConstants.NODESET
        );

        for (int i = 0; i < sectionNodes.getLength(); i++) {
            Element section = (Element) sectionNodes.item(i);
            SeccionFormulario sec = new SeccionFormulario();
            sec.setId(section.getAttribute("id"));
            sec.setBind(section.getAttribute("bind"));
            sec.setCssClass(section.getAttribute("class"));
            sec.setNoPrintInPdf(sec.getCssClass() != null && sec.getCssClass().contains("noprintinpdf"));
            sec.setTitulo(resolverTituloSeccion(section, doc, xpath, resources));
            sec.setGridCount(contarGrids(section));
            sec.setComponentes(extraerCamposSeccion(section, resources, bindToRef, doc, xpath));
            secciones.add(sec);
        }
        return secciones;
    }

    private List<ComponenteFormulario> extraerCamposSeccion(Element section,
                                                              Map<String, RecursoFormulario> resources,
                                                              Map<String, String> bindToRef,
                                                              Document doc, XPath xpath) throws Exception {
        List<ComponenteFormulario> campos = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (String[] tipo : TIPOS_CAMPO) {
            NodeList nodes = section.getElementsByTagNameNS("*", tipo[0]);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String id = el.getAttribute("id");
                if (id.isBlank() || ids.contains(id)) {
                    continue;
                }
                ids.add(id);
                campos.add(construirCampo(el, tipo[1], resources, bindToRef, doc, xpath));
            }
        }
        return campos;
    }

    private ComponenteFormulario construirCampo(Element el, String tipo,
                                                   Map<String, RecursoFormulario> resources,
                                                   Map<String, String> bindToRef,
                                                   Document doc, XPath xpath) throws Exception {
        String id = el.getAttribute("id");
        String bind = el.getAttribute("bind");
        String clave = bind.endsWith("-bind") ? bind.substring(0, bind.length() - 5)
                : id.endsWith("-control") ? id.substring(0, id.length() - 8) : id;

        RecursoFormulario res = resources.getOrDefault(clave, new RecursoFormulario());
        ComponenteFormulario c = new ComponenteFormulario();
        c.setId(id);
        c.setTipo(tipo);
        c.setLabel(res.getLabel());
        c.setHint(res.getHint());
        c.setAlert(res.getAlert());

        if (el.hasAttribute("appearance")) {
            c.setAppearance(el.getAttribute("appearance"));
        }

        Map<String, String> meta = new HashMap<>();
        meta.put("resourceKey", clave);
        meta.put("bind", bind);

        if ("image".equals(tipo) || "attachment".equals(tipo) || "image-attachment".equals(tipo)) {
            String ref = el.getAttribute("ref");
            if (ref.isBlank() && bindToRef.containsKey(bind)) {
                ref = obtenerValorInstancia(doc, xpath, bindToRef.get(bind));
                meta.put("rutaInstancia", bindToRef.get(bind));
            }
            meta.put("ref", ref != null ? ref : "");
        }
        c.setMetadatos(meta);

        if ("select1".equals(tipo) || "select".equals(tipo) || "databound-select1".equals(tipo)) {
            OrbeonResourceParser.configurarItemsSelect(el, clave, resources, c);
        }
        return c;
    }

    private int contarGrids(Element section) {
        return section.getElementsByTagNameNS("*", "grid").getLength();
    }

    private String resolverTituloSeccion(Element section, Document doc, XPath xpath,
                                           Map<String, RecursoFormulario> resources) throws Exception {
        NodeList labels = section.getElementsByTagNameNS("*", "label");
        for (int i = 0; i < labels.getLength(); i++) {
            Element labelEl = (Element) labels.item(i);
            String ref = labelEl.getAttribute("ref");
            if (!ref.isBlank()) {
                Matcher m = REF_RECURSO.matcher(ref);
                if (m.find()) {
                    RecursoFormulario res = resources.get(m.group(1));
                    if (res != null && !res.getLabel().isBlank()) {
                        return res.getLabel();
                    }
                }
            }
        }
        String bind = section.getAttribute("bind");
        if (bind.endsWith("-bind")) {
            RecursoFormulario res = resources.get(bind.substring(0, bind.length() - 5));
            if (res != null && !res.getLabel().isBlank()) {
                return res.getLabel();
            }
        }
        return section.getAttribute("id").replace("-section", "").replace("-", " ");
    }

    private List<ImagenFormulario> extraerImagenes(Document doc, XPath xpath) throws Exception {
        List<ImagenFormulario> imagenes = new ArrayList<>();
        NodeList nodos = (NodeList) xpath.evaluate(
                "//*[local-name()='instance' and @id='fr-form-instance']//*[contains(local-name(), 'img')]",
                doc, XPathConstants.NODESET
        );
        for (int i = 0; i < nodos.getLength(); i++) {
            Element el = (Element) nodos.item(i);
            ImagenFormulario img = new ImagenFormulario();
            img.setTag(el.getLocalName());
            img.setFilename(el.getAttribute("filename"));
            img.setMediatype(el.getAttribute("mediatype"));
            img.setSrc(el.getTextContent().trim());
            imagenes.add(img);
        }
        return imagenes;
    }

    private List<String> obtenerInstancias(Document doc, XPath xpath) throws Exception {
        List<String> ids = new ArrayList<>();
        NodeList instances = (NodeList) xpath.evaluate(
                "//*[local-name()='instance' and @id]", doc, XPathConstants.NODESET
        );
        for (int i = 0; i < instances.getLength(); i++) {
            ids.add(((Element) instances.item(i)).getAttribute("id"));
        }
        return ids;
    }

    private String obtenerTitulo(Document doc, XPath xpath) throws Exception {
        Node n = (Node) xpath.evaluate("//*[local-name()='title']", doc, XPathConstants.NODE);
        return n != null ? n.getTextContent().trim() : "Formulario Orbeon";
    }

    private Map<String, String> construirMapaBindARef(Document doc, XPath xpath) throws Exception {
        Map<String, String> mapa = new LinkedHashMap<>();
        NodeList binds = (NodeList) xpath.evaluate(
                "//*[local-name()='bind' and @id and @ref]", doc, XPathConstants.NODESET
        );
        for (int i = 0; i < binds.getLength(); i++) {
            Element b = (Element) binds.item(i);
            mapa.put(b.getAttribute("id"), b.getAttribute("ref"));
        }
        return mapa;
    }

    private String obtenerValorInstancia(Document doc, XPath xpath, String ref) throws Exception {
        String expr = String.format(
                "//*[local-name()='instance' and @id='fr-form-instance']//*[local-name()='%s']",
                OrbeonXmlUtil.escaparXPathLiteral(ref)
        );
        Node n = (Node) xpath.evaluate(expr, doc, XPathConstants.NODE);
        return n != null ? n.getTextContent().trim() : "";
    }
}
