#!/usr/bin/perl -w

# Invoke: cbServer.pl http://myHost:8080
use Frontier::Daemon;
use Frontier::Client;
use xmlBlaster::Exception ;
use xmlBlaster::XmlBlaster ;
use xmlBlaster::MsgUnit ;
use xmlBlaster::EraseReturnQos ;


sub do_update {
        print "GOT AN UPDATE\n";
        return "<qos><state>OK</state></qos>";
}

sub do_ping {
        print "got ping\n";
        return "<qos><state>OK</state></qos>";
}

my $server_url=$ARGV[0];
my $local_url="http://127.0.0.1:9091/RPC2";

my $server = Frontier::Client->new(url => $server_url);
print "Connected to xmlBlaster server on $server_url \n";

# Call the remote server and get our result.
my $sessionId = $server->call('authenticate.login', "dk2", "dk2",
                "<qos><callback type='XMLRPC'>$local_url</callback>".
                "<local>false</local></qos>", "");
print "\nLogin success with sessionId=$sessionId \n";

$server->call('xmlBlaster.subscribe',
                        $sessionId,
                        "<key oid='' queryType='XPATH'>//service</key>",
                        "<qos><duplicateUpdates>false</duplicateUpdates></qos>");


print "\nListening for callbacks on $local_url\n";
print "\nTry 'perl testpub.pl http://myHost:8080' in another console, you should receive the update here\n";

Frontier::Daemon->new(
            LocalPort => 9091,
            methods => {
                        'update'   => \&do_update,
                        'ping'   => \&do_ping
            });


