#!/bin/sh

exec bash -c "exec $(java -Dcapsule.trampoline -jar $0) $@"
