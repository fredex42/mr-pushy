#!/usr/bin/env python3
import certifi
import requests
import argparse

parser = argparse.ArgumentParser()
parser.add_argument("--host", dest="host", help="Hostname to try to connect to")
parser.add_argument("--cafile", dest="cafile", help="CA file to add if connection fails")
args = parser.parse_args()

try:
    print("Attempting to connect to {0}".format(args.host))
    test = requests.get("https://{0}".format(args.host))
    print("HTTPS connection worked OK")
except requests.exceptions.SSLError as err:
    print("SSL error: {0}".format(err))
    print("Adding custom certs to Certifi store...")
    cafile = certifi.where()

    with open(args.cafile, "rb") as infile:
        new_cert_data = infile.read()

    with open(cafile, 'ab') as outfile:
        outfile.write(new_cert_data)

    print('Added the certificate, try re-running this program to see if it worked')