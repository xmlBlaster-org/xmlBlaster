#!/usr/bin/perl -w

# Perl callback server example, xmlBlaster.org
# @author David Kelly <davidk@navahonetworks.com>
# @author Russell Chan <russ@navahonetworks.com>
# @author Jason Martin <jhmartin@toger.us>
use strict;

use MIME::Base64;
use Frontier::Daemon;
use Frontier::Client;
use xmlBlaster::Exception ;
use xmlBlaster::XmlBlaster ;
use xmlBlaster::MsgUnit ;
use xmlBlaster::EraseReturnQos ;


sub do_update {
        print "***\nReceived update ...\n";
        print "Header:" . $_[1] . "\n";
        print "Message:" .decode_base64($_[2]->value) . "\n";
        print "QoS:". $_[3] . "\n***\n";

# Acknowledge receipt of the update
        return "<qos><state>OK</state></qos>";
}

sub do_ping {
        print "Received ping ...\n";
        return "<qos><state>OK</state></qos>";
}

my $local_url="http://127.0.0.1:9091/RPC2";
my $server_url=$ARGV[0];

my $server = Frontier::Client->new(url => $server_url);
print "Connected to xmlBlaster server on $server_url \n";

# Call the remote server and get our result.
# Retries and delay set to cover a race between subscribing and
# xmlBlaster attempting to communicate with the xmlrpc server created below.
my $sessionId = $server->call('authenticate.login', "dk2", "dk2",
                "<qos><callback type='XMLRPC' retries='2' delay='2000'>$local_url</callback>".
                "<local>false</local></qos>", "");
print "\nLogin success with sessionId=$sessionId \n";

$server->call('xmlBlaster.subscribe',
                        $sessionId,
                         "<key oid='' queryType='XPATH'>//service</key>",
                        "<qos><duplicateUpdates>false</duplicateUpdates></qos>");

# ReuseAddr is an option to the IO::Socket class of which Frontier::Daemon is a
# subclass.  It prevents an 'Address already in use' error that occurs when this
# script is interrupted and restarted quickly.
my $result = Frontier::Daemon->new(
	    ReuseAddr => 1,
            LocalPort => 9091,
            methods => {
                        'update'   => \&do_update,
                        'ping'   => \&do_ping
            });

die "Unable to spawn daemon: $!" unless $result;
