#!/usr/local/bin/perl
#
#	xmlBlasterClient.pl
#
#	HOWTO RUN :
#		after done 'Required configuration' if needed,
#		type on your keyboard :
#		$ ./xmlBlasterClient.pl http://your.host:port/
#
#	Required configuration :
#		- change variable $securityServiceType to fit your server configuration.
#		it's the name of authentification plugin you want to use.
#		- change variable @profiles to fit your server configuration.
#		this test application need 2 valid users for login.
#
#	Required files :
#		- messageUnit.pm :  xmlBlaster connect qos
#		- connectQos.pm : xmlBlaster connect qos
#		- Exception.pm : my exception management
#
# 2002-02-11 16:27 mad@ktaland.com
#
#	- create package 'connectQos'
#	- create package 'messageUnit'
#
# 2001-12-17 11:54 mAd@ktaland.com
#
#	Connect to xmlBlaster via XML-RPC
#	IOR with MICO is to heavy to install ;o{
#
#	Lib required :
#
#	- XML-RPC implemeted by Frontier (Frontier-RPC-0.07b3)
#	- XML Parser :
#		install expat (expat-1.95.2 at http://sourceforge.net/projects/expat/)
#		then perl module XML-Parser (XML-Parser.2.30 at http://search.cpan.org/search?dist=XML-Parser)
#
#

use Frontier::Client;
use Data::Dumper;

use lib( '.' );
use Exception;

use strict;

use connectQos ;
use messageUnit ;

my $securityServiceType = 'simple' ;

my @profiles = (
	{'user'=>'mad' ,
	'passwd'=>'secret' ,
	},
	{'user'=>'yann' ,
	'passwd'=>'secret' ,
	},
);

# MAIN
try
{
	#$server_url = 'http://MyHost:8080/';

	my $server_url =  @ARGV[0];
	print "Trying to connect to xmlBlaster server on $server_url ...\n";

	# Make an object to represent the XML-RPC server.

	my $server = Frontier::Client->new(url => $server_url);
	print "OK.\n";

	# Login

	print "> Login as ",$profiles[0]->{'user'},"...\n" ;
	my $xml ;
	my $loginQos = connectQos->new( 'securityService.type'=> $securityServiceType ,
									'user'=>$profiles[0]->{'user'}, 'passwd'=>$profiles[0]->{'passwd'} );
	$xml = $server->call( 'authenticate.connect', $loginQos->xml );
	my $sessionId = $loginQos->sessionId( $xml ) ;
	print "OK. Login as ",$profiles[0]->{'user'}," success with sessionId=$sessionId \n" ;

	#
	# just create another connection,
	#	to get 2 users at one time.
	#

	print "Create a second xmlBlaster connexion on $server_url ...\n";
	my $server2 = Frontier::Client->new(url => $server_url);
	print "OK.\n";

	print "> Login as ",$profiles[1]->{'user'}," ...\n" ;
	my $loginQos2 = connectQos->new( 'securityService.type'=> $securityServiceType ,
									'user'=>$profiles[1]->{'user'}, 'passwd'=>$profiles[1]->{'passwd'} );
	my $xml2 = $server->call( 'authenticate.connect', $loginQos2->xml );
	my $sessionId2 = $loginQos->sessionId( $xml2 ) ;
	print "OK. Login as ",$profiles[1]->{'user'}," success with sessionId=$sessionId \n" ;

	#
	#	We're connected
	#	do some work ...
	#

	getServerSysInfo ( $server, $sessionId );

	testPubSub ( $server, $sessionId, $server2, $sessionId2 );

	# Logout from xmlBlaster
	print "> Logout user ",$profiles[0]->{'user'}," ...\n" ;
	$server->call('authenticate.logout', $sessionId );
	print "OK.\n";
	print "> Logout user ",$profiles[1]->{'user'}," ...\n" ;
	$server2->call('authenticate.logout', $sessionId2 );
	print "OK.\n";

}
catch
{
    my $execption = shift ;
	$execption->dump ;
};

##################################
sub testPubSub {
	my( $server1, $sessionId1, $server2, $sessionId2 )=@_;

	my( $key, $qos, $content );
	my( $keyoid, $messages );

	$content = 'HELLO !' ;
	$key = "<key oid='' contentMime='text/plain'>\n</key>" ;
	$qos = "<qos></qos>" ;

	#
	#	User 1 publish a message
	#

	print "> user [",$profiles[0]->{'user'},"] try publish message [",$content,"]...\n";

	$keyoid = $server1->call( 'xmlBlaster.publish', $sessionId1, $key,$content,$qos );
	print "Ok. publish keyoid = ",$keyoid,"\n";

	#
	#	User 2 get the message just posted by User 1
	#

	print "> user [",$profiles[1]->{'user'},"] try get message ...\n";

	$key = "<key oid='$keyoid' contentMime='text/plain'>\n</key>" ;
	$messages = $server2->call( 'xmlBlaster.get', $sessionId2, $key ,$qos );
	foreach my $message ( @$messages ){
		my $message_unit = messageUnit->new( $message );
		print "Ok. message = [", $message_unit->content(), "]\n";
	}

	#
	#	User 2 erase that message
	#

	print "> user [",$profiles[1]->{'user'},"] try erase message ...\n";

	my $keyoid_aref = $server2->call( 'xmlBlaster.erase', $sessionId2, $key ,$qos );
	foreach( @$keyoid_aref ){
		if( $_ eq $keyoid ){
			print "Ok. erased message = [", $_, "]\n";
		}else{
			print "ERROR: erased unknow message [$_]\n";
		}
	}

}#testPubSub

##################################
sub getServerSysInfo {
	my( $server, $sessionId )=@_;

	# Call the server and get its current memory consumption.

	my $messages ;
	my $message_unit ;

	my @sysInternal = ('__cmd:?totalMem','__cmd:?freeMem','__cmd:?usedMem','__sys__UserList' );
	foreach my $keyoid ( @sysInternal ){
		print "> get $keyoid...";
		$messages = $server->call('xmlBlaster.get', $sessionId, "<key oid='$keyoid'></key>", "<qos></qos>");
		foreach my $message ( @$messages ){
			my $message_unit = messageUnit->new( $message );
			print " $keyoid = [", $message_unit->content(), "]\n";
		}

	}

}#getServerSysInfo

1;
