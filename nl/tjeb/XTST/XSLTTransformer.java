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

/*
 * THIS VERSION IS WORK IN PROGRESS
 * TODO ITEMS:
 *  - find general way to merge while adhering to xsd
 *  - add text nodes at the correct location (i.e. if following a moved node, add it after that
 *  - can we transform directly to new xml document, without passing through String first and parsing that again?
 *
 */
package nl.tjeb.XTST;

import java.io.StringWriter;
import java.io.File;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.StringReader;

import java.util.ArrayList;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import javax.xml.parsers.*;
import javax.xml.transform.OutputKeys;

import net.sf.saxon.Configuration;
import net.sf.saxon.TransformerFactoryImpl;

/**
 * This class sets up a Saxon XSLT Transformer
 */
public class XSLTTransformer {
    net.sf.saxon.s9api.Processor processor;
    net.sf.saxon.s9api.SchemaManager schemaManager;

    ArrayList<Transformer> transformers;
    //Transformer transformer;

    /**
     * Initializer
     *
     * @param xsltFileName The xslt file to use with the transformation
     */
    public XSLTTransformer(ArrayList<String> xsltFileNames) {
        processor = new net.sf.saxon.s9api.Processor(false);
        schemaManager = processor.getSchemaManager();
        transformers = new ArrayList<Transformer>();
        for (String fname : xsltFileNames) {
            transformers.add(setupTransformer(fname));
        }
    }

    /**
     * Initialize the Saxon Transformer
     */
    private Transformer setupTransformer(String xsltFileName) {
        TransformerFactory transformFactory = TransformerFactory.newInstance();
        TransformerFactoryImpl transformFactoryImpl = (TransformerFactoryImpl) transformFactory;
        net.sf.saxon.Configuration saxonConfig = transformFactoryImpl.getConfiguration();
        saxonConfig.setLineNumbering(true);
        saxonConfig.setRecoveryPolicy(Configuration.RECOVER_SILENTLY);
        saxonConfig.registerExtensionFunction(new LineNumbers());
        transformFactoryImpl.setConfiguration(saxonConfig);

        try {
            Transformer transformer =
            transformFactory.newTransformer(new StreamSource(new File(xsltFileName)));
            return transformer;
        } catch (Exception e) {
            // TODO better handling
            System.exit(1);
            return null;
        }
    }

    /**
     * Transform an xml document represented as a String
     *
     * @param in String containing the full xml document
     * @return The transformation result
     * @throws TransformerException if the transformation fails
     */
    public String transformString(String in) throws TransformerException, SAXException, IOException, ParserConfigurationException {
        // Perform all transformations, then combine the results
        Document result = null;
        for (Transformer t : transformers) {
            StringReader reader = new StringReader(in);
            StreamSource source = new StreamSource(reader);
            if (result == null) {
                result = transformOne(t, source);
            } else {
                mergeResults(result, transformOne(t, source));
            }
        }
        return documentToString(result);
    }

    /**
     * Transform an xml document read from a stream
     *
     * @param in DataInputStream to read the xml document from
     * @return The transformation result
     * @throws TransformerException if the transformation fails
     */
/*
    public String transformStream(DataInputStream in) throws TransformerException, SAXException, IOException, ParserConfigurationException {
        StreamSource source = new StreamSource(in);
        return transformAll(source);
    }
*/
    /**
     * Transform an xml document contained in a file
     *
     * @param in Filename of the file containing the full xml document
     * @return The transformation result
     * @throws TransformerException if the transformation fails
     */
      public String transformFile(String xmlFileName) throws TransformerException, SAXException, IOException, ParserConfigurationException {
        Document result = null;
        for (Transformer t : transformers) {
            StreamSource source = new StreamSource(new File(xmlFileName));
            if (result == null) {
                result = transformOne(t, source);
            } else {
                mergeResults(result, transformOne(t, source));
            }
        }
        return documentToString(result);

    }

    /**
     * Transform an xml document represented an xml Source object
     *
     * @param in xml document to transform
     * @return The transformation result
     * @throws TransformerException if the transformation fails
     */
    /*
    public String transform(Source xmlFile) throws TransformerException {
        StreamResult result = new StreamResult(new StringWriter());
        transformer.transform(xmlFile, result);
        String xmlString = result.getWriter().toString();
        return xmlString;
    }*/

    private Document transformOne(Transformer transformer, Source xmlFile) throws TransformerException, SAXException, IOException, ParserConfigurationException {
        StreamResult result = new StreamResult(new StringWriter());
        transformer.transform(xmlFile, result);
        String xmlString = result.getWriter().toString();

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        System.out.println("[XX]");
        System.out.println("[XX]");
        System.out.println("[XX]");
        System.out.println("[XX] PARSE RESULT DOC:");
        System.out.println(xmlString);
        System.out.println("[XX]");
        System.out.println("[XX]");
        System.out.println("[XX]");
        Document document = docBuilder.parse(new InputSource(new StringReader(xmlString)));
        return document;
    }

    private String documentToString(Document doc) throws TransformerException, IOException {
        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(domSource, result);
        return writer.toString();
    }

    private void mergeResults(Document target, Document source) {
        // loop through all the elements in the second source, and add specific ones to the target
        Node root = source.getFirstChild();
        Node targetRoot = target.getFirstChild();
        Node cur = root.getFirstChild();
        while (cur != null) {
            Node copy = cur.cloneNode(true);
            target.adoptNode(copy);
            // Always add at the end, except for one currently hardcoded
            // exception: add svrl:ns-prefix-in-attribute-values at
            // the start instead of at the end
            if (copy.getNodeName() == "svrl:ns-prefix-in-attribute-values") {
                targetRoot.insertBefore(copy, targetRoot.getFirstChild());
            } else {
                targetRoot.appendChild(copy);
            }
            System.out.println("YOYO NO HAVE NODE: " + cur.getNodeName());
            cur = cur.getNextSibling();
        }
    }
}
