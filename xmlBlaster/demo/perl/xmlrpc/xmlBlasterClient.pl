#!/usr/local/bin/perl -w
#
#       xmlBlasterClient.pl
#
#       HOWTO RUN :
#               type on your keyboard :
#               $ ./xmlBlasterClient.pl your.host:port
#
#       Required libraries :
#
#               this demo use xml-rpc protocol to connect to xmlBlaster server.
#               the xml-rpc implementation used in this demo is Frontier.
#
#               - XML Expat
#               install expat (expat-1.95.2 at http://sourceforge.net/projects/expat/)
#               on my linux redhat 7.2 it was already installed.
#               - perl XML Parser :
#               perl module XML-Parser (XML-Parser.2.30 at http://search.cpan.org/search?dist=XML-Parser)
#               - perl XMLRPC :
#               xml-rpc implemeted by Frontier (Frontier-RPC-x.xx).
#               Look at CPAN for this package.
#
# 04/07/02 17:21 mad@ktaland.com
#       upgrade to fit new xmlBlaster implementation
#       done with BRANCH_0_7_9g on date 2002-07-08
#
#       - create package 'XmlBlaster' to encapsulate xml-rpc calls.
#       - create package 'EraseReturnQos'
#       - create package 'PublishReturnQos'
#
# 2002-02-11 16:27 mad@ktaland.com
#
#       - create package 'connectQos'
#       - create package 'messageUnit'
#
# 2001-12-17 11:54 mAd@ktaland.com
#
#       Connect to xmlBlaster via XMLRPC
#       IOR with MICO is to heavy to install ;o{
#

use Frontier::Client;
use Data::Dumper;

use strict;

use lib( '.' );

use xmlBlaster::Exception ;
use xmlBlaster::XmlBlaster ;
use xmlBlaster::MsgUnit ;
use xmlBlaster::EraseReturnQos ;

my $securityServiceType = 'htpasswd' ;

my @profiles = (
        {'user'=>'admin' ,
        'passwd'=>'secret' ,
        },
        {'user'=>'guest' ,
        'passwd'=>'secret' ,
        },
);

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

        print "> Construct client I (",$profiles[0]->{'user'},")...\n";
        my $xb1 = xmlBlaster::XmlBlaster->new(
                                        {
                                        'host'=> $server,
                                        'user'=> $profiles[0]->{'user'},
                                        'passwd'=> $profiles[0]->{'passwd'},
                                        'securityService.type'=> 'htpasswd',
                                        }
                                );

        print "> Connect client I ...\n";
        $xb1->connect();

        print "> Construct client II (",$profiles[1]->{'user'},")...\n";
        my $xb2 = xmlBlaster::XmlBlaster->new(
                                        {
                                        'host'=> $server,
                                        'user'=> $profiles[1]->{'user'},
                                        'passwd'=> $profiles[1]->{'passwd'},
                                        'securityService.type'=> 'htpasswd',
                                        }
                                );
        print "> Connect client II ...\n";
        $xb2->connect();

        #
        #
        #
        print "> Get server informations ...\n";
        getServerSysInfo( $xb1 );

        #
        #
        #
        print "> Testing Publish/Subscribe ...\n";
        testPubGet( $xb1, $xb2 );

        #
        #       Leave the place
        #

        print "> Disconnect client I ...\n";
        $xb1->logout();
        print "> Disconnect client II ...\n";
        $xb2->logout();

}
catch
{
    my $exception = shift ;
        print $exception->dump ;
        #print '='x40,"\n",Dumper( $exception ),"\n",'='x40,"\n";
};


##################################
sub testPubGet {

        my( $srv1, $srv2)=@_;

        my( $key, $content );
        my( $keyoid, $messages );

        #
        #       User 1 publish 2 messages
        #

        $key = "<key oid='myHello1' contentMime='text/plain' />" ;
        $content = 'my first HELLO!' ;
        print "> user [",$profiles[0]->{'user'},"] publish [",$content,"]...\n";

        $keyoid = $srv1->publish( $key, $content );

        $key = "<key oid='myHello2' contentMime='text/plain' />" ;
        $content = 'my HELLO number 2!' ;
        print "> user [",$profiles[0]->{'user'},"] publish [",$content,"]...\n";

        $keyoid = $srv1->publish( $key, $content );

        #
        #       User 2 get messages just posted by User 1
        #

        print "> user [",$profiles[1]->{'user'},"] get messages ...\n" ;

        $key = "<key oid='' queryType='XPATH'>/xmlBlaster/key[starts-with(\@oid,'myHel')]</key>" ;

        $messages = $srv2->get( $key );

        if( scalar(@$messages) <=0 ){
                print "No message found ! It's like a error ! Abort.\n";
                return undef ;
        }

        foreach my $message ( @$messages ){
                my $message_unit = xmlBlaster::MsgUnit->new( $message );
                print "Found message = [",$message_unit->keyOid()," / ", $message_unit->content(), "]\n";
        }

        #
        #       User 2 erase those messages
        #

        print "> user [",$profiles[1]->{'user'},"] erase messages ...\n";

        my $eraseRetQos_aref = $srv2->erase( $key );

        foreach( @$eraseRetQos_aref ){
                my $eraseRetQos = xmlBlaster::EraseReturnQos->new( $_ );
                print "Erased message = [", $eraseRetQos->keyOid() , "]\n";
        }

}#testPubGet

##################################
sub getServerSysInfo {

        my( $server )=@_;

        # Call the server and get its current memory consumption.

        # 08/07/02 15:39 cyrille@ktaland.com 
        # from demo/HelloWorld.java :
        # MsgUnit[] msgs = con.get("<key oid='__cmd:?freeMem'/>", null);
        #

        try {

                my $messages ;
                my $message_unit ;
        
                my @sysInternal = ('__cmd:?totalMem','__cmd:?usedMem','__cmd:?freeMem','__cmd:?clientList' );
        
                foreach my $keyoid ( @sysInternal ){
        
                        print "> get $keyoid... \n";
        
                        $messages = $server->get( "<key oid='$keyoid' />" );
                        foreach my $message ( @$messages ){

                                my $message_unit = xmlBlaster::MsgUnit->new( $message );

                                print " $keyoid = [", $message_unit->content(), "]\n";
                        }
                }

        }
        catch
        {
            my $exception = shift ;
                print $exception->dump ;
                #print '='x40,"\n",Dumper( $exception ),"\n",'='x40,"\n";
        };

}#getServerSysInfo

1;
