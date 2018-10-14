/*
 * Copyright (c) 2017 Jelte Jansen
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

import java.io.*;
import javax.xml.validation.*;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import org.xml.sax.SAXException;

/**
 * XSLT and XSD document handle
 */

public class DocumentHandler {
    XSLTTransformer transformer;
    String XSLTFile;
    long xsltModified;
    long modifyChecked;
    long checkEveryMilliseconds;
    String XSDFile;
    Validator XSDValidator;
    private String _name;
    private String _description;

    /**
     * Initializer
     *
     * @param XSLTFileName The XSLT file to use in the transformation
     * @param XSDFileName The XSD file to validate against (may be null)
     * @param checkEverySeconds Check fro reload every X seconds
     */
    public DocumentHandler(String XSLTFileName, String xsdFileName, int checkEverySeconds) throws SAXException, FileNotFoundException {
        _name = "";
        _description = "";
        XSLTFile = XSLTFileName;
        loadXSLT();
        XSDFile = xsdFileName;
        loadXSD();
    }

    /**
     * Initializer with name and description, used in multimode
     *
     * @param name A human-readable name for this handler
     * @param description A description for this handler
     * @param XSLTFileName The XSLT file to use in the transformation
     * @param XSDFileName The XSD file to validate against (may be null)
     * @param checkEverySeconds Check fro reload every X seconds
     */
    public DocumentHandler(String XSLTFileName, String xsdFileName, int checkEverySeconds, String name, String description) throws SAXException, FileNotFoundException {
        _name = name;
        _description = description;
        XSLTFile = XSLTFileName;
        loadXSLT();
        XSDFile = xsdFileName;
        loadXSD();
    }

    public String getName() {
        return _name;
    }

    public String getDescription() {
        return _description;
    }


    /**
     * Load the XSLT file
     * Remember current time and last modified time of file
     */
    private void loadXSLT() {
        xsltModified = new File(XSLTFile).lastModified();
        modifyChecked = System.currentTimeMillis();
        transformer = new XSLTTransformer(XSLTFile);
        System.out.println("Loaded XSLT file " + XSLTFile);
    }


    private StreamSource filenameToSource(String filename) throws FileNotFoundException {
        File f = new File(filename);
        InputStream fstream = new FileInputStream(f);
        StreamSource sstream = new StreamSource(fstream, f.getAbsolutePath());
        System.out.println("[XX] LOADED STREAMSOURCE AT " + filename);
        return sstream;
    }

    /**
     * Load an XSD file
     */
    private void loadXSD() throws SAXException, FileNotFoundException {
        System.out.println("Loading XSD file, if any");
        if (XSDFile == null) {
            XSDValidator = null;
            System.out.println("No XSD file set");
        } else {
            System.out.println("Loading XSD file: " + XSDFile);
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            StreamSource[] sources = new StreamSource[2];
            sources[0] = filenameToSource(XSDFile);
            // TODO: make this configurable. comma-separated option?  is there a way to do multi?
            // maybe even just load them all?
            sources[1] = filenameToSource("document_types/SI-UBL-2.0/../xsd_ubl2.1/maindoc/UBL-CreditNote-2.1.xsd");
            Schema schema = schemaFactory.newSchema(sources);
            //Schema schema = schemaFactory.newSchema(new File(XSDFile));
            XSDValidator = schema.newValidator();
            System.out.println("Loaded XSD file " + XSDFile);
        }
    }

    /**
     * Check whether the XSLT file has been modified since it was
     * loaded. If so, reload it. Check at most once every CHECK_EVERY
     * milliseconds.
     */
    public void checkModified() {
        long now = System.currentTimeMillis();
        if (now > modifyChecked + checkEveryMilliseconds) {
            long modified = new File(XSLTFile).lastModified();
            if (modified > xsltModified) {
                loadXSLT();
            }
        }
        modifyChecked = now;
    }

    public boolean hasXSDValidator() {
        return (XSDValidator != null);
    }

    public Validator getXSDValidator() {
        return XSDValidator;
    }

    public XSLTTransformer getTransformer() {
        return transformer;
    }
}
