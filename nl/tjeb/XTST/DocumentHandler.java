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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.validation.*;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import org.xml.sax.SAXException;

/**
 * XSLT and XSD document handle
 */

public class DocumentHandler {
    XSLTTransformer transformer;
    // We keep track of the loaded files, and their last modified time
    // To our 'list' of files is a LinkedHashmap with the file path as
    // keys, and the mtime as values
    // (LinkedHashMap to preserve order, just in case)
    LinkedHashMap<String, Long> XSLTFiles;
    //ArrayList<String> XSLTFiles;
    long xsltModified;
    long modifyChecked;
    // By default, check every 5 seconds
    long checkEveryMilliseconds = 5000;
    LinkedHashMap<String, Long> XSDFiles;
    Validator XSDValidator = null;
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
        XSLTFiles = new LinkedHashMap<String, Long>();
        XSLTFiles.put(XSLTFileName, new Long(0));
        loadXSLT();
        XSDFiles = new LinkedHashMap<String, Long>();
        XSDFiles.put(xsdFileName, new Long(0));
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
        XSLTFiles = new LinkedHashMap<String, Long>();
        XSLTFiles.put(XSLTFileName, new Long(0));
        loadXSLT();
        XSDFiles = new LinkedHashMap<String, Long>();
        XSDFiles.put(xsdFileName, new Long(0));
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
    public DocumentHandler(String XSLTFileName, ArrayList<String> xsdFileNames, int checkEverySeconds, String name, String description) throws SAXException, FileNotFoundException {
        _name = name;
        _description = description;
        XSLTFiles = new LinkedHashMap<String, Long>();
        XSLTFiles.put(XSLTFileName, new Long(0));
        loadXSLT();
        XSDFiles = new LinkedHashMap<String, Long>();
        for (String fileName : xsdFileNames) {
            XSDFiles.put(fileName, new Long(0));
        }
        loadXSD();
    }

    public DocumentHandler(ArrayList<String> xslFileNames, ArrayList<String> xsdFileNames, int checkEverySeconds, String name, String description) throws SAXException, FileNotFoundException {
        _name = name;
        _description = description;
        XSLTFiles = new LinkedHashMap<String, Long>();
        for (String fileName : xslFileNames) {
            XSLTFiles.put(fileName, new Long(0));
        }
        loadXSLT();
        XSDFiles = new LinkedHashMap<String, Long>();
        for (String fileName : xsdFileNames) {
            XSDFiles.put(fileName, new Long(0));
        }
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
        // todo: we only check for the last modified time of the last file right now
        for (String fname : XSLTFiles.keySet()) {
            System.out.println("Loading XSLT file " + fname);
            xsltModified = new File(fname).lastModified();
            modifyChecked = System.currentTimeMillis();
            XSLTFiles.put(fname, new Long(xsltModified));
        }
        transformer = new XSLTTransformer(XSLTFiles.keySet());
        System.out.println("Loaded XSLT files");
    }


    private StreamSource filenameToSource(String filename) throws FileNotFoundException {
        File f = new File(filename);
        InputStream fstream = new FileInputStream(f);
        StreamSource sstream = new StreamSource(fstream, f.getAbsolutePath());
        return sstream;
    }

    /**
     * Load an XSD file
     */
    private void loadXSD() {
        try {
            System.out.println("Loading XSD file, if any");
            if (XSDFiles == null || XSDFiles.size() == 0) {
                XSDValidator = null;
                System.out.println("No XSD files set");
            } else {
                System.out.println("Loading XSD file: " + XSDFiles.toString());
                SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                ArrayList<StreamSource> streamSources = new ArrayList<StreamSource>();
                for (String xsdFileName : XSDFiles.keySet()) {
                  streamSources.add(filenameToSource(xsdFileName));
                  long xsdModified = new File(xsdFileName).lastModified();
                  XSDFiles.put(xsdFileName, new Long(xsdModified));
                }
                StreamSource[] sources = new StreamSource[XSDFiles.size()];
                streamSources.toArray(sources);
                Schema schema = schemaFactory.newSchema(sources);
                //Schema schema = schemaFactory.newSchema(new File(XSDFile));
                XSDValidator = schema.newValidator();
                System.out.println("Loaded XSD files " + XSDFiles.toString());
            }
        } catch (FileNotFoundException fxfe) {
            System.out.println("Error reading XSD File: " + fxfe);
        } catch (SAXException saxe) {
            System.out.println("Error reading XSD File: " + saxe);
        }
    }

    /**
     * Check whether the XSLT file has been modified since it was
     * loaded. If so, reload it. Check at most once every CHECK_EVERY
     * milliseconds.
     * TODO: CURRENTLY DISABLED
     */
    public void checkModified() {
        //System.out.println("[XX] checkModified called");
        // Don't check *every* time; check at most once every 5 seconds
        long now = System.currentTimeMillis();
        if (now > modifyChecked + checkEveryMilliseconds) {
            for (Map.Entry<String, Long> entry : XSLTFiles.entrySet()) {
                long modified = new File(entry.getKey()).lastModified();
                if (modified > entry.getValue()) {
                    // just reload all XSLT files
                    loadXSLT();
                    return;
                }
            }
            for (Map.Entry<String, Long> entry : XSDFiles.entrySet()) {
                long modified = new File(entry.getKey()).lastModified();
                if (modified > entry.getValue()) {
                    // just reload all XSLT files
                    loadXSD();
                    return;
                }
            }
            modifyChecked = now;
        }
    }

    public boolean hasXSDValidator() {
        return (XSDValidator != null);
    }
    
    public boolean hasTransformer() {
        return (transformer != null);
    }

    public Validator getXSDValidator() {
        return XSDValidator;
    }

    public XSLTTransformer getTransformer() {
        return transformer;
    }
}
