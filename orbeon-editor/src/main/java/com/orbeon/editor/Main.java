package com.orbeon.editor;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.File;
import java.net.URL;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String xmlPath = null;

        // Leer argumentos de línea de comandos
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--xml".equals(args[i]) && i + 1 < args.length) {
                xmlPath = args[++i];
            } else if (args[i].endsWith(".xml") || args[i].endsWith(".txt")) {
                xmlPath = args[i];
            }
        }

        // Validar XML si se especificó
        if (xmlPath != null) {
            File f = new File(xmlPath);
            if (!f.exists()) {
                System.err.println("ERROR: No se encuentra el fichero: " + xmlPath);
                System.exit(1);
            }
            OrbeonXmlService.getInstance().loadXml(f);
            System.out.println("XML cargado: " + f.getAbsolutePath());
        }

        // Configurar servidor Jetty
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Recursos estáticos (HTML/CSS/JS embebidos)
        URL staticResource = Main.class.getResource("/static");
        if (staticResource != null) {
            context.setResourceBase(staticResource.toExternalForm());
        } else {
            context.setResourceBase("src/main/resources/static");
        }

        // Servlets API REST
        context.addServlet(new ServletHolder(new ApiServlet()), "/api/*");
        context.addServlet(new ServletHolder(new StaticHtmlServlet()), "/");

        // Servlet por defecto para recursos estáticos
        ServletHolder defaultHolder = new ServletHolder("default", DefaultServlet.class);
        defaultHolder.setInitParameter("dirAllowed", "false");
        context.addServlet(defaultHolder, "/static/*");

        server.setHandler(context);
        server.start();

        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║        Orbeon XML Editor arrancado                ║");
        System.out.println("║  Accede en: http://localhost:" + port + "               ║");
        System.out.println("╚═══════════════════════════════════════════════════╝");
        if (xmlPath == null) {
            System.out.println("  Tip: Carga un XML desde la interfaz web o con:");
            System.out.println("       java -jar orbeon-editor.jar --xml ruta/formulario.xml");
        }

        server.join();
    }
}
