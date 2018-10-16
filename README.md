
# XSLT Transformer server (XTST)

### Description

(tldr; I wrote some Java so you don't have to)

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

    java -jar ~/opt/XTST/XTST.jar <xslt_file> [xsd_file]

To run as a server in multimode, use

    java -jar ~/opt/XTST/XTST.jar -m <directory>

See Multimode for more information.

By default, it will listen on localhost port 35791
You can specify the hostname/address or port number with -a and -p

    java -jar ~/opt/XTST/XTST.jar -a 192.0.2.1 -p 47806 <xslt_file>

There is also a helper wrapper script, which assumes there is an XSLT
file in ~/opt/XTST/transform.xsl. By default this file will contain a
very basic transformation that transforms the example.xml in the source
directory.

    ~/opt/XTST/xslt_transformer_service.sh start

send_document.py is a basic script that simply reads a file and sends it to the server.

    ~/opt/XTST/send_document.py example.xml

When running in multimode, send_document also requires a keyword;
    ~/opt/XTST/send_document.py -k foo example.xml

### Multimode

When running in multimode, instead of passing an XSLT file, you can
pass a directory. XTST will traverse the directory structure looking for files called 'xtst.properties'; which should contain the following lines:

    keyword=<keyword>
    xsl_file=<filename (relative to path of xtst.properties file)>
    xsd_file=<filename (relative to path of xtst.properties file)>

xsd_file is optional, keyword and xsl_file are mandatory. You can also specify multiple xsd files with the keywords xsd_file1= to xsd_file10=.

XTST will load document handlers for all the xtst.properties files it finds, and will use communication protocol 2, which supports selecting the correct handler.

Be warned: at this moment, XTST will fail and exit if the xsl or xsds cannot be parsed, or if a keyword is used twice.



### Protocol (version 1)

The procotol is based on (string) messages; each message is prepended by
its size, sent as a 4-byte signed integer in network order. The string
is then sent encoded as UTF-8.

Upon connection of a client, the server first sends a message with the protocol version

    XSLT Transformer server version <VERSION>, protocol version: 1

The server then waits for the client to send an xml document (as one message)

    <?xml etc. >

The server transforms the document. If anything goes wrong during tranformation, it will send back one message

    Error: <error message>

And the connection is closed.

If the transformation succeeds, the server sends back two messages:

    Success: transformation succeeded
    <?xml etc. >

The connection is then closed as well.

### Protocol (version 2)

When running in multimode, the protocol will be version 2. This is
the same as version 1, except the client should first send a keyword,
with which the server will find the correct transformation to perform.

Full description:

Upon connection of a client, the server first sends a message with the protocol version

    XSLT Transformer server version <VERSION>, protocol version: 2

The server then waits for the client to send a command
    <command>

This can be either

1. validate <keyword>
2. reload

#### The validate command

In multimode, the keyword for validate is required, in single mode it can be left out.

This keyword will be used to find the corresponding document handler, as specified by the keyword in the relevant xtst.properties file.

The server then waits for the client to send an xml document (as one message)

    <?xml etc. >

The server transforms the document. If anything goes wrong during tranformation, it will send back one message

    Error: <error message>

And the connection is closed.

If the transformation succeeds, the server sends back two messages:

    Success: transformation succeeded
    <?xml etc. >

The connection is then closed as well.

#### The reload command

When running in single mode, the server will reload the XSLT and XSD file. In multimode,
it will re-read the directory tree and load any document handler specification found in xtst.properties files.

If the reload succeeds, the server will send a message "Success: handler(s) reloaded".

If there is a failure reloading, the server will send a message "Error: <failure>". In this case, the old document handler(s) are kept active.

### License

The code in this repository is available under the GNU Public License version 3, see LICENSE.TXT for details.
See the lib/ directory for licenses of the libraries that are used (argparse4j and saxon9he)

