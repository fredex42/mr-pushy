Upload Flush List (Multi-threaded) - aka Mr Pushy
====

This project is a program for emergency use, to quickly "flush" large media files
into S3 storage and remove them from local storage.

A paranoid approach is taken by re-verifying the e-tag and file size once upload
is complete in order to ensure that no files become corrupted during the process.

Description
---

#### Input
As an input, the program expects a list of (absolute) file paths indicating the
specific files to upload and remove.  We generate this from a seperate database.
Two formats are supported:

1. Plain file list, as a Unix standard UTF-8 text file with one file on each line,
separated by Linefeed (LF) characters
2. CSV format, as a Unix standard UTF-8 text file, with two colums where the second
column indicates the filepath

See the samples in `src/test/resources` for examples that are used in testing.

There are scripts in the `scripts/` directory to assist with building these lists, consult the README
there for more information.

#### Arguments
You must specify the path to the list of files you want to upload as the only conventional commandline
argument.  The program will exit if you don't do this.

Runtime parameters are specified as system properties.  They can be specified on the
commandline using the standard -Dparameter=value syntax:

- **uploadThreads** (number; default 8) - maximum number of threads to use for upload.  A static threadpool of this 
size is created at startup.
- **verifyThreads** (number; default 36) - maximum number of threads to use for checksum verification.  A static 
threadpool of this size is created at startup.
- **reallyDelete** (boolean; default false) - if set to any string, delete files from local storage.
Otherwise treat the run as a test and don't delete anything.
- **limit** (number; default not set) - if set, stop after this many files. Used for testing.
- **destBucket** (string; must be set) - upload to this S3 bucket
- **stripPathSegments** (number; default 5) - remove this number of segments from the local path to generate
the remote path.  For example, if the local path is `/mnt/media_volume/my_project/files/card01/file.mxf` and
**stripPathSegments** is set to `2`, then the remote path will be
 `s3://{destBucket}/my_project/files/card01/file.mxf`.
- **chunkSize** (number; default 50) - upload chunk size in megabytes.  This allows you to optimize for your
disk and network speeds.  The chunk size can't be less than 4Mb due to S3 limits
and can't be so small that any file has more than 999 chunks.
Multiple chunks are uploaded in parallel, one per thread.
- **hideNotFound** (boolean; default false) - By default, a warning is shown if any requested file is not
 found on the local disk.  Setting this parameter to any string hides these warnings.

#### Logging
Logging is carried out by the standard log4j plugin which is configured via the `logback.xml` file.  In order
to customise the logging, copy the existing `logback.xml` file from `src/main/resources/logback.xml` to your
server and edit it to suit your needs.  Then run the program with the parameter `FIXME` to use your updated
configuration.  The default logger outputs to standard out and shows the MtUploader at `DEBUG` level.
See online documentation about configuring logback.xml to understand how to output to a file, adjust logging
priorities, formats etc.

#### Do Docker?

The app itself can now build as a Docker container, by running `sbt docker:publish` instead of `sbt rpm:packageBin`
 
Once you have built the app, it can then be run in a Docker environment with the following command:

```
$ docker run --rm -it -v /path/to/pushlist.lst:/mnt/data/pushlist.lst -v /path/to/media:/path/to/media -e JAVA_OPTS="-DdestBucket={bucket} -DreallyDelete={option}..." mr-pushy:{revision} /mnt/data/pushlist.lst
```

Note that in order for this to work, you need to bind-mount your media path to the SAME path in the container in order
for the paths in your push list to be accurate.

The "arguments" are the same as above, but must be specified in the `JAVA_OPTS` environment variable as above.

Compilation
---
This project uses `sbt` and the SBT Universal Packaging plugin to make your life easier.

With `sbt` installed, on a RedHat linux-based system (or container) simply run:
```
$ sbt rpm:packageBin
```
to download all necessary requirements, compile, link and make an RPM of the products.
Other packageBin options from Universal Packager should work but are untested.

Alternatively, run:
``` 
$ sbt -Ddocker.username={yourname} [-Ddocker.host={yourrepo}] [-Dbuild.number={revision}] docker:publish
```
to publish as a Docker container to your local repository

#### Do Docker?

If you have Docker installed, then you can use the `Dockerfile` in the `.circleci` directory
to quickly make an environment suitable for compiling, building and running the program.

Simply change to the .circleci directory and run:
```
$ docker build . -t {yourname}/{imagename}:{version}
$ docker run --rm -it -mount 'type=bind,src={checkout-path},dst=/mnt' {yourname}/{imagename}:{version}
```
substituting the values in braces for your setup.  Then in the Docker container:
```
$ cd mnt
$ sbt rpm:packageBin
$ mv target/rpm/RPMS/noarch/ /mnt
```
to get the built RPM package in your current directory.
