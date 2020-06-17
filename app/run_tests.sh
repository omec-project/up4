#!/usr/bin/env bash

set -e

# Get project path first so there is no ".." in it. `docker run -v` doesn't like dots
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." > /dev/null 2>&1 && pwd)"
APP_DIR=${PROJECT_DIR}/app/
P4C_OUT=${PROJECT_DIR}/p4src/build/
PTF_DIR=${PROJECT_DIR}/ptf/

onos_url=http://localhost:8181/onos
onos_curl="curl --fail -sSL --user onos:rocks --noproxy localhost"


rand=${RANDOM}

# onos
onosImageName=onosproject/onos:2.2-latest
onosRunName=onos-${rand}

# bmv2 and ptf image
bmv2ImageName=ccasconeonf/up4-ptf:latest
bmv2RunName=bmv2-${rnd}

onosWarmUpTime=10


function stop() {
    set +e
    echo "*** Stopping ${onosRunName}..."
    docker stop -t0 "${onosRunName}" > /dev/null
    echo "*** Stopping ${bmv2RunName}..."
    docker stop -t0 "${bmv2RunName}" > /dev/null
    exit ${exitVal}
}
trap stop EXIT
trap stop INT

echo "*** Starting bmv2+ptf image in Docker (${bmv2RunName})..."
docker run --name "${runName}" -d --privileged --rm \
    -v "${PTF_DIR}":/ptf -w /ptf \
    -v "${P4C_OUT}":/p4c-out \
    "${PTF_DOCKER_IMG}" \
    ./lib/start_bmv2.sh > /dev/null


echo "*** Starting ONOS in Docker (${onosRunName})..."
docker run --name ${dutRunName} -d -it --privileged --rm \
    -e ONOS_APPS=gui2,drivers.bmv2,pipelines.fabric,lldpprovider,hostprovider,fwd \ 
    -p 8101:8101 -p 8181:8181 \	
    --entrypoint "/fabric-p4test/run/bmv2/stratum_entrypoint.sh" \
    --network "container:${bmv2RunName}" \
    -v "${ROOT_DIR}":/fabric-p4test \
    ${onosImageName}



echo "*** Sleeping for ${onosWarmUpTime} seconds to let ONOS warm up"
sleep ${onosWarmUpTime}

echo "*** Activating apps and installing static routes"
${APP_DIR}/util/onos-cmd app activate segmentrouting
${APP_DIR}/util/onos-cmd app activate pipelines.fabric
${APP_DIR}/util/onos-cmd app activate netcfghostprovider
${APP_DIR}/util/onos-cmd app activate org.onosproject.protocols.grpc

sleep 1

${APP_DIR}/util/onos-cmd route-add 17.0.0.0/24 140.0.100.1  

sleep 1

echo "*** Loading PTF netcfg"
${onos_curl} -X POST -H 'Content-Type:application/json' \
    ${onos_url}/v1/network/configuration -d@${APP_DIR}/util/netcfg.json

sleep 3

echo "*** Loading UP4 app"
${APP_DIR}/onos-tools/onos-app localhost install! ${APP_DIR}/target/up4-app-0.1.oar

sleep 2

echo "*** Running PTF tests"
docker exec "${bmv2RunName}" ./lib/runner.py \
    --bmv2-json /p4c-out/bmv2.json \
    --p4info /p4c-out/p4info.bin \
    --grpc-addr localhost:51001 \
    --device-id 1 \
    --ptf-dir ./tests \
    --cpu-port 255 \
    --port-map /ptf/lib/port_map.json "${@}"

exitVal=$?

stop
