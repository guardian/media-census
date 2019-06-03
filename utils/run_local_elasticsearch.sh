#!/usr/bin/env bash


DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." >/dev/null 2>&1 && pwd )"

docker network ls | grep mediacensus-dev >/dev/null 2>&1

if [ "$?" != "0" ]; then
    echo Setting up docker network....
    docker network create mediacensus-dev
fi

if [ ! -d "$DIR/esdata" ]; then
    echo Elasticsearch data directory "$DIR/esdata" does not exist. Creating from fresh...
    mkdir -p "$DIR/esdata"
fi

docker run -v "$DIR/esdata":/usr/share/elasticsearch/data --name mediacensus-elasticsearch --network=mediacensus-dev -p 9200:9200 -e "ES_JAVA_OPTS=-Xms1g -Xmx1g" docker.elastic.co/elasticsearch/elasticsearch:6.8.0
