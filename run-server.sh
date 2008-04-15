#!/bin/sh

set -e

MEM=1024m

xvfb-run java -Xmx$MEM -Dplugins.dir=. -jar ../ImageJ/ij.jar -eval 'run("Job Server","");'



