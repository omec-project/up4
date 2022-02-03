# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0

# Regular mn-stratum with dbuf binary and scapy.

ARG DBUF_IMAGE
ARG MN_STRATUM_IMAGE

FROM $DBUF_IMAGE as dbuf
RUN mkdir /output
RUN cp $(which dbuf) /output/dbuf

FROM $MN_STRATUM_IMAGE
COPY --from=dbuf /output/dbuf /usr/local/bin

RUN install_packages python3 python3-pip python3-setuptools
RUN pip3 install wheel
RUN pip3 install scapy==2.4.4

ENV PATH=/up4/bin:${PATH}