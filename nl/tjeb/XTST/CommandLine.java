/*
 * Copyright (c) 2016 Jelte Jansen
 *
 * This file is part of the XSLT Transformation Server Tool (XTST).
 *
 * XTST is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * XTST is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with XTST.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.tjeb.XTST;

import javax.xml.transform.TransformerException;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import javax.xml.validation.*;
import javax.xml.XMLConstants;
import java.io.*;
import java.util.ArrayList;
import javax.xml.transform.stream.StreamSource;

import static net.sourceforge.argparse4j.impl.Arguments.storeTrue;


/**
 * Command line handler class
 */
public class CommandLine {
    int port;
    String host;
    String xmlFile;
    boolean multimode;
    String xsltFile;
    String xsdFile;
    int checkEverySeconds;

    public CommandLine(String[] args) {
        host = "localhost";
        port = 35791;
        xmlFile = null;
        multimode = false;
        xsltFile = null;
        xsdFile = null;
        checkEverySeconds = 30;

        parseArguments(args);
    }

    private void parseArguments(String[] argv) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("XTST")
                .defaultHelp(true)
                .description("Transform an XML file according to an XSLT file");
        parser.addArgument("-a", "--address")
                .help("Listen on the given hostname or IP address (defaults to localhost)");
        parser.addArgument("-p", "--port")
                .type(Integer.class)
                .help("Listen on the given port number (defaults to 35791)");
        parser.addArgument("-f", "--file")
                .help("Do not start a server but just transform the given xml file");
        parser.addArgument("-c", "--check")
                .type(Integer.class)
                .help("Check the xsl file every X seconds (defaults to 30)");
        parser.addArgument("xslt_file_or_directory")
                .help("XSLT file to use for transformations");
        parser.addArgument("xsd_file").nargs("?").help("XSD schema to validate against");
        parser.addArgument("-m", "--multimode")
                .action(storeTrue())
                .help("Run in multimode; read transformations and xsds from a directory tree");
        Namespace ns = null;
        // for multimode: each subdirectory is considered a transformation
        // if it contains a file called 'xtst.properties' with the following
        // values:
        // keyword
        // xsl_file
        // xsd_file (optional)

        try {
            ns = parser.parseArgs(argv);

            // sanity checks
            if (ns.getBoolean("multimode")) {
                multimode = true;
                if (ns.get("xsd_file") != null) {
                    System.out.println("Cannot run in multimode and specify a single xsd file");
                    System.exit(1);
                }
            }

            if (ns.get("address") != null) {
                host = ns.get("address");
            }
            if (ns.get("port") != null) {
                port = ((Integer)ns.get("port")).intValue();
            }
            if (ns.get("file") != null) {
                xmlFile = ns.get("file");
            }
            if (ns.get("check") != null) {
                checkEverySeconds = ((Integer)ns.get("check")).intValue();
            }
            xsltFile = ns.get("xslt_file_or_directory");
            xsdFile = ns.get("xsd_file");
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }

    /**
     * There are two run modes: single file, and server mode
     * Mode depends on whether xmlFile is set, in which case
     * we will only transform the given file
     */
    public void run() {
        if (xmlFile != null) {
            try {
                if (xsdFile != null) {
                    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                    Schema schema = schemaFactory.newSchema(new File(xsdFile));
                    Validator XSDValidator = schema.newValidator();

                    StreamSource source = new StreamSource(new File(xmlFile));
                    XSDValidator.validate(source);
                }
                ArrayList<String> xsltFiles = new ArrayList<String>();
                xsltFiles.add(xsltFile);
                XSLTTransformer sv = new XSLTTransformer(xsltFiles);

                String result = sv.transformFile(xmlFile);
                System.out.println(result);
            } catch (TransformerException te) {
                te.printStackTrace();
                System.exit(1);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                System.exit(1);
            } catch (Exception xpe) {
                xpe.printStackTrace();
                System.exit(1);
                }

        } else {
            try {
                DocumentHandlerManager manager = new DocumentHandlerManager(multimode, xsltFile, xsdFile, checkEverySeconds);

                if (manager.getDocumentHandlerCount() == 0) {
                    System.out.println("Warning: no directories with xtst.properties found in " + xsltFile);
                }

                Thread t = new Server(host, port, multimode, manager);
                t.start();
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    public static void main(String [] args) {
        CommandLine cl = new CommandLine(args);
        cl.run();
    }

}
