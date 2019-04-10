FROM centos:7.6.1810

RUN adduser ci
COPY setup/bintray-sbt-rpm.repo /etc/yum.repos.d
RUN yum -y install epel-release yum-utils && yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
RUN yum -y install java-1.8.0-openjdk-headless rpm-build docker-ce-cli sbt awscli device-mapper-persistent-data lvm2 && yum -y autoremove && yum -y clean all && rm -rf /var/cache/yum

USER ci
WORKDIR /home/ci

### do an initial run to download jars, speeds up execution at the expense of a larger image
RUN echo exit | sbt

CMD sbt