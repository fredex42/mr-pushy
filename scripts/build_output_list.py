#!/usr/bin/env python3

import argparse
import logging

logging.basicConfig(level=logging.WARN)
logger = logging.getLogger(__name__)
logger.level = logging.INFO

def tell_me_about(s): return (type(s), s)

def load_exclude_list(filename):
    with open(filename, "r") as f:
        content = f.read()
        return filter(lambda entry: len(entry)>0, content.split("\n"))


def should_output(line, excludelines):
    for test in excludelines:
        if line.startswith(test):
            return False
    return True

###START MAIN
parser = argparse.ArgumentParser()
parser.add_argument("--allfiles", dest='allfiles', help="List of all files, to build the output list from", default="all_media.lst")
parser.add_argument("--files-list-encoding", dest="encoding", help="Character encoding of the file in --allfiles. On a Mac this should probably be latin1", default="latin1")
parser.add_argument("--exclude", dest='exclude', help="File containing list of paths to exclude", default="excludepaths.lst")
parser.add_argument("--output", dest='outputfile', default='to_upload.lst')
args = parser.parse_args()

exclude_list = list(load_exclude_list(args.exclude))

logger.debug(exclude_list)

with open(args.outputfile,"w") as fpout:
    with open(args.allfiles,"r", encoding=args.encoding) as f:
        for line in f:
            # if args.encoding=="utf-8":
            #     converted_line = line
            # else:
            #     converted_line = line.encode("utf-8").decode("utf-8")
            if should_output(line.rstrip(), exclude_list):
                fpout.write(line)
