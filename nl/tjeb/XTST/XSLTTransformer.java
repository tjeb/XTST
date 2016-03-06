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

import java.io.StringWriter;
import java.io.File;
import java.io.DataInputStream;
import java.io.StringReader;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.TransformerException;

import net.sf.saxon.Configuration;
import net.sf.saxon.TransformerFactoryImpl;

/**
 * This class sets up a Saxon XSLT Transformer
 */
public class XSLTTransformer {
    net.sf.saxon.s9api.Processor processor;
    net.sf.saxon.s9api.SchemaManager schemaManager;

    Transformer transformer;

    /**
     * Initializer
     *
     * @param xsltFileName The xslt file to use with the transformation
     */
    public XSLTTransformer(String xsltFileName) {
        processor = new net.sf.saxon.s9api.Processor(false);
        schemaManager = processor.getSchemaManager();
        setupTransformer(xsltFileName);
    }

    /**
     * Initialize the Saxon Transformer
     */
    private void setupTransformer(String xsltFileName) {
        TransformerFactory transformFactory = TransformerFactory.newInstance();
        TransformerFactoryImpl transformFactoryImpl = (TransformerFactoryImpl) transformFactory;
        net.sf.saxon.Configuration saxonConfig = transformFactoryImpl.getConfiguration();
        saxonConfig.setRecoveryPolicy(Configuration.RECOVER_SILENTLY);
        transformFactoryImpl.setConfiguration(saxonConfig);

        try {
            transformer =
            transformFactory.newTransformer(new StreamSource(new File(xsltFileName)));
        } catch (Exception e) {
            // TODO better handling
            System.exit(1);
        }
    }

    /**
     * Transform an xml document represented as a String
     *
     * @param in String containing the full xml document
     * @return The transformation result
     * @throws TransformerException if the transformation fails
     */
    public String transformString(String in) throws TransformerException {
        StringReader reader = new StringReader(in);
        StreamSource source = new StreamSource(reader);
        return transform(source);
    }

    /**
     * Transform an xml document read from a stream
     *
     * @param in DataInputStream to read the xml document from
     * @return The transformation result
     * @throws TransformerException if the transformation fails
     */
    public String transformStream(DataInputStream in) throws TransformerException {
        StreamSource source = new StreamSource(in);
        return transform(source);
    }

    /**
     * Transform an xml document contained in a file
     *
     * @param in Filename of the file containing the full xml document
     * @return The transformation result
     * @throws TransformerException if the transformation fails
     */
    public String transformFile(String xmlFileName) throws TransformerException {
        StreamSource source = new StreamSource(new File(xmlFileName));
        return transform(source);
    }

    /**
     * Transform an xml document represented an xml Source object
     *
     * @param in xml document to transform
     * @return The transformation result
     * @throws TransformerException if the transformation fails
     */
    public String transform(Source xmlFile) throws TransformerException {
        StreamResult result = new StreamResult(new StringWriter());
        transformer.transform(xmlFile, result);
        String xmlString = result.getWriter().toString();
        return xmlString;
    }
}
