#!/usr/local/bin/perl
#
#       messagesList.pl
#
#       HOWTO RUN :

#               type on your keyboard :
#               $ ./messagesList.pl your.host:port
#
# 04/07/02 17:21 mad@ktaland.com
#       upgrade to fit new xmlBlaster implementation
#       done with BRANCH_0_7_9g on date 2002-07-08
#
# 12/02/02 13:18 mad@ktaland.com
#
#       - create that cool tool after made demo xmlBlasterClient.pl
#
# 2001-12-17 11:54 mAd@ktaland.com
#
#       Connect to xmlBlaster via XMLRPC
#       IOR with MICO is to heavy to install ;o{
#
#       Lib required :
#
#       - XMLRPC implemeted by Frontier (Frontier-RPC-0.07b3)
#       - XML Parser :
#               install expat (expat-1.95.2 at http://sourceforge.net/projects/expat/)
#               then perl module XML-Parser (XML-Parser.2.30 at http://search.cpan.org/search?dist=XML-Parser)
#

use Frontier::Client;
use Data::Dumper;

use lib( '.' );

use xmlBlaster::Exception ;
use xmlBlaster::XmlBlaster ;
use xmlBlaster::MsgUnit ;

use strict;

# MAIN
try
{
        my $server =  $ARGV[0];
        if( ! defined($server) ){
                print "give me a server url like : MyHost:8080\n";
                exit ;
        }

        #
        #       Connecting 2 clients
        #

        print "> Construct client for server $server ...\n";
        my $xb = xmlBlaster::XmlBlaster->new(
                                        {
                                        'host'=> $server,
                                        'user'=> 'guest',
                                        'passwd'=> 'secret',
                                        }
                                );

        print "> Connect with $xb ...\n";
        $xb->connect();
        print "> Connected ...\n";

        #
        #       We're connected
        #       do some work ...
        #

        my $cpt = getMessageList ( $xb );

        print "> $cpt messages found.\n";

        # Logout from xmlBlaster

        print "> Disconnect ...\n";
        $xb->logout();

}
catch
{
    my $exception = shift ;
        $exception->dump ;
};

##################################
sub getMessageList {

        my( $srv )=shift ;

        #
        #       User try to get the list of messages from xmlBlaster.
        #

        print "> get list of all messages ...\n" ;

        #my $queryString = '//key' ;
        my $queryString = '/xmlBlaster/key' ;

        my $key = "<key oid='' queryType='XPATH'>\n".$queryString."</key>" ;

        my $messages_aref = $srv->get( $key );
        my $cpt = 0 ;

        foreach my $message ( @$messages_aref ){

                my $message_unit = xmlBlaster::MsgUnit->new( $message );
                print $message_unit->dump() ;
                $cpt ++ ;
        }

        return $cpt ;

}#getMessageList

1;
