# EraseRetQos.pm
#
# 08/07/02 cyrille@ktaland.com 
#

package xmlBlaster::EraseRetQos ;

use strict ;

#######################
# new
#
# 08/07/02 cyrille@ktaland.com 
#
sub new {

	my $class = shift;
	my $xml = shift;

	my $self = {} ;
    bless( $self, $class );

	$self->{ 'xml' } = $xml ;
	$self->{ 'keyOid' } = undef ;

	return $self ;

}#new

sub keyOid {

	my $self = shift ;

	if( ! $self->{ 'keyOid' } ){

		if( defined $self->{ 'xml' } ){
			$self->{'xml'} =~ /<key (.*|[ ])oid=['"](.*?)['"]/mo ;
			$self->{'keyOid'} = $2 ;
		}

	}

	return $self->{ 'keyOid' };

}#keyOid

1;
