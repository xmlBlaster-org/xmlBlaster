#!/usr/local/bin/perl
#
#	messagesList.pl
#
#	HOWTO RUN :
#		after done 'Required configuration' if needed,
#		type on your keyboard :
#		$ ./messagesList.pl http://your.host:port/
#
#	Required configuration :
#		- change variable $securityServiceType to fit your server configuration.
#		it's the name of authentification plugin you want to use.
#		- change variable @profiles to fit your server configuration.
#		this tool need 1 valid user for login.
#
#	Required files :
#		- messageUnit.pm :  xmlBlaster connect qos
#		- connectQos.pm : xmlBlaster connect qos
#		- Exception.pm : my exception management
#
# 12/02/02 13:18 mad@ktaland.com
#
#	- create that cool tool after made demo xmlBlasterClient.pl
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
);

# MAIN
try
{
	#$server_url = 'http://MyHost:8080/';

	my $server_url =  @ARGV[0] ;
	print "Trying to connect to xmlBlaster server on $server_url ...\n";

	# Make an object to represent the XML-RPC server.

	my $server = Frontier::Client->new(url => $server_url);
	print "OK.\n";

	# Login

	print "> Login as ", $profiles[0]->{'user'} ,"...\n" ;
	my $xml ;
	my $loginQos = connectQos->new( 'securityService.type'=> $securityServiceType ,
									'user'=>$profiles[0]->{'user'}, 'passwd'=>$profiles[0]->{'passwd'} );
	$xml = $server->call( 'authenticate.connect', $loginQos->xml );
	my $sessionId = $loginQos->sessionId( $xml ) ;
	print "OK. Login as ",$profiles[0]->{'user'}," success with sessionId=$sessionId \n" ;

	#
	#	We're connected
	#	do some work ...
	#

	getMessageList ( $server, $sessionId );

	# Logout from xmlBlaster
	print "> Logout user ",$profiles[0]->{'user'}," ...\n" ;
	$server->call('authenticate.logout', $sessionId );
	print "OK.\n" ;

}
catch
{
    my $exception = shift ;
	$exception->dump ;
};

##################################
sub getMessageList {

	my( $server, $sessionId )=@_;

	my( $key, $qos, $content );

	my( $keyoid, $messages );

	$key = "<key oid='' contentMime='text/plain'>\n</key>" ;
	$qos = "<qos></qos>" ;

	#
	#	User try to get the list of messages from xmlBlaster.
	#

	print "> user [", $profiles[0]->{'user'} ,"] try get list of messages ...\n" ;

	#$key = "<key oid='$keyoid' contentMime='text/plain'>\n</key>" ;
	my $queryString = '//key' ;
	$key = "<key oid='' queryType='XPATH'>\n".$queryString."</key>" ;

	$messages = $server->call( 'xmlBlaster.get', $sessionId, $key ,$qos );

	foreach my $message ( @$messages ){
		my $message_unit = messageUnit->new( $message );
		#print "message = [", $message_unit->content(), "]\n";
		print $message_unit->dump() ;
	}

}#getMessageList

1;
