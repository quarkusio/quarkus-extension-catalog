FROM nginx

LABEL author "Quarkus Team <quarkus-dev@googlegroups.com>"
LABEL maintainer "Quarkus Team <quarkus-dev@googlegroups.com>"
ARG REGISTRY_VERSION
ENV LANG=en_US.utf8

ADD nginx.conf /etc/nginx/nginx.conf.tmp
RUN envsubst '$REGISTRY_VERSION' < /etc/nginx/nginx.conf.tmp > /etc/nginx/nginx.conf &&\
    rm /etc/nginx/nginx.conf.tmp

ADD registry.json /usr/share/nginx/html/
ADD registry.json.gz /usr/share/nginx/html/
