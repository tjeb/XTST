package nl.tjeb.XTST;

import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import org.xml.sax.SAXException;

import java.io.*;

class DocumentHandlerManager {
    // todo: make private
    public Map<String, DocumentHandler> _handlers;
    private boolean _multimode;
    private int _checkEverySeconds;
    private String _baseDirectory;
    private String _xsltFile;
    private String _xsdFile;

    public DocumentHandlerManager(boolean multimode, String xsltFileOrDirectory, String xsdFile, int checkEverySeconds) throws IOException, SAXException {
        _checkEverySeconds = checkEverySeconds;
        _multimode = multimode;
        if (multimode) {
            _baseDirectory = xsltFileOrDirectory;
        } else {
            _xsltFile = xsltFileOrDirectory;
        }
        _xsdFile = xsdFile;

        load();
    }

    private synchronized void replaceHandlers(Map<String, DocumentHandler> new_handlers) {
        _handlers = new_handlers;
        System.out.println("[XX] handlers replaced, new keywords:");
        for (String key : _handlers.keySet()) {
            System.out.println("   [XX] '" + key + "'");
        }
    }

    public void load() throws IOException, SAXException {
        Map new_handlers = new HashMap<String, DocumentHandler>();
        if (_multimode) {
            readDirectories(_baseDirectory, new_handlers);
        } else {
            new_handlers.put("default", new DocumentHandler(_xsltFile, _xsdFile, _checkEverySeconds));
        }
        replaceHandlers(new_handlers);
    }

    public int getDocumentHandlerCount() {
        return _handlers.size();
    }

    public DocumentHandler getDocumentHandler(String keyword) {
        return _handlers.get(keyword);
    }

    private void readXTSTProperties(File propertiesFile, Map handlers) throws IOException, SAXException {
        System.out.println("Read properties from " + propertiesFile);
        InputStream in = new FileInputStream(propertiesFile);
        Properties properties = new Properties();
        // load a properties file
        properties.load(in);
        String keyword = properties.getProperty("keyword");
        if (keyword == null) {
            System.out.println("Error: missing keyword property in " + propertiesFile.getAbsoluteFile());
            // TODO: should not exit
            System.exit(1);
        }
        if (handlers.containsKey(keyword)) {
            System.out.println("Error: duplicate entries for keyword " + keyword);
            // TODO: should not exit
            System.exit(1);
        }

        String xsltFileRel = properties.getProperty("xsl_file");
        if (xsltFileRel == null) {
            System.out.println("Error: missing xsl_file property in " + propertiesFile.getAbsoluteFile());
            System.exit(1);
        }
        File xsltFile = new File(propertiesFile.getParent(), xsltFileRel);
        String xsdFileString = null;
        if (properties.getProperty("xsd_file") != null) {
            File xsdFile = new File(propertiesFile.getParent(), properties.getProperty("xsd_file"));
            xsdFileString = xsdFile.toString();
        }
        String name = "";
        if (properties.getProperty("name") != null) {
          name = properties.getProperty("name");
        }
        String description = "";
        if (properties.getProperty("description") != null) {
          description = properties.getProperty("description");
        }
        System.out.println("Loading files for keyword '" + keyword +"'");
        DocumentHandler handler = new DocumentHandler(xsltFile.toString(), xsdFileString, _checkEverySeconds, name, description);
        handlers.put(keyword, handler);
    }

    private void checkDirectory(File dir, Map handlers) throws IOException, SAXException  {
        String[] subDirs = dir.list();
        for(String filename : subDirs){
            File dirEntry = new File(dir, filename);
            if ("xtst.properties".equals(dirEntry.getName())) {
                readXTSTProperties(dirEntry, handlers);
            }

            if (dirEntry.isDirectory()) {
                checkDirectory(new File(dir, filename), handlers);
            }
        }
    }

    private void readDirectories(String directory, Map handlers) throws IOException, SAXException {
        File maindir = new File(directory);
        if (maindir.isDirectory()) {
            checkDirectory(maindir, handlers);
        } else {
            System.out.println("Error: " + directory + " is not a directory");
        }
    }

    public Map<String, DocumentHandler> getHandlers() {
        return _handlers;
    }

}
