package com.orbeon.editor;

import com.orbeon.editor.util.OrbeonXmlUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbeonXmlUtilTest {

    @Test
    void detectaEtiquetasControlNumericoEnFragmento() {
        String xml = """
                <form>
                    <control-1/>
                    <xf:bind id="control-1-bind" ref="control-1" name="control-1"/>
                    <control-2><valor/></control-2>
                </form>
                """;
        List<String> tags = OrbeonXmlUtil.detectarEtiquetasControlNumerico(xml);
        assertTrue(tags.contains("control-1"));
        assertTrue(tags.contains("control-2"));
    }

    @Test
    void detectaPorIdControl() {
        String xml = "<xf:input id=\"control-2-control\" bind=\"control-2-bind\"/>";
        List<String> tags = OrbeonXmlUtil.detectarEtiquetasControlNumerico(xml);
        assertTrue(tags.contains("control-2"));
    }

    @Test
    void renombraControlNumericoEnFragmento() throws Exception {
        String xml = """
                <form>
                    <control-1/>
                    <xf:bind xmlns:xf="http://www.w3.org/1999/xforms" id="control-1-bind" ref="control-1" name="control-1"/>
                    <fr:explanation xmlns:fr="http://orbeon.org/oxf/xml/form-builder" id="control-1-control" bind="control-1-bind">
                        <fr:text xmlns:fr="http://orbeon.org/oxf/xml/form-builder" ref="$form-resources/control-1/text"/>
                    </fr:explanation>
                    <control-1><text>hola</text></control-1>
                </form>
                """;
        String out = OrbeonXmlUtil.renombrarEtiquetaControlNumerico(xml, "control-1", "avisoColectivo");
        assertFalse(out.contains("control-1-control"));
        assertTrue(out.contains("avisoColectivo-control"));
        assertTrue(out.contains("avisoColectivo-bind"));
        assertTrue(out.contains("$form-resources/avisoColectivo/"));
        assertTrue(out.contains("<avisoColectivo>"));
    }
}
