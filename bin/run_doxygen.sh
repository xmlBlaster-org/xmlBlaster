#!/bin/sh
#
# File:    xmlBlaster/bin/run_doxygen.sh
# Comment: Create Java, C and C++ doxygen API
# Input:   All Java, C and C++ files
# Output:  Doxygen html corrected with tidy
# Todo:    Do these tasks with ant, change to jtidy
#

echo "#######################"
echo "begin of $0"
echo "#######################"

export DOXY=doxygen
export TIDY=tidy

cd ${XMLBLASTER_HOME}/config
pwd
date
time ${DOXY} doxyfile


cd ${XMLBLASTER_HOME}/src/c/doc
pwd
date
time ${DOXY} Doxyfile


cd ${XMLBLASTER_HOME}/src/c++/doc
pwd
date
time ${DOXY} Doxyfile

cd ${XMLBLASTER_HOME}/doc/doxygen

for i in `find . -name *.html` ; do
  tidy --drop-empty-paras no --show-body-only no --fix-uri no --alt-text "image" --output-xhtml yes --indent yes --numeric-entities true --doctype omit --char-encoding utf8 --input-encoding latin1 $i > tmp00.dat
  mv tmp00.dat $i
done



echo "#######################"
echo "end of $0"
echo "#######################"
echo ""
