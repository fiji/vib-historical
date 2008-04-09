#!/bin/sh

set -e

MEM=1024m

xfvb-run java -Xmx$MEM -Dplugins.dir=. -jar ../ImageJ/ij.jar -eval 'run("Job Server","");'



