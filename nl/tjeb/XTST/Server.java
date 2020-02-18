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

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.xml.transform.TransformerException;
import org.xml.sax.SAXException;

import java.util.Map;


import javax.xml.transform.stream.StreamSource;

/**
 * XSLT Transformation Server
 *
 * Listens on a TCP port for connections
 * When a client connects, it will read an xml document sent by the
 * client. It will transform the document, report the result, then
 * send the document back.
 *
 * Protocol (version 1):
 * Each string is sent as 4 bytes of network order data, followed by
 * the string (utf-8 encoding)
 * Upon connect, the server sends a protocol version string
 * It will then read an xml document sent by the client (as one string)
 * The document will then be transformed
 * It will then send a status string, of the form:
 * "Success: document transformed"
 * or
 * "Error: <error messsage>"
 * Upon success, the transformation result is sent as one string
 *
 *
 * Protocol (version 3):
 * Each string is sent as 4 bytes of network order data, followed by
 * the string (utf-8 encoding)
 * Upon connect, the server sends a protocol version string
 * It will then read a command, which is one of:
 * validate
 * validate <keyword> (when running in multimode, specifying which
 *                     transformation to perform)
 * reload (reload the handler (directory))
 * list-handlers: return an xml element tree containing the currently
 *                active handlers, see below for the format
 *
 * After reading the command, it will send a status message to the
 * client, either 'Success: <msg>' or 'Error: <msg>'
 * In case of Error the server then closes the connection
 * In case of success of the validate command, it will then read an
 * xml document sent by the client (as one string)
 * It will then send a status string, of the form:
 * "Success: document transformed"
 * or
 * "Error: <error messsage>"
 * Upon success, the transformation result is sent as one string
 *
 * For all other commands, the server will send back an arbitrary number
 * of strings, followed by a string containing 'XTSTResponseEnd'
 *
 * list-handlers format example:
 * <XTSTHandlers>
 *   <Handler>
 *     <Name>Foo bar</Name>
 *     <Description>Some description</Description>
 *     <Keyword>urn:www.example.com:foobar</Keyword>
 *   </Handler>
 *   <Handler>
 *     <Name>Baz</Name>
 *     <Description>Some other description</Description>
 *     <Keyword>urn:www.example.com:baz</Keyword>
 *   </Handler>
 * </XTSTHandlers>
 */
public class Server extends Thread
{
    private ServerSocket serverSocket;
    DocumentHandlerManager _manager;

    boolean multimode;
    static String VERSION = "1.1.0beta";
    static String PROTOCOL_VERSION = "3";

    /**
     * Initializer
     *
     * @param hostname The hostname or IP address to listen on
     * @param port The port number to listen on
     * @param XSLTFileName The XSLT file to use in the transformation
     * @param xsdFileName The XSD file to validate against (may be null)
     * @param checkEverySeconds Check fro reload every X seconds
     */
    public Server(String host, int port, boolean multimode_on, DocumentHandlerManager manager) throws IOException, SAXException {
        InetAddress addr = InetAddress.getByName(host);
        serverSocket = new ServerSocket(port, 100, addr);
        System.out.println("Listening on port: " + port);
        multimode = multimode_on;
        _manager = manager;
    }

    /**
     * Read a chunk of data
     *
     * @param read_buffer the buffer to read the data into, this must
     *        be at least size bytes long
     * @param size the number of bytes to read
     * @param in The DataInputStream to read from
     * @throws IOException if there is an error reading
     */
    private void readData(byte[] read_buffer, int size, DataInputStream in) throws IOException {
        int total_read = 0;
        int bytes_read = 0;
        while (total_read < size) {
            bytes_read = in.read(read_buffer, total_read, size - total_read);
            total_read += bytes_read;
        }
    }

    /**
     * Read the data length of the next chunk of data
     *
     * On the wire the size is sent through 4 bytes, in network order
     * (i.e. big-endian)
     *
     * @param reader The BufferedReader wrapping the client connection
     * @return The size of the next data chunk
     * @throws IOException If there is an error during the read
     */
    private int readDataLength(DataInputStream in) throws IOException {
        byte[] read_buffer = new byte[4];
        readData(read_buffer, 4, in);
        ByteBuffer bb = ByteBuffer.wrap(read_buffer);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getInt();
    }

    /**
     * Read one string chunk of data
     * First reads the (4-byte) data size, then the data itself
     * The string is read as UTF-8
     *
     * @param in The data stream to read from
     * @return String the string that is read
     * @throws IOException If there is an error during the read
     */
    private String readDataString(DataInputStream in) throws IOException {
        int size = readDataLength(in);
        byte[] read_buffer = new byte[size];
        readData(read_buffer, size, in);
        return new String(read_buffer, "UTF-8");
    }

    /**
     * Send one chunk of data
     *
     * @param data The bytes to send
     * @param out The DataOutputStream to send to
     * @throws IOException If there is an error while sending
     */
    private void sendData(byte[] data, DataOutputStream out) throws IOException {
        out.write(data, 0, data.length);
    }

