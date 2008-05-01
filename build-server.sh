#!/bin/sh

set -e

( find . -iname '*.class' -print0 | xargs -0 rm ) || true

sed -e "s/\\/\\* <double-only> \\*\\//\\/* <double-only>/" \
    -e "s/\\/\\* <\\/double-only> \\*\\//<\\/double-only> \\*\\//" \
    -e "s/\\/\\* <float-only>/\\/\\* <not-in-original-version> \\*\\//" \
    -e "s/<\\/float-only> \\*\\//\\/\\* <\\/not-in-original-version> \\*\\//" \
    -e "s/double/float/g" \
    -e "s/FastMatrix/FloatMatrix/g" \
    -e "s/JacobiDouble/JacobiFloat/g" \
    -e "s/[0-9][0-9]*\.[0-9][0-9]*/&f/g" \
    -e "s/toArray/toArrayFloat/g" \
    < vib/FastMatrix.java > vib/FloatMatrix.java

sed -e "s/double/float/g" \
    -e "s/FastMatrixN/FloatMatrixN/g" \
    -e "s/[0-9][0-9]*\.[0-9][0-9]*/&f/g" \
    < math3d/FastMatrixN.java > math3d/FloatMatrixN.java

javac -cp ../ImageJA/ij.jar:Jama-1.0.2.jar:Quick3dApplet-1.0.8.jar:jzlib-1.0.7.jar:junit-4.4.jar $(find . -name '*.java' | egrep -v _darcs)

