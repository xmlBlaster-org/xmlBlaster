# MsgUnit.pm
#
# 04/07/02 17:17 mad@ktaland.com
#       upgrade
# 11/02/02 15:04 mad@ktaland.com
#
package xmlBlaster::MsgUnit ;

use Data::Dumper ;      # for sub dump()
use MIME::Base64;       # to decode xml-rpc data type : Frontier::RPC2::Base64

use strict ;

#######################
# new
#
# 11/02/02 11:54 mad@ktaland.com
#
sub new {

        my $class = shift;
        # init from hash
        #my $self = ($#_ == 0) ? { %{ (shift) } } : { @_ };
        # init from ref
        my $data = shift ;
        my $self = {} ;
        bless( $self, $class );

        $self->{'key_xml'} = '' ;
        $self->{'qos_xml'} = '' ;
        $self->{'content'} = '' ;
        $self->{'frontier_content'} = undef ;
        $self->{'frontier_data'} = $data ;

        ###print STDERR '='x40,"\n", Dumper($data) , '='x40, "\n";

        if( defined $data && ref($data) eq 'ARRAY' ){

                #
                # $data should be a Frontier ( Frontier-RPC ) returned message.
                #

                my $aref = $data ;
                $self->{'key_xml'} = $aref->[0] if( ref($aref->[0]) eq '' );
                my $frontier_content = $aref->[1] ;
                $self->{'qos_xml'} = $aref->[2] if( ref($aref->[2]) eq '' );

                #
                # Frontier Type ( Frontier-RPC-0.07b3 )
                #       Frontier::RPC2::Boolean
                #       Frontier::RPC2::DateTime::ISO8601
                #       Frontier::RPC2::Base64
                #

                if( ref( $frontier_content ) eq 'Frontier::RPC2::Base64' ){

                        my $base64encoded = $frontier_content->value() ;
                        $self->{'content'} = decode_base64( $base64encoded );
                        $self->{'frontier_content'} = $frontier_content ;

                }elsif( ref( $frontier_content ) eq 'Frontier::RPC2::DateTime::ISO8601' ){

                        # never tested !!!
                        # TODO : is the right way ???!!!

                        $self->{'content'} = $frontier_content->value() ;
                        $self->{'frontier_content'} = $frontier_content ;

                }elsif( ref( $frontier_content ) eq 'Frontier::RPC2::Boolean' ){

                        # never tested !!!
                        # TODO : is the right way ???!!!

                        $self->{'content'} = $frontier_content->value() ;
                        $self->{'frontier_content'} = $frontier_content ;

                }else{
                        print STDERR "ERROR: Unknow that Frontier Type ", ref($frontier_content),"\n";
                }

        }

        return $self ;

}#new

###################

sub keyOid {
        my $self = shift ;

        my $keyoid = $self->{'key_xml'} ;
        $keyoid =~ /<key (.*|[ ])oid=['"](.*?)['"]/mo ;
        $keyoid = $2 ;

        return $keyoid ;
}
sub xmlKey {
        my $self = shift ;
        return $self->{'key_xml'} ;
}
sub xmlQos {
        my $self = shift ;
        return $self->{'qos_xml'} ;
}
sub content {
        my $self = shift ;
        return $self->{'content'} ;
}

#############

sub dump {
        my( $self, $debug )=@_;

        my $str = '' ;
        $str .= ('='x40)."\n" ;
        $str .= "MsgUnit::dump :\n" ;
        if( defined $debug ){
                $str .= "Frontier data : [ ".Dumper( $self->{'frontier_data'} ). " ]\n";
        }
        $str .= "KeyOid : [". $self->keyOid ."]\n";
        $str .= "Qos : [". $self->xmlQos ."]\n";
        $str .= "Content : [". $self->content ."]\n";
        $str .= ('='x40)."\n" ;
        return $str ;

}#dump

1;
