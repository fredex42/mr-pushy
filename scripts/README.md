# Building your push list

This directory contains scripts to make it easier to build your push list, while excluding projects that are not completed yet.

## Step zero: prerequisites

You need to have Python3 installed, and ensure that the requirements from this directory are installed:
``` 
pip3 install -r requirements.txt
```

## Step one: exclude list

You need to generate a list of the paths to exclude from upload.  It's assumed that you are doing this from a CSV spreadsheet
export, that contains one column of Pluto URLs.

First, you need to create a simple YAML file with your credentials:
```yaml
user: {your-username}
password: {your-password}
```
save this as auth.yaml.

Simply run:
```bash
./generate_exclude_list.py --file {path/to/csvfile} --column {idx} --host {plutohost} --authfile auth.yaml
```
`idx` is the number of the column that contains the Pluto URLs (starting at zero)

This script supports more options, run with `--help` or check the source for more information.

It will check the asset folder path for each project listed, and for each commission will find a project with a valid
asset folder and take off the project part to get the commission asset root.

The result will be a file called `excludepaths.lst`

## Step two: complete media list

Next you need a list of _all_ potential media to upload.

Simply:
```bash
sudo find /path/to/media -type f | grep -v '/\\.' > ${HOME}/all_media.lst
```
It's important to get the whole media path here, so don't use a relative path in `find` or the next stage will fail.

## Step three: upload list

Get hold of both the full media list and the exclude list, and run `build_output_list`:

```bash
$ ./build_output_list.py [--allfiles all_media.lst] [--exclude exclude_paths.lst] [--output to_upload.lst]
```
This will generate a file `to_upload.lst` that contains everything in `all_media.lst`  that does NOT start with any path in `exclude_paths.lst`.

You can then  run the main app on `to_upload.lst`.

## What's https-certifi?

This is a utility script that will add a custom CA certificate to your Certifi bundle if required to access a given server 
(taken with thanks from https://incognitjoe.github.io/adding-certs-to-requests.html).

Internally signed certs are common on internal systems, and ours is no exception.

The script will attempt an HTTPS connection to a given server, and use certifi's functions to add it to the CA bundle
used by requests.

To use it, run:

```bash
$ ./https-certifi --host {server-to-check} --cafile {path-to-ca-public-certificate}
```

If an SSL error is encountered performing an HTTPS get to {server-to-check} then the contents of the PEM-encoded
text file at {path-to-ca-public-certificate} is appended to certifi's CA bundle and a message is displayed to explain this.

You then run the same command again, and you should see that the connection works without error
