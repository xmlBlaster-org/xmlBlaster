#! /bin/sh
# \
exec tclmicosh "$0" ${1+"$@"}

#
# Load MICO module, if not already available
#

if [catch {package require tclmico}] {
    load libtclmico[info sharedlibextension]
}

source xmlBlaster.tcl

#
# Initialize MICO
#

eval corba::init $argv

#
# Feed the interface repository with information about the AuthServer
# interface
#

mico::ir add $_ir_xmlBlaster

#
# Connect to the server
#

set authServer [mico::bind IDL:org.xmlBlaster/authenticateIdl/AuthServer:1.0]

#
# login()
#

set xmlBlaster [$authServer login "Tcl-Gesa" "secret" "" "QOS"]
