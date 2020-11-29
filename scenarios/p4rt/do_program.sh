#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

set -e
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# shellcheck disable=SC1090
source "$VENV"/bin/activate

"${DIR}"/program_many_flows.py $@
