#
# connectQos.pm
#
# 11/02/02 11:53 mad@ktaland.com
#
#	connection data helper for perl xmlBlaster client
#
# xmlBlaster java.org.xmlBlaster.util.ConnectQos.java :
#*     &lt;qos>
# *        &lt;securityService type="simple" version="1.0">
# *          &lt;![CDATA[
# *          &lt;user>michele&lt;/user>
# *          &lt;passwd>secret&lt;/passwd>
# *          ]]>
# *        &lt;/securityService>
# *        &lt;session timeout='3600000' maxSessions='20'>
# *        &lt;/session>
# *        &lt;ptp>true&lt;/ptp>
# *        &lt;callback type='IOR'>
# *           IOR:10000010033200000099000010....
# *           &lt;burstMode collectTime='400' />
# *        &lt;/callback>
# *     &lt;/qos>
#
package connectQos ;

use strict ;

#######################
# new
#
# 11/02/02 11:54 mad@ktaland.com
#
sub new {
    my $class = shift;
    my $self = ($#_ == 0) ? { %{ (shift) } } : { @_ };
    bless( $self, $class );

	$self->{'securityService.type'} = 'simple' if !exists $self->{'securityService.type'} ;
	$self->{'securityService.version'} = '1.0' if !exists $self->{'securityService.version'} ;

	$self->{'passwd'} = '' if !exists $self->{'passwd'} ;
	$self->{'user'} = '' if !exists $self->{'user'} ;
	$self->{'sessionId'} = '' if !exists $self->{'sessionId'} ;

	return $self ;

}#new

####################
# sessionId
#
# 11/02/02 12:28 mad@ktaland.com
#
sub sessionId {
	my $self = shift ;
	my $xml = shift ;

	if( $xml ){
		# update connectQos data
		$self->fromXml( $xml );
	}

	return $self->{'sessionId'} ;

}#sessionId

####################
# toXml
#
# 11/02/02 11:59 mad@ktaland.com
#
sub xml {
	my $self = shift ;

	my $loginQos = "<qos>\n"
			."<securityService type=\"".$self->{'securityService.type'}
						."\" version=\"".$self->{'securityService.version'}."\">\n"
            ."   <![CDATA[\n"
            ."   <user>".$self->{'user'}."</user>\n"
            ."   <passwd>".$self->{'passwd'}."</passwd>\n"
            ."   ]]>\n"
            ."</securityService>\n"
            #."<ptp>true</ptp>\n"
            #."<session timeout='3600000' maxSessions='6'>\n</session>\n"
            ."</qos>\n" ;

	return $loginQos ;

}#xml

###############
sub fromXml {

	# TODO : full parsing to get all da parameters & associated values
	# actually, only sessionId is parsed

	my $self = shift ;
	my $xml = shift ;

	#
	#	<qos>
	#	<securityService type="htpasswd" version="1.0">
	#		<user>afdas</user>
	#		<passwd>xb12afdas</passwd>
	#	</securityService>
	#	<ptp>true</ptp>
	#	<session timeout='3600000' maxSessions='6'>
	#		<sessionId>195.246.158.42-null-1013428006016--1307479829-22</sessionId>
	#	</session>
	#	</qos>
	#

	if( $xml =~ /<sessionId>(.*)<\/sessionId>/ ){
		$self->{'sessionId'} = $1 ;
	}

	return 1 ;

}#fromXml

1;