    /**
     * Send the data size of the next chunk, as 4 bytes in network order (signed)
     *
     * @param size the size of the data to send
     * @param out The DataOutputStream to send to
     * @throws IOException If there is an error while sending
     */
    private void sendDataSize(int size, DataOutputStream out) throws IOException {
        byte[] size_bytes = new byte[4];
        ByteBuffer bb = ByteBuffer.wrap(size_bytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putInt(size);
        sendData(size_bytes, out);
    }

    /**
     * Send one string encoded with UTF-8
     * First send the data size (see \sendDataSize()) then the data
     *
     * @param String the data string to send
     * @param out The DataOutputStream to send to
     * @throws IOException If there is an error while sending
     */
    private void sendDataString(String data, DataOutputStream out)throws IOException  {
        try {
            byte[] data_bytes = data.getBytes("UTF-8");
            sendDataSize(data_bytes.length, out);
            sendData(data_bytes, out);
        } catch (java.io.UnsupportedEncodingException usee) {
            usee.printStackTrace();
        }
    }

    private void validateDocument(DocumentHandler handler, DataInputStream in, DataOutputStream out) throws IOException, TransformerException {
        String status = null;
        String result = null;

        handler.checkModified();
        String xml = readDataString(in);
        // Validate against schema
        if (handler != null && handler.hasXSDValidator()) {
            try {
                StringReader reader = new StringReader(xml);
                StreamSource source = new StreamSource(reader);
                handler.getXSDValidator().validate(source);
                result = handler.getTransformer().transformString(xml);
                status = "Success: transformation succeeded\n";
            } catch (SAXException saxe) {
                status = "Error: invalid " + saxe.toString();
                System.out.println(status);
            } catch (Exception exc) {
                status = "Error processing document: " + exc.toString();
                System.out.println(status);
                exc.printStackTrace();
            }
        } else if (handler != null) {
            // Transform XSLT
            try {
                result = handler.getTransformer().transformString(xml);
                status = "Success: transformation succeeded\n";
            } catch (Exception exc) {
                status = "Error processing document: " + exc.toString();
                System.out.println(status);
                exc.printStackTrace();
            }
        }

        //System.out.println("Sending status: " + status);
        sendDataString(status, out);

        if (result != null) {
            sendDataString(result, out);
        }
    }

    /**
     * Run the server
     */
    public void run() {
        while(true) {
            try {
                Socket server = serverSocket.accept();

                DataInputStream in =
                      new DataInputStream(server.getInputStream());
                DataOutputStream out =
                     new DataOutputStream(server.getOutputStream());

                String command = null;
                String status = null;
                String result = null;

                sendDataString("XSLT Transformer server version " +
                               VERSION + ", protocol version: " +
                               Server.PROTOCOL_VERSION + "\n", out);

                try {
                    command = readDataString(in);

                    if (command.startsWith("validate")) {
                        if (multimode) {
                            if (command.length() <= 9) {
                                status = "Error: validate needs a keyword when running in multimode";
                                sendDataString(status, out);
                            } else {
                                String keyword = command.substring(9);
                                DocumentHandler handler = _manager.getDocumentHandler(keyword);
                                if (handler == null) {
                                    System.out.println("Request for unknown keyword '" + keyword + "'");

                                    for (String key : _manager._handlers.keySet()) {
                                        System.out.println("   '" + key + "'");
                                    }
                                    sendDataString("Error: unknown keyword '" + keyword + "'\n", out);
                                } else {
                                    sendDataString("Success: send the XML document now", out);
                                    validateDocument(handler, in, out);
                                }
                            }
                        } else {
                            sendDataString("Success: send the XML document now", out);
                            validateDocument(_manager.getDocumentHandler("default"), in, out);
                        }
                    // check other commands here
                    } else if (command.equals("reload")) {
                        _manager.load();
                        sendDataString("Success: handler(s) reloaded", out);
                        sendDataString("XTSTResponseEnd", out);
                    } else if (command.equals("list-handlers")) {
                        sendHandlers(out);
                        sendDataString("XTSTResponseEnd", out);
                    } else {
                        status = "Error: Unknown command";
                        sendDataString(status, out);
                    }
                } catch (IOException ioe) {
                    System.out.println("[XX] XTST Error: " + ioe);
                    sendDataString("Error: " + ioe + "\n", out);
                } catch (Exception xpe) {
                    System.out.println("[XX] XTST Error: " + xpe);
                    sendDataString("Error: " + xpe + "\n", out);
                }
                server.close();
            } catch (java.net.SocketException se) {
                System.out.println("Could not send back result: " + se);
            } catch (SocketTimeoutException s) {
                System.out.println("Socket timed out!");
                break;
            } catch(IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private void sendHandlers(DataOutputStream out) throws IOException {
        sendDataString("<XTSTHandlers>", out);
        for (Map.Entry<String, DocumentHandler> entry : _manager.getHandlers().entrySet()) {
            DocumentHandler handler = entry.getValue();
            sendDataString("  <Handler>", out);
            sendDataString("    <Name>" + handler.getName() + "</Name>", out);
            sendDataString("    <Description>" + handler.getDescription() + "</Description>", out);
            sendDataString("    <Keyword>" + entry.getKey() + "</Keyword>", out);
            sendDataString("  </Handler>", out);
            //result += "  <Name>" + handler.getName() + "</Name>\n";
            //result += "  <Description>" + handler.getDescription() + "</Description>\n";
            //result += "  <Keyword>" + entry.getKey() + "</Keyword>\n";

        }
        sendDataString("</XTSTHandlers>", out);
    }
}
