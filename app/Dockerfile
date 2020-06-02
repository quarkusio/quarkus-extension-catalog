FROM nginx

LABEL author "Quarkus Team <quarkus-dev@googlegroups.com>"
LABEL maintainer "Quarkus Team <quarkus-dev@googlegroups.com>"

ENV LANG=en_US.utf8

ADD nginx.conf /etc/nginx/nginx.conf
ADD registry.json /usr/share/nginx/html/
ADD registry.json.gz /usr/share/nginx/html/
