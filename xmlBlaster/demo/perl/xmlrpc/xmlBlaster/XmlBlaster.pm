# XmlBlaster.pm
#
#       use XMLRPC (Frontier) for connecting to xmlBlaster
#
# 04/07/02 17:17 mad@ktaland.com
#       upgrade
# 14/02/02 08:32 mad@ktaland.com
#
package xmlBlaster::XmlBlaster ;

use Data::Dumper;

use Frontier::Client;

use xmlBlaster::Exception ;
use xmlBlaster::ConnectQos ;
use xmlBlaster::MsgUnit ;
use xmlBlaster::PublishReturnQos ;

use strict;

##############
sub new
{

        my $class = shift;
        # init from hash
        my $self = ($#_ == 0) ? { %{ (shift) } } : { @_ };
        bless( $self, $class );

        $self->{'user'} = 'test' if( ! exists $self->{'user'} );
        $self->{'passwd'} = 'secret' if( ! exists $self->{'passwd'} );
        $self->{'host'} = 'localhost:8080' if( ! exists $self->{'host'} );

        $self->{'server'} = undef ;
        $self->{'sessionId'} = undef ;

        return $self ;

}#new

################
sub _getServer {

        my $self = shift ;

        if( ! defined($self->{'server'}) ){
                $self->{'server'} = Frontier::Client->new( url => 'http://'.$self->{'host'}.'/' );
        }
        return $self->{'server'} ;

}#_getServer

################
sub connect
{
        my $self = shift ;

        my $srv = $self->_getServer();

        my $loginQos = xmlBlaster::ConnectQos->new(
                        {
                        'user' => $self->{'user'} ,
                        'passwd'=>$self->{'passwd'} ,
                        }
                );

        #print '='x40,"\nConnectQos XML :\n",Dumper( $loginQos->xml ),"\n",'='x40,"\n" ;

        my $xml = $srv->call( 'authenticate.connect', $loginQos->xml );

        #print '='x40,"\nConnectReturnQos XML :\n",Dumper( $xml ),"\n",'='x40,"\n" ;

        $self->{'sessionId'} = $loginQos->sessionId( $xml ) ;

        #print '='x40,"\nConnect loginQos :\n",Dumper( $loginQos ),"\n",'='x40,"\n" ;

        return $self->{'sessionId'} ?1 :0 ;

}#connect

##############
sub logout
{
        my $self = shift ;

        # Logout from xmlBlaster

        $self->{'server'}->call( 'authenticate.logout', $self->{'sessionId'} );

        return 1 ;

}#logout

############
#       parameters :
#               - $key : must be defined
#               - $content : optional
#               - $qos : optional
sub publish
{
        my( $self, $key, $content, $qos )=@_;

        my $srv = $self->_getServer();

        # a default empty key.
        $key = '' if( ! $key ) ;

        # a default Qos.
        $qos = '<qos></qos>' if( ! $qos || $qos eq '' ) ;

        # a default empty content.
        $content = '' if( ! $content || $content eq '' ) ;

        # 08/07/02 16:30 cyrille@ktaland.com 
        # no publish return a string like :
        #
        #       <qos><state id=\'OK\'/><key oid=\'http://213.186.34.8:40000-1026138362565-3\'/></qos>
        #

        my $xml = $self->{'server'}->call( 'xmlBlaster.publish', $self->{'sessionId'}, $key,$content,$qos );

        #print '='x40,"\n",Dumper( $xml ),"\n",'='x40,"\n" ;

        my $publishretqos = xmlBlaster::PublishReturnQos->new( $xml );

        #print '='x40,"\n",Dumper( $publishretqos ),"\n",'='x40,"\n" ;

        return $publishretqos->keyOid() ;

}#publish

############
#       parameters :
#               - $key : must be defined
#               - $qos : optional
sub get
{
        my( $self, $key, $qos )=@_;

        if( ! $key ){
                throw( new xmlBlaster::Exception( code => 'XMLBLASTER_ERROR',
                                                        info => [ __FILE__ . ' at Line ' . __LINE__,
                                                                        'Need a valid key for querying message !'
                                                                         ]
                                                                        ) );
        }

        # a default Qos
        $qos = '<qos></qos>' if( ! $qos || $qos eq '' ) ;

        my $messages = $self->{'server'}->call( 'xmlBlaster.get', $self->{'sessionId'}, $key ,$qos );

        return $messages ;

}#get

############
#       parameters :
#               - $key : must be defined
#               - $qos : optional
sub erase
{
        my( $self, $key, $qos )=@_;

        if( ! $key ){
                throw( new xmlBlaster::Exception( code => 'XMLBLASTER_ERROR',
                                                        info => [ __FILE__ . ' at Line ' . __LINE__,
                                                                        'Need a valid key for erasing message !'
                                                                         ]
                                                                        ) );
        }

        # a default Qos
        $qos = '<qos></qos>' if( ! $qos || $qos eq '' ) ;

        my $some_eraseRetQos = $self->{'server'}->call( 'xmlBlaster.erase', $self->{'sessionId'}, $key ,$qos );

        return $some_eraseRetQos ;

}#erase

##################
#
sub DESTROY
{
        my( $self ) = @_;

    return 1;

}#DESTROY

1;
