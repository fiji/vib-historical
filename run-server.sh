#!/bin/sh

if ! [ `whoami` = 'www-data' ]
then
	echo The server must be run as the www-data user.
	exit -1
fi

if ! [ `( cd ../ImageJA && git-branch | egrep '^\*' | sed 's/\* //' )` = xvfb-server ]
then
	echo The respository ../ImageJA must be at xvfb-server
	exit -1
fi

set -e

MEM=1024m

xvfb-run java -Xmx$MEM -Dplugins.dir=. -jar ../ImageJA/ij.jar -eval 'run("Job Server","");'



