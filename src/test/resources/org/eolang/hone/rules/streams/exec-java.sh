#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2023-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

class=$1
tmp=$2
out=$3

out=$(realpath "${out}")

mkdir -p "$(dirname "${out}")"

mkdir -p "${tmp}"
rm -rf "${tmp:?}"/*
cp "${class}" "${tmp}/Foo.class"

cd "${tmp}"
java -enableassertions Foo > "${out}"
