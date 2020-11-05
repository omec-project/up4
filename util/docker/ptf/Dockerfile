# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

# Docker image to run PTF-based data plane tests using stratum_bmv2

ARG MN_STRATUM_SHA

FROM bitnami/minideb:stretch as builder

ENV BUILD_DEPS \
    python-pip \
    python-setuptools \
    git
RUN install_packages $BUILD_DEPS

RUN mkdir -p /ouput

ENV PIP_DEPS \
    git+https://github.com/secdev/scapy.git@31bf7cea2bb44f9dc52708db7e989a525757f288 \
    git+https://github.com/p4lang/ptf.git@10a2d4b7d704a430218adc8accf8115376a356eb \
    googleapis-common-protos==1.6.0 \
    ipaddress
RUN pip install --no-cache-dir --root /output $PIP_DEPS

FROM opennetworking/mn-stratum:latest@$MN_STRATUM_SHA as runtime

LABEL description="Docker image to run PTF-based data plane tests using stratum_bmv2"

ENV RUNTIME_DEPS \
    make
RUN install_packages $RUNTIME_DEPS

COPY --from=builder /output /

ENV DOCKER_RUN true

ENTRYPOINT []
