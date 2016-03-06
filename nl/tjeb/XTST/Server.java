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
import java.util.ArrayList;
import java.lang.Character;
import net.sf.saxon.trans.XPathException;
import javax.xml.transform.TransformerException;

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
 */
public class Server extends Thread
{
    private ServerSocket serverSocket;
    XSLTTransformer transformer;
    String xsltFile;
    static String VERSION = "1.0.0";
    static String PROTOCOL_VERSION = "1";

    /**
     * Initializer
     *
     * @param hostname The hostname or IP address to listen on
     * @param port The port number to listen on
     * @param xsltFileName The XSLT file to use in the transformation
     */
    public Server(String host, int port, String xsltFileName) throws IOException {
        InetAddress addr = InetAddress.getByName(host);
        serverSocket = new ServerSocket(port, 100, addr);
        transformer = new XSLTTransformer(xsltFileName);
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

                String status = null;
                String result = null;

                sendDataString("XSLT Transformer server version " +
                               VERSION + ", protocol version: " +
                               Server.PROTOCOL_VERSION + "\n", out);
                
                try {
                    String xml = readDataString(in);
                    status = "Success: transformation succeeded\n";
                    result = transformer.transformString(xml);
                } catch (IOException ioe) {
                    status = "Error: " + ioe;
                } catch (Exception xpe) {
                    status = "Error: " + xpe;
                }

                sendDataString(status, out);

                if (result != null) {
                    sendDataString(result, out);
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
}
