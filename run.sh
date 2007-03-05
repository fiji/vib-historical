#!/bin/sh

curdir="$(cd "$(dirname "$0")"; pwd)"
case `uname -o 2>/dev/null` in
Cygwin*)
	curdir=$(cygpath --mixed $curdir)
	CPSEP=\;
	;;
*)
	CPSEP=:
	;;
esac

case "$1" in
app)
	shift
	java -Xmx700m -Dplugins.dir="$curdir" $EXTRADEFS \
		-classpath "$curdir"/../ImageJ/ij.jar$CPSEP. vib.app.App "$@"
	;;
*)
	java -Xmx700m -Dj3d.noOffScreen=true -Dplugins.dir="$curdir" $EXTRADEFS \
		-jar "$curdir"/../ImageJ/ij.jar "$@"
	;;
esac

