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

export

all: verify rmi

quick: target/make/classes/Hello.class
	export TARGET=$(realpath target/make)
	cp ./src/main/resources/org/eolang/hone/docker/in-docker-pom.xml "$${TARGET}/pom.xml"
	cp ./src/main/resources/org/eolang/hone/docker/entry.sh "$${TARGET}"
	"$${TARGET}/entry.sh"

target/image.txt: target/make/classes/Hello.class src/main/resources/org/eolang/hone/docker/Dockerfile src/main/resources/org/eolang/hone/docker/entry.sh
	sudo docker build -t hone-maven-plugin "$$(pwd)/src/main/resources/org/eolang/hone/docker"
	sudo docker build -t hone-maven-plugin -q "$$(pwd)/src/main/resources/org/eolang/hone/docker" > "$@"

target/entry.exit: target/image.txt target/make/classes/Hello.class
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

target/make/classes/Hello.class: Makefile
	mkdir -p target/make/classes
	mkdir -p target/make/src
	echo "class Hello { double foo() { return Math.sin(42); } }" > target/make/src/Hello.java
	javac -d target/make/classes target/make/src/Hello.java

clean:
	rm -rf target
