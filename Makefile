# The MIT License (MIT)
#
# Copyright (c) 2024 Objectionary.com
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included
# in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

.ONESHELL:
.PHONY: clean all rmi verify
.SHELLFLAGS := -e -o pipefail -c
SHELL := /bin/bash

all: verify rmi

quick: target/make
	TARGET=$(realpath $<) ./src/docker/entry.sh

target/image.txt: target src/docker/Dockerfile src/docker/entry.sh
	sudo docker build -t hone-maven-plugin "$$(pwd)/src/docker"
	sudo docker build -t hone-maven-plugin -q "$$(pwd)/src/docker" > "$@"

target/entry.exit: target/image.txt target/make
	img=$$(cat $<)
	docker run --rm -v "$$(realpath "$$(pwd)/target/make"):/target" \
		-e "TARGET=/target" \
		"$${img}"
	echo "$$?" > "$@"

verify: target/entry.exit
	e=$$(cat $<)
	test "$${e}" = "0"

rmi: target/image.txt
	img=$$(cat $<)
	sudo docker rmi "$${img}"
	rm "$<"

target/make:
	mkdir -p target/make/classes
	javac -d target/make/classes src/it/simple/src/main/java/org/eolang/hone/App.java
