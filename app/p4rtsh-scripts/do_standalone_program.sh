#!/bin/bash

# for running program_many_flows.py outside of a docker-compose environment

docker run -ti \
    -v $PWD:/scripts/ \
    --entrypoint /scripts/do_program.sh \
    p4lang/p4runtime-sh $@
