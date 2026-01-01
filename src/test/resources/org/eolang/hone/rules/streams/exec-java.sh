#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
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
