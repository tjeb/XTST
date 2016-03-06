
# XSLT Transformer server (XTST)

### Description

This is a very simple XSLT Transformer that can run as a server.

The idea behind it is that there is very little support for XSLT 2.0 in
programming languages other than Java. Until that improves, it would be
nice to have a separate little tool to perform XSLT(2) transformations
for programs written in other languages.

There are two modes of operation: single transformation of a file, or a
server that can perform transformations sent over a socket.

By default, it will listen for connections on localhost port 35791. Once a client connects, it reads an XML document from the client, transforms it, and sends the transformation result back. For a full overview of the data protocol, see below.

### Requirements

* Java 1.7 or higher
* Python for the example client

### Installation

By default it will install to ~/opt/XTST
Update the Makefile if you want a different directory

    make
    make install


### Running

For transformation of a single file, use

    java -jar ~/opt/XTST/XTST.jar -f <xml_file> <xslt_file>

To run as a server, just use

    java -jar ~/opt/XTST/XTST.jar <xslt_file>

By default, it will listen on localhost port 35791
You can specify the hostname/address or port number with -a and -p

    java -jar ~/opt/XTST/XTST.jar -a 192.0.2.1 -p 47806 <xslt_file>

There is also a helper wrapper script, which assumes there is an XSLT file in ~/opt/XTST/transform.xsl

    ~/opt/XTST/xslt_transformer_service.sh start

send_document.py is a basic script that simply reads a file and sends it to the server.

    ~/opt/XTST/send_document.py ~/my_xml_doc.xml


### Protocol (version 1)

The procotol is based on (string) messages; each message is prepended by
its size, sent as a 4-byte signed integer in network order. The string
is then sent encoded as UTF-8.

Upon connection of a client, the server first sends a message with the protocol version
    XSLT Transformer server version 1.0.0, protocol version: 1

The server then waits for the client to send an xml document (as one message)
    <?xml etc. >

The server transforms the document. If anything goes wrong during tranformation, it will send back one message
    Error: <error message>
And the connection is closed.

If the transformation succeeds, the server sends back two messages:
    Success: transformation succeeded
    <?xml etc >

The connection is then closed as well.

### License

The code in this repository is available under the GNU Public License version 3, see LICENSE.TXT for details.
See the lib/ directory for licenses of the libraries that are used (argparse4j and saxon9he)

