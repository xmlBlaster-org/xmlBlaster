# Automatically generated sample implementation code
# PLEASE EDIT     PLEASE EDIT      PLEASE EDIT.
# Generated on Sat Nov 20 23:40:13 1999 by idl2perl 2.1 with command:
# /usr/bin/idl2perl -o . -imp /home/ruff/xmlBlaster/src/idl/xmlBlaster.idl

use clientIdl::BlasterCallback_types;
use clientIdl::BlasterCallback_skel;

# interface clientIdl::BlasterCallback (IDL:org.xmlBlaster/clientIdl/BlasterCallback:1.0)

package clientIdl::BlasterCallback_impl;
use COPE::CORBA::Servant;
@clientIdl::BlasterCallback_impl::ISA=qw(CORBA::BOA::_Servant);
sub _skelname($) { 'clientIdl::BlasterCallback_skel' }

use COPE::CORBA::Exception;
use Experimental::Exception;

use vars '%ones';
# operation update (IDL:org.xmlBlaster/clientIdl/BlasterCallback/update:1.0)

sub new {
    my($class, $oid) = @_;

    # Rather than creating a new object, just create a
    # CORBA object reference (IOR)
    my $ior = $CORBA::BOA::_The_Boa->create_reference_with_id($oid, $class->_interface);

    # Put the object data in our "database"
    # Note that we don't save the IOR; we can recreate it any time from
    # the oid
    $ones{$oid} = 1;

    # and return the object reference
    return $ior;
}

sub GetId ($) {
    my($self) = @_;

    # Extract the object ID from the call
    my $oid = $CORBA::BOA::_The_Boa->servant_to_id($self);

    # If this was a real application, we could use $oid to look up
    # the object info in our database

    # Another client might have deleted the object from the database...
    if (!exists($ones{$oid})) {
	throw new CORBA::OBJECT_NOT_EXIST minor => 0, completed=> Corba::CompletionStatus::COMPLETED_NO;
    }

    return $oid;
}

sub update ($$$) {
    print "*********** INVOKED UPDATE ***********\n";
    my($self,$msgUnitArr) = @_;
    print "*********** INVOKED UPDATE ***********\n";
}

sub init() {
    my $default = bless {}, 'clientIdl::BlasterCallback_impl';
    $CORBA::BOA::_The_Boa->activate_object($default);
    $CORBA::BOA::_The_Boa->set_servant($default->_interface, $default);
}

1;
