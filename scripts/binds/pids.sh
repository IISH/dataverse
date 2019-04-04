#!/bin/bash
#
# Bind pids
#
# Usage ./pids.sh KEY


PID_WEBSERVICE_KEY="$1"
PID_WEBSERVICE_ENDPOINT="https://pid.socialhistoryservices.org/secure/"

sudo -u dvnapp psql dvndb -q -c "select id, authority, identifier FROM dataset WHERE protocol = 'hdl'" > out.txt
# E.g.
# 3304 | 12345     | UJY101
# 3309 | 12345     | E7RMO2
# 3319 | 12345     | VIUUH3

while read line
do
    IFS="|" read id authority identifier <<< "$line"
    id=$(echo "$id" | xargs)
    authority=$(echo "$authority" | xargs)
    identifier=$(echo "$identifier" | xargs)
    pid="${authority}/${identifier}"
    soapenv="<?xml version='1.0' encoding='UTF-8'?>  \
        <soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/' xmlns:pid='http://pid.socialhistoryservices.org/'>  \
            <soapenv:Body> \
                <pid:UpsertPidRequest> \
                    <pid:na>${authority}</pid:na> \
                    <pid:handle> \
                        <pid:pid>${pid}</pid:pid> \
                        <pid:locAtt> \
                                <pid:location weight='1' href='https://datasets.socialhistory.org/dataset.xhtml?persistentId=hdl:${pid}'/> \
                                <pid:location weight='0' href='https://datasets.socialhistory.org/api/meta/dataset/${id}' view='ddi'/> \
                            </pid:locAtt> \
                    </pid:handle> \
                </pid:UpsertPidRequest> \
            </soapenv:Body> \
        </soapenv:Envelope>"

    wget -O "/tmp/${id}.xml" --header="Content-Type: text/xml" \
        --header="Authorization: bearer ${PID_WEBSERVICE_KEY}" --post-data "$soapenv" \
        --no-check-certificate "$PID_WEBSERVICE_ENDPOINT"
done < out.txt