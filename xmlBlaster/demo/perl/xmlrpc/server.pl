#!/usr/bin/perl -w

# Perl callback server example, xmlBlaster.org
# @author David Kelly davidk@navahonetworks.com
# @author Russell Chan russ@navahonetworks.com

use Frontier::Daemon;
use Frontier::Client;
use xmlBlaster::Exception ;
use xmlBlaster::XmlBlaster ;
use xmlBlaster::MsgUnit ;
use xmlBlaster::EraseRetQos ;


sub do_update {
        print "Received update ...\n";
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
my $sessionId = $server->call('authenticate.login', "dk2", "dk2",
                "<qos><callback type='XML-RPC'>$local_url</callback>".
                "<local>false</local></qos>", "");
print "\nLogin success with sessionId=$sessionId \n";

$server->call('xmlBlaster.subscribe',
                        $sessionId,
                         "<key oid='' queryType='XPATH'>//service</key>",
                        "<qos><duplicateUpdates>false</duplicateUpdates></qos>");

Frontier::Daemon->new(
            LocalPort => 9091,
            methods => {
                        'update'   => \&do_update,
                        'ping'   => \&do_ping
            });

