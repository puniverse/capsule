#!/bin/sh

exec `java -Dcapsule.trampoline -jar $0` "$@"
