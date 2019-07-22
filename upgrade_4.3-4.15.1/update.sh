#!/bin/bash

#set -e


BASE="https://github.com/IQSS/dataverse/releases/download"
GLASSFISH_HOME="/home/glassfish/glassfish-4.1"
IAMHERE=$(pwd)


if [[ -z "$PG_HOST" ]]
then
    echo "Need PG_HOST=ip to postgresql."
    exit 1
fi
if [[ -z "$PG_USER" ]]
then
    echo "Need PG_USER for postgresql."
    exit 1
fi
if [[ -z "$PG_DB" ]]
then
    echo "Need PG_USER for postgresql."
    exit 1
fi


function libraries {
    sudo apt install postgresql-client-common postgresql-client
}

function go {
    echo "https://github.com/IQSS/dataverse/releases/tag/v${1}"
    workdir="${IAMHERE}/${1}"
    if [[ ! -d "$workdir" ]]
    then
        mkdir "$workdir"
    fi
    cd "$workdir"
}

function get {
    url="$1"
    filename=$(basename "$url")
    if [[ -f "$filename" ]]
    then
        echo "File present ${filename}"
    else
        wget "$url" -O "$filename"
    fi
}

function stop-domain {
    echo "Stop domain"
    sudo -u glassfish ${GLASSFISH_HOME}/bin/asadmin stop-domain
}

function start-domain {
    echo "Start glassfish"
    sudo chown glassfish:glassfish "$GLASSFISH_HOME"
    sudo -u glassfish ${GLASSFISH_HOME}/bin/asadmin start-domain
}

function undeploy {
    app=$(ls ${GLASSFISH_HOME}/glassfish/domains/domain1/applications | grep dataverse-)
    if [[ -z "$app" ]]
    then
        app="$1"
    fi
    echo "Undeploy ${app}"
    sudo -u glassfish ${GLASSFISH_HOME}/bin/asadmin undeploy "$app"

    echo "Remove generated"
    sudo rm -rf ${GLASSFISH_HOME}/glassfish/domains/domain1/generated

    stop-domain
    start-domain
}

function deploy {
    app=$(ls | grep dataverse-*.war)
    echo "Deploy ${app}"
    sudo -u glassfish ${GLASSFISH_HOME}/bin/asadmin deploy "$app"

    stop-domain
    start-domain
}

function sql {
    file="$1"
    if [[ -z "file" ]]
    then
        file="update.sql"
    fi
    echo "SQL update ${file}"
    psql -h "$PG_HOST" -U "$PG_USER" -d "$PG_DB" -f "$file"
}

function sqlc {
    s="$1"
    echo "SQL update ${s}"
    psql -h "$PG_HOST" -U "$PG_USER" -d "$PG_DB" <<< "$s"
}

function dataverse-4.3.1 {
    version=4.3.1
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/schema.xml
    undeploy "4.3"
    sudo cp domain.xml "${GLASSFISH_HOME}/glassfish/domains/domain1/config/domain.xml"
    deploy
}

function dataverse-4.4 {
    version=4.4
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/schema.xml
    get ${BASE}/v${version}/citation.tsv
    undeploy "4.3.1"
    deploy
    curl http://localhost:8080/api/admin/datasetfield/load -X POST --data-binary @citation.tsv -H "Content-type: text/tab-separated-values"
}

function dataverse-4.5 {
    version=4.5
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.4_to_v4.5.sql.txt
    get ${BASE}/v${version}/schema.xml
    undeploy "4.4"
    # -Ddataverse.timerServer=true  already added in 4.3.1
    deploy
    sql upgrade_v4.4_to_4.5.sql.txt
}

function dataverse-4.5.1 {
    version=4.5.1
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.5_to_v4.5.1.sql
    undeploy "4.5"
    deploy
    sql upgrade_v4.5_to_v4.5.1.sql
}

function dataverse-4.6 {
    version=4.6
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/schema.xml
    get ${BASE}/v${version}/social_science.tsv
    get ${BASE}/v${version}/upgrade_v4.5.1_to_v4.6.sql
    undeploy "4.5.1"
    deploy
    sql upgrade_v4.5.1_to_v4.6.sql
    curl http://localhost:8080/api/admin/datasetfield/load -X POST --data-binary @social_science.tsv -H "Content-type: text/tab-separated-values"
}

function dataverse-4.6.1 {
    version=4.6.1
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/shibAuthProvider.json
    get ${BASE}/v${version}/upgrade_v4.6_to_v4.6.1.sql
    undeploy "4.6"
    deploy
    sql upgrade_v4.6_to_v4.6.1.sql
    curl -X DELETE http://localhost:8080/api/admin/settings/:ShibEnabled
    curl -X PUT -d builtin http://localhost:8080/api/admin/settings/:builtin
}

