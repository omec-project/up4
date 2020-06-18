#!/usr/bin/env bash

cd /p4runtime-sh/
source $VENV/bin/activate

/scripts/program_tables.py $@
