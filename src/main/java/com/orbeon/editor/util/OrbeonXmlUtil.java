package com.orbeon.editor.util;

import com.orbeon.editor.model.EtiquetaControlNumerico;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OrbeonXmlUtil {

    /** Etiquetas de instancia, ids Orbeon y resources con nombre {@code control-N}. */
    private static final Pattern[] PATRONES_CONTROL_NUMERICO = {
            Pattern.compile("<(?:/)?(control-\\d+)(?=[\\s/>])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("id=[\"'](control-\\d+)-(?:control|bind)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:ref|name)=[\"'](control-\\d+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\$form-resources/(control-\\d+)/", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern NOMBRE_CONTROL_NUMERICO =
            Pattern.compile("^control-\\d+$", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOMBRE_CAMPO_VALIDO =
            Pattern.compile("^[a-zA-Z][\\w.-]*$");

    private OrbeonXmlUtil() {
    }

    /**
     * Detecta nombres de etiqueta {@code control-N} (N numérico) en el XML en bruto.
     */
    public static List<String> detectarEtiquetasControlNumerico(String xml) {
        TreeSet<String> encontradas = new TreeSet<>((a, b) -> {
            int na = Integer.parseInt(a.substring(a.indexOf('-') + 1));
            int nb = Integer.parseInt(b.substring(b.indexOf('-') + 1));
            int cmp = Integer.compare(na, nb);
            return cmp != 0 ? cmp : a.compareToIgnoreCase(b);
        });
        if (xml == null || xml.isBlank()) {
            return new ArrayList<>();
        }
        for (Pattern patron : PATRONES_CONTROL_NUMERICO) {
            Matcher matcher = patron.matcher(xml);
            while (matcher.find()) {
                encontradas.add(matcher.group(1).toLowerCase());
            }
        }
        return new ArrayList<>(encontradas);
    }

    public static List<EtiquetaControlNumerico> analizarEtiquetasControlNumerico(String xml) throws Exception {
        List<String> nombres = detectarEtiquetasControlNumerico(xml);
        List<EtiquetaControlNumerico> lista = new ArrayList<>();
        if (nombres.isEmpty()) {
            return lista;
        }
        Document doc = null;
        try {
            doc = parsear(xml);
        } catch (Exception ignored) {
            // Análisis parcial sin DOM si el XML no parsea
        }
        for (String nombre : nombres) {
            EtiquetaControlNumerico item = new EtiquetaControlNumerico();
            item.setNombre(nombre);
            item.setBindId(nombre + "-bind");
            item.setControlId(nombre + "-control");
            item.setOcurrencias(contarReferenciasCampo(xml, nombre));
            if (doc != null) {
                Element control = buscarPorId(doc, nombre + "-control");
                if (control != null) {
                    item.setTipoControl(control.getLocalName());
                }
            }
            lista.add(item);
        }
        return lista;
    }

    public static String renombrarEtiquetaControlNumerico(String xml, String nombreActual, String nombreNuevo) throws Exception {
        validarRenombreControlNumerico(nombreActual, nombreNuevo);
        Document doc = parsear(xml);
        renombrarEtiquetaControlNumerico(doc, nombreActual.toLowerCase(), nombreNuevo);
        return serializar(doc);
    }

    public static void renombrarEtiquetaControlNumerico(Document doc, String nombreActual, String nombreNuevo) {
        validarRenombreControlNumerico(nombreActual, nombreNuevo);
        String viejo = nombreActual.toLowerCase();
        String nuevo = nombreNuevo.trim();
        String viejoBind = viejo + "-bind";
        String nuevoBind = nuevo + "-bind";
        String viejoControl = viejo + "-control";
        String nuevoControl = nuevo + "-control";

        List<Element> etiquetasInstancia = new ArrayList<>();
        NodeList todos = doc.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Node n = todos.item(i);
            if (n instanceof Element el && viejo.equals(el.getLocalName())) {
                etiquetasInstancia.add(el);
            }
        }
        for (Element el : etiquetasInstancia) {
            renombrarEtiquetaElemento(doc, el, nuevo);
        }

        for (int i = 0; i < todos.getLength(); i++) {
            Node n = todos.item(i);
            if (!(n instanceof Element el)) {
                continue;
            }
            if (viejoBind.equals(el.getAttribute("id"))) {
                el.setAttribute("id", nuevoBind);
                el.setAttribute("ref", nuevo);
                el.setAttribute("name", nuevo);
            } else if (viejoControl.equals(el.getAttribute("id"))) {
                el.setAttribute("id", nuevoControl);
                if (viejoBind.equals(el.getAttribute("bind"))) {
                    el.setAttribute("bind", nuevoBind);
                }
            }
            if (viejo.equals(el.getAttribute("ref")) || viejo.equals(el.getAttribute("name"))) {
                if (viejo.equals(el.getAttribute("ref"))) {
                    el.setAttribute("ref", nuevo);
                }
                if (viejo.equals(el.getAttribute("name"))) {
                    el.setAttribute("name", nuevo);
                }
            }
            if (viejoBind.equals(el.getAttribute("bind"))) {
                el.setAttribute("bind", nuevoBind);
            }
            if (el.hasAttribute("ref")) {
                String ref = el.getAttribute("ref");
                String pathViejo = "$form-resources/" + viejo + "/";
                String pathNuevo = "$form-resources/" + nuevo + "/";
                if (ref.contains(pathViejo)) {
                    el.setAttribute("ref", ref.replace(pathViejo, pathNuevo));
                }
            }
        }
    }

    private static void validarRenombreControlNumerico(String nombreActual, String nombreNuevo) {
        if (nombreActual == null || !NOMBRE_CONTROL_NUMERICO.matcher(nombreActual.trim()).matches()) {
            throw new IllegalArgumentException("Nombre actual inválido (se espera control-N): " + nombreActual);
        }
        if (nombreNuevo == null || nombreNuevo.isBlank()) {
            throw new IllegalArgumentException("El nuevo nombre es obligatorio");
        }
        String nuevo = nombreNuevo.trim();
        if (NOMBRE_CONTROL_NUMERICO.matcher(nuevo).matches()) {
            throw new IllegalArgumentException("El nuevo nombre no puede seguir el patrón control-N");
        }
        if (!NOMBRE_CAMPO_VALIDO.matcher(nuevo).matches()) {
            throw new IllegalArgumentException("Nombre de campo inválido: " + nuevo);
        }
        if (nombreActual.equalsIgnoreCase(nuevo)) {
            throw new IllegalArgumentException("El nuevo nombre debe ser distinto del actual");
        }
    }

    private static int contarReferenciasCampo(String xml, String nombre) {
        if (xml == null || nombre == null) {
            return 0;
        }
        String[] agujas = {
                "<" + nombre + ">",
                "<" + nombre + "/",
                "<" + nombre + " ",
                "</" + nombre + ">",
                "id=\"" + nombre + "-bind\"",
                "id=\"" + nombre + "-control\"",
                "ref=\"" + nombre + "\"",
                "name=\"" + nombre + "\"",
                "bind=\"" + nombre + "-bind\"",
                "$form-resources/" + nombre + "/"
        };
        int total = 0;
        for (String aguja : agujas) {
            int idx = 0;
            while ((idx = xml.indexOf(aguja, idx)) >= 0) {
                total++;
                idx++;
            }
        }
        return total;
    }

    private static void renombrarEtiquetaElemento(Document doc, Element viejo, String nuevoNombre) {
        Element nuevo = doc.createElementNS(viejo.getNamespaceURI(), nuevoNombre);
        NamedNodeMap attrs = viejo.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            nuevo.setAttributeNS(attr.getNamespaceURI(), attr.getNodeName(), attr.getNodeValue());
        }
        while (viejo.hasChildNodes()) {
            nuevo.appendChild(viejo.getFirstChild());
        }
        viejo.getParentNode().replaceChild(nuevo, viejo);
    }

    public static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    public static String serializar(Document documento) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(documento), new StreamResult(writer));
        return writer.toString();
    }

    public static Element buscarPorId(Document doc, String id) {
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (n instanceof Element el && id.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }

    public static String escaparXPathLiteral(String valor) {
        if (valor == null) {
            return "";
        }
        if (!valor.contains("'")) {
            return valor;
        }
        return valor.replace("'", "', \"'\", '");
    }
}