function dataverse-4.6.2 {
    version=4.6.2
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.6.1_to_v4.6.2.sql
    get ${BASE}/v${version}/citation.tsv
    undeploy "4.6.1"
    deploy
    sql upgrade_v4.6.1_to_v4.6.2.sql
    curl http://localhost:8080/api/admin/datasetfield/load -X POST --data-binary @citation.tsv -H "Content-type: text/tab-separated-values"
}

function dataverse-4.7 {
    version=4.7
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.6.2_to_v4.7.sql
    undeploy "4.6.2"
    deploy

    # Run this script if you want to preserve the word Dataverse after your current dataverse names.
    # Uncomment the UPDATE line before running it.
    sql upgrade_v4.6.2_to_v4.7.sql
}

function dataverse-4.7.1 {
    version=4.7.1
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.7_to_v4.7.1.sql
    undeploy "4.7"
    deploy

    # Old account giving errors when running the upgrade sql
    sqlc "DELETE FROM explicitgroup_authenticateduser WHERE containedauthenticatedusers_id = 6;"
    sqlc "DELETE FROM apitoken WHERE authenticateduser_id = 6;"
    sqlc "UPDATE dvobject SET releaseuser_id = 2 WHERE releaseuser_id =  6 ;"
    sql upgrade_v4.7_to_v4.7.1.sql
    # Optionally restore old behavior of requiring API tokens to use Search API.
    # Search API does not require token now but if want to preserve old behavior run command:
    # curl -X PUT -d true http://localhost:8080/api/admin/settings/:SearchApiRequiresToken
}

function dataverse-4.8 {
    version=4.8
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/citation.tsv
    get ${BASE}/v${version}/3561-update.sql
    get ${BASE}/v${version}/upgrade_v4.7.1_to_v4.8.sql

    undeploy "4.7.1"
    deploy
    sql upgrade_v4.7.1_to_v4.8.sql
    sql 3561-update.sql
    curl http://localhost:8080/api/admin/datasetfield/load -X POST --data-binary @citation.tsv -H "Content-type: text/tab-separated-values"
}

function dataverse-4.8.1 {
    version=4.8.1
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.8"
    deploy
}

function dataverse-4.8.2 {
    version=4.8.2
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.8.1"
    deploy
}

function dataverse-4.8.3 {
    version=4.8.3
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.8.2"
    sudo rm "${GLASSFISH_HOME}/glassfish/lib/postgresql-9.1.jdbc4-latest.jar"
    sudo cp postgresql-42.2.6.jar "${GLASSFISH_HOME}/glassfish/lib/"
    sudo rm -rf ${GLASSFISH_HOME}/glassfish/domains/domain1/osgi-cache/felix
    deploy
}

function dataverse-4.8.4 {
    version=4.8.4
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.8.3_to_v4.8.4.sql

    undeploy "4.8.3"
    deploy
    sql upgrade_v4.8.3_to_v4.8.4.sql
}

function dataverse-4.8.5 {
    version=4.8.5
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.8.4"
    deploy
}

function dataverse-4.8.6 {
    version=4.8.6
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.8.5_to_v4.8.6.sql

    undeploy "4.8.5"
    deploy
    sql upgrade_v4.8.5_to_v4.8.6.sql
}

function dataverse-4.9 {
    version=4.9
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/citation.tsv
    get ${BASE}/v${version}/schema.xml
    get ${BASE}/v${version}/solrconfig.xml
    get ${BASE}/v${version}/upgrade_v4.8.6_to_v4.9.sql

    undeploy "4.8.6"
    deploy
    sql upgrade_v4.8.6_to_v4.9.sql

    # Optionally enable provenance
    curl -X PUT -d 'true' http://localhost:8080/api/admin/settings/:ProvCollectionEnabled
    
    curl http://localhost:8080/api/admin/datasetfield/load -X POST --data-binary @citation.tsv -H "Content-type: text/tab-separated-values"

    get https://archive.apache.org/dist/lucene/solr/7.3.0/solr-7.3.0.tgz
    tar xvzf solr-7.3.0.tgz
    cp -r solr-7.3.0/server/solr/configsets/_default solr-7.3.0/server/solr/collection1/
    sudo mkdir /usr/local/solr
    sudo mv solr-7.3.0 /usr/local/solr/

    cp schema.xml /usr/local/solr/solr-7.3.0/server/solr/collection1/conf/schema.xml
    cp solrconfig.xml /usr/local/solr/solr-7.3.0/server/solr/collection1/conf/solrconfig.xml

    sudo chown -R solr:solr /usr/local/solr
    get http://guides.dataverse.org/en/4.9/_downloads/solr.service
    sudo cp solr.service /etc/systemd/system/
    sudo systemctl daemon-reload
    sudo systemctl start solr.service
    sudo systemctl enable solr.service

    sudo rm /etc/init.d/solr
    rm -rf /home/solr/solr/solr-4.6.0

    curl http://localhost:8080/api/admin/index/clear
    curl http://localhost:8080/api/admin/index
    curl -X DELETE http://localhost:8080/api/admin/index/timestamps
    curl http://localhost:8080/api/admin/index/continue
}

