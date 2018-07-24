FROM centos:7.4.1708

RUN adduser ci
COPY setup/bintray-sbt-rpm.repo /etc/yum.repos.d
RUN yum -y install epel-release
RUN yum -y install java-1.8.0-openjdk-headless java-1.8.0-openjdk-devel rpm-build sbt awscli && yum -y autoremove && yum -y clean all && rm -rf /var/cache/yum

USER ci
WORKDIR /home/ci

### do an initial run to download jars, speeds up execution at the expense of a larger image
RUN echo exit | sbt

CMD sbt