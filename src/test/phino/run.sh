#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail
shopt -s globstar nullglob

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "${here}/../../.." && pwd)"

if ! command -v phino >/dev/null 2>&1; then
  echo "phino is not installed, see https://github.com/objectionary/phino"
  exit 1
fi

if ! command -v yq >/dev/null 2>&1; then
  echo "yq is not installed, see https://github.com/mikefarah/yq"
  exit 1
fi

cd "${root}"

tests=("${here}"/*.yml)
total=${#tests[@]}
if [ "${total}" -eq 0 ]; then
  echo "No test files found in ${here}"
  exit 1
fi

passed=0
failed=0
failed_names=()
echo "Found ${total} test(s) in ${here}"

for yml in "${tests[@]}"; do
  name="$(basename "${yml}" .yml)"
  echo "Running ${name}..."
  tmp="$(mktemp -d)"
  input="${tmp}/input.phi"
  output="${tmp}/output.phi"
  raw="$(yq -r '.input' "${yml}")"
  trimmed="${raw#"${raw%%[![:space:]]*}"}"
  if [ "${trimmed:0:1}" = '{' ] || [ "${trimmed:0:1}" = 'Φ' ]; then
    printf '%s\n' "${raw}" > "${input}"
  else
    printf '{%s}\n' "${raw}" > "${input}"
  fi
  rules=()
  while IFS= read -r pat; do
    [ -z "${pat}" ] && continue
    # shellcheck disable=SC2206
    matched=("${root}"/${pat})
    if [ ${#matched[@]} -eq 0 ] || [ ! -e "${matched[0]}" ]; then
      echo "  no files matched '${pat}'"
      rules=()
      break
    fi
    for f in "${matched[@]}"; do
      if [ -f "${f}" ]; then
        rules+=("--rule=${f}")
      fi
    done
  done < <(yq -r '.rules[]' "${yml}")
  if [ ${#rules[@]} -eq 0 ]; then
    echo "  FAIL: no rules to apply"
    failed=$(( failed + 1 ))
    failed_names+=("${name}")
    rm -rf "${tmp}"
    continue
  fi
  if ! phino rewrite --sweet "${rules[@]}" "${input}" > "${output}" 2> "${tmp}/err"; then
    echo "  FAIL: phino rewrite returned non-zero"
    sed 's/^/    /' "${tmp}/err"
    failed=$(( failed + 1 ))
    failed_names+=("${name}")
    rm -rf "${tmp}"
    continue
  fi
  ok=true
  count="$(yq -r '.expected | length' "${yml}")"
  for (( i = 0; i < count; i++ )); do
    pattern="$(yq -r ".expected[${i}]" "${yml}")"
    if ! phino match --pattern "${pattern}" "${output}" > "${tmp}/match" 2>&1; then
      echo "  FAIL: expected pattern #$(( i + 1 )) did not match"
      printf '    pattern:\n'
      printf '%s\n' "${pattern}" | sed 's/^/      /'
      printf '    actual:\n'
      sed 's/^/      /' "${output}"
      ok=false
      break
    fi
  done
  if ${ok}; then
    echo "  OK (${count} pattern(s) matched)"
    passed=$(( passed + 1 ))
  else
    failed=$(( failed + 1 ))
    failed_names+=("${name}")
  fi
  rm -rf "${tmp}"
done

echo ""
echo "Total: ${total}, passed: ${passed}, failed: ${failed}"
if [ "${failed}" -gt 0 ]; then
  echo "Failures:"
  for n in "${failed_names[@]}"; do
    echo "  - ${n}"
  done
  exit 1
fi