function dataverse-4.9.1 {
    version=4.9.1
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.9"
    deploy
}

function dataverse-4.9.2 {
    version=4.9.2
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.9.1_to_v4.9.2.sql

    undeploy "4.9.1"
    deploy
    sql upgrade_v4.9.1_to_v4.9.2.sql
}

function dataverse-4.9.3 {
    version=4.9.3
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/schema.xml
    get ${BASE}/v${version}/upgrade_v4.9.2_to_v4.9.3.sql

    undeploy "4.9.2"
    deploy
    sql upgrade_v4.9.2_to_v4.9.3.sql
    sudo cp schema.xml /usr/local/solr/solr-7.3.0/server/solr/collection1/conf/schema.xml
    sudo service solr restart
}

function dataverse-4.9.4 {
    version=4.9.4
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.9.3"
    deploy
}

function dataverse-4.10 {
    version=4.10
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/citation.tsv
    get ${BASE}/v${version}/schema.xml
    get ${BASE}/v${version}/solrconfig.xml
    get ${BASE}/v${version}/upgrade_v4.9.4_to_v4.10.sql

    undeploy "4.9.4"
    deploy
    sql upgrade_v4.9.4_to_v4.10.sql
    sudo cp schema.xml /usr/local/solr/solr-7.3.0/server/solr/collection1/conf/schema.xml
    sudo cp solrconfig.xml /usr/local/solr/solr-7.3.0/server/solr/collection1/conf/solrconfig.xml
    service solr restart
    curl http://localhost:8080/api/admin/datasetfield/load -X POST --data-binary @citation.tsv -H "Content-type: text/tab-separated-values"
}

function dataverse-4.10.1 {
    version=4.10.1
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.10"
    deploy
}

function dataverse-4.11 {
    version=4.11
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/upgrade_v4.10.1_to_v4.11.sql

    undeploy "4.10.1"
    deploy
    sql upgrade_v4.10.1_to_v4.11.sql

    for key in GoogleAnalyticsCode PiwikAnalyticsId PiwikAnalyticsHost PiwikAnalyticsTrackerFileName
    do
        curl -X DELETE http://localhost:8080/api/admin/settings/:$key
    done

    echo "See http://guides.dataverse.org/en/latest/installation/config.html#web-analytics-code"
}

function dataverse-4.12 {
    version=4.12
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/schema.xml

    undeploy "4.11"
    deploy
    sudo cp schema.xml /usr/local/solr/solr-7.3.0/server/solr/collection1/conf/schema.xml
    service solr restart
}

function dataverse-4.13 {
    version=4.13
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.12"
    deploy
}

function dataverse-4.14 {
    version=4.14
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.13"
    deploy
}

function dataverse-4.15 {
    version=4.15
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    get ${BASE}/v${version}/dvinstall.zip
    get ${BASE}/v${version}/jhove.conf
    get ${BASE}/v${version}/schema.xml

    undeploy "4.14"
    sudo cp jhove.conf "${GLASSFISH_HOME}/glassfish/domains/domain1/config/jhove.conf"
    sudo cp jhoveConfig.xsd "${GLASSFISH_HOME}/glassfish/domains/domain1/config/jhoveConfig.xsd"
    stop-domain
    sudo mv "$GLASSFISH_HOME" /usr/local/
    ln -s /usr/local/glassfish-4.1 "$GLASSFISH_HOME"

    # Double
    sqlc "UPDATE authenticateduser SET useridentifier = 'FaraiNyika2' where id = 144;"

    deploy
    sudo cp schema.xml /usr/local/solr/solr-7.3.0/server/solr/collection1/conf/schema.xml
    sudo cp solrconfig.xml /usr/local/solr/solr-7.3.0/server/solr/collection1/conf/solrconfig.xml

    curl -X DELETE http://localhost:8080/api/admin/index/timestamps
    curl http://localhost:8080/api/admin/index/continue
}

function dataverse-4.15.1 {
    version=4.15.1
    go "$version"
    get ${BASE}/v${version}/dataverse-${version}.war
    # get ${BASE}/v${version}/dvinstall.zip

    undeploy "4.14"
    deploy
}

