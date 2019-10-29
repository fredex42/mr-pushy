#!/usr/bin/env python3

import csv
import argparse
import urllib.parse
import logging
import requests
import re
import yaml
from yaml import SafeLoader

logging.basicConfig(level=logging.WARN)
logger = logging.getLogger(__name__)
logger.level = logging.INFO

is_vidispine = re.compile(r'^\w{2}-\d+')


class InvalidVidispineID(Exception):
    pass


class InvalidPlutoType(Exception):
    pass


class HttpError(Exception):
    pass


class AssetFolderNotFound(Exception):
    pass


def load_file(filename, url_column):
    """
    generator that yields the specified column from the given CSV file
    :param filename: file to read
    :param url_column: (zero-based) column index to read
    :return: yields URL strings
    """
    with open(filename, "r") as f:
        reader = csv.reader(f)
        for row in reader:
            #logger.debug(row)
            if len(row) < url_column: continue
            if row[url_column] == '': continue
            yield row[url_column]


def lookup_project_assetfolder(project_id):
    """
    ask pluto for the asset folder for the given project ID
    :param project_id: project ID to look up
    :return: the asset folder path as provided by Pluto.  Raises HttpError or AssetFolderNotFound depending on the http error code.
    """
    url = u"{proto}://{host}/gnm_asset_folder/lookup/{id}".format(proto=args.proto, host=args.host, id=project_id)

    response = requests.get(url, auth=(auth['user'], auth['password']), headers={'Accept': 'application/json'}, verify=False)
    if response.status_code == 404:
        logger.warning("404 Not found, asset folder check URL is {0}".format(url))
        raise AssetFolderNotFound(project_id)
    elif response.status_code == 200:
        content = response.json()
        return content['asset_folder']
    else:
        raise HttpError(response.text)


def safe_lookup_subproject(infolist, commission_id):
    """
    Helper function to handle exceptions when iterating the list of projects from a commission
    :param infolist: information on commission contents as returned from pluto ajax api
    :return: either the commission base path or None
    """
    project_id=infolist[7]
    try:
        return remove_last_part(lookup_project_assetfolder(project_id))
    except AssetFolderNotFound as e:
        logger.warning("No asset folder found for project {0} in commission {1}".format(project_id, commission_id))
        return None


def lookup_commission_basepath(commission_id):
    """
    ask pluto for the commission base path of the given commission.
    this will return a list of all the basepaths of the projects contained within; on occasion the projects were moved from
    elsewhere and their basepaths do not correspond.
    :param commission_id: commission ID to look up
    :return: asset folder basepath as provided by Pluto
    """
    url = u"{proto}://{host}/project/api/commission/{id}/".format(proto=args.proto, host=args.host, id=commission_id)

    response = requests.get(url, auth=(auth['user'], auth['password']), headers={'Accept': 'application/json'}, verify=False)
    if response.status_code == 200:
        data = response.json()
        if len(data['aaData'])==0:
            logger.warning("Commission {0} has no projects".format(commission_id))
            raise AssetFolderNotFound(commission_id)
        else:
            raw_list = map(lambda infolist: safe_lookup_subproject(infolist, commission_id), data['aaData'])
            nonempty_list = filter(lambda entry: entry is not None, raw_list)
            deduped_list = list(set(nonempty_list))
            return deduped_list
    else:
        raise HttpError(response.text)


def remove_last_part(path):
    if path.endswith("/"):
        actual_path = path[0:-1]
    else:
        actual_path = path

    parts = actual_path.split('/')

    return "/".join(parts[0:-1])


def lookup_paths(projecturl):
    """
    parse out the URL then ask Pluto what asset folders this corresponds to
    :param projecturl: project URL
    :return: either project asset folder or LIST of commission base asset folder. Raises InvalidVidispineID or InvalidPlutoType.
    """
    urlparts = urllib.parse.urlparse(projecturl)

    pathparts = urlparts.path.split("/")
    #last path element should be the ID
    if not is_vidispine.match(pathparts[-2]):
        raise InvalidVidispineID(pathparts[-2])

    if pathparts[-3]=="project":
        return lookup_project_assetfolder(pathparts[-2])
    elif pathparts[-3]=="commission":
        return lookup_commission_basepath(pathparts[-2])
    else:
        raise InvalidPlutoType(pathparts[-3])


def find_sensitive_projects():
    xmldoc = """<ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
	<field>
		<name>gnm_storage_rule_sensitive</name>
		<value>storage_rule_sensitive</value>
	</field>
	<field>
		<name>gnm_type</name>
		<value>project</value>
	</field>
</ItemSearchDocument>"""

    response = requests.put("{0}/API/search".format(args.vsurl),
                            data=xmldoc,
                            headers={'Accept': 'application/json', 'Content-Type': 'application/xml'},
                            auth=(auth['user'], auth['password'])
                            )
    if response.status_code==200:
        data = response.json()
        logger.info("Found {0} potential sensitive projects".format(data["hits"]))
        project_list = list(map(lambda entry: entry["id"], filter(lambda entry: entry["type"]=="Collection", data["entry"])))
        logger.info("Count after filter: {0}".format(len(project_list)))
        return project_list
    else:
        raise HttpError(response.text)


def read_authfile(authfile):
    with open(authfile, "r") as f:
        return yaml.load(f.read(), Loader=SafeLoader)


### START MAIN
parser = argparse.ArgumentParser()
parser.add_argument("--file", dest='sourcefile', help="CSV spreadsheet to read")
parser.add_argument("--column", dest='col_index', help="Zero-based index of the column that contains URLs", default=2)
parser.add_argument("--proto", dest='proto', help="Specify http or https protocol", default="https")
parser.add_argument("--host", dest='host', help="Host that pluto is running on", default="localhost")
parser.add_argument("--vsurl", dest='vsurl', help="URL to access Vidispine", default="https://localhost:8080")
parser.add_argument("--skip-sensitive", dest='skip_sensitive', help="Don't scan for 'sensitive' projects to add to the list", action="store_true", default=False)
parser.add_argument("--authfile", dest='authfile', default="auth.yaml")
parser.add_argument("--output", dest='outputfile', default='excludepaths.lst')
args = parser.parse_args()
auth = read_authfile(args.authfile)

exclude_list = []
for entry in load_file(args.sourcefile, int(args.col_index)):
    logger.info("Got {0}".format(entry))
    try:
        new_paths = lookup_paths(entry)
        if isinstance(new_paths, list):
            exclude_list = exclude_list + new_paths
        else:
            exclude_list.append(new_paths)
        #logger.debug(exclude_list)
    except InvalidVidispineID as e:
        logger.error("Invalid vidispine ID: {0}".format(str(e)))

logger.info("Completed list")

if not args.skip_sensitive:
    logger.info("Adding sensitive projects (from VS records)")
    extra_project_list = find_sensitive_projects()
    for projectid in extra_project_list:
        try:
            exclude_list.append(lookup_project_assetfolder(projectid))
        except InvalidVidispineID as e:
            logger.error("Invalid vidispine ID: {0}".format(str(e)))
        except AssetFolderNotFound as e:
            logger.warning("No asset folder found for {0}".format(str(e)))

with open(args.outputfile, "w") as fout:
    for entry in exclude_list:
        fout.write(entry + "\n")
