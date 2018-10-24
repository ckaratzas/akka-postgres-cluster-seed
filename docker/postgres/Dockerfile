FROM postgres

RUN sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt/ $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
RUN apt-get update
RUN apt-get install -y wget ca-certificates
RUN wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add -
RUN apt-get -y upgrade

RUN apt-get -y install postgresql-10-cron

COPY postgres-cron.conf /postgresql.conf

COPY docker-entrypoint-initdb.d /docker-entrypoint-initdb.d

CMD ["postgres", "-c", "config_file=/postgresql.conf"]