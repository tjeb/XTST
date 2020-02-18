#!/usr/bin/env python3

#  Copyright (c) 2016 Jelte Jansen
#
#  This file is part of the XSLT Transformation Server Tool (XTST).
#
#  XTST is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Lesser General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  XTST is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Lesser General Public License for more details.
#
#  You should have received a copy of the GNU Lesser General Public License
#  along with XTST.  If not, see <http://www.gnu.org/licenses/>.
#

import argparse
import socket
import struct
import sys

#
# Example client code
#
# This tool sends a document (preferrable XML, but it does not check)
# to the XSLT Transformer server, and reads the transformation result
# document
#

PROTOCOL_VERSION = 3

def send_data_string(s, data):
    bts = data.encode("UTF-8")
    l_bts = struct.pack(">I", len(bts))
    s.send(l_bts)
    s.send(bts)

def read_data(s, size):
    result = bytes()
    while len(result) < size:
        rec = s.recv(size)
        result += rec
    return result

def read_data_string(s, decode=True):
    # read size first, 4 bytes network order (signed)
    size_bytes = read_data(s, 4)
    size = struct.unpack(">i", size_bytes)[0]

    result = read_data(s, size)
    if decode:
        return result.decode("UTF-8")
    else:
        return result

def check_protocol_version(version_string):
    protocol_version = int(version_string.split(":")[1])
    if protocol_version != PROTOCOL_VERSION:
        print(("Remote server has protocol version %d, " +
              "while I use %d. Aborting")
              % (protocol_version, PROTOCOL_VERSION))
        exit(1)
    return protocol_version

def send_command(command, host, port):
    s = socket.socket(
        socket.AF_INET, socket.SOCK_STREAM)
    s.connect((host, port))
    version_string = read_data_string(s)
    protocol_version = check_protocol_version(version_string)
    send_data_string(s, command)
    response = read_data_string(s)
    while response != "XTSTResponseEnd":
      print(response)
      response = read_data_string(s)

def send_document(filename, host, port, outputfile, keyword):
    with open(filename) as inf:
        lines = inf.readlines()
        xml = "".join(lines)

        s = socket.socket(
            socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))

        version_string = read_data_string(s)
        protocol_version = check_protocol_version(version_string)
        if not keyword:
            send_data_string(s, "validate")
        else:
            send_data_string(s, "validate %s" % keyword)
        status = read_data_string(s)
        if (status.startswith("Success: ")):
            send_data_string(s, xml);

            status = read_data_string(s)
        if status.startswith("Success:"):
            result = read_data_string(s, True)
            if not outputfile:
                print(result)
            else:
                with open(outputfile, 'w') as outf:
                    outf.write(result.encode("UTF-8"))
        else:
            print(status)

if __name__ == "__main__":
    arg_parser = argparse.ArgumentParser(prog="si_checker")
    arg_parser.add_argument('-a', '--address', default='localhost',
                            help='hostname or IP address of the server')
    arg_parser.add_argument('-p', '--port', type=int, default=35791,
                            help='port of the server')
    arg_parser.add_argument('-o', '--outputfile',
                            help='save returned document to file')
    arg_parser.add_argument('-k', '--keyword', type=str,
                            help='use keyword to select handler in multimode')
    arg_parser.add_argument('-c', '--command', action='store_true',
                            help='send an arbitrary command to the server (instead of a document)')
    arg_parser.add_argument('document_or_command',
                            help='document to send')

    args = arg_parser.parse_args()

    if args.command:
        send_command(args.document_or_command, args.address, args.port)
    else:
        send_document(args.document_or_command, args.address, args.port, args.outputfile, args.keyword)
