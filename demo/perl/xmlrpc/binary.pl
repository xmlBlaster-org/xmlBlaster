#!/usr/local/bin/perl -w
#
#       binary.pl
#
#       HOWTO RUN :
#               type on your keyboard :
#               $ ./binary.pl your.host:port
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

use Frontier::Client;
use Data::Dumper;
use MIME::Base64;

use strict;

use lib( '.' );

use xmlBlaster::Exception ;
use xmlBlaster::XmlBlaster ;
use xmlBlaster::MsgUnit ;
use xmlBlaster::EraseReturnQos ;

my $coder = Frontier::RPC2->new;


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
        #       User 1 publish message
        #

        $key = "<key oid='myHello1' contentMime='text/plain' />" ;
        open(DAT, "test.bin") || die("Could not open file test.bin!");
        #@content=<DAT>;
        $content=$coder->base64(encode_base64(<DAT>));
        print "> user [",$profiles[0]->{'user'},"] publish [",$content,"]...\n";

        $keyoid = $srv1->publish( $key, $content );

        #
        #       User 2 get message just posted by User 1
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
                open(DAT,">return.bin") || die("Cannot Open File return.bin");
                print DAT $message_unit->content();
                close(DAT);
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


1;
