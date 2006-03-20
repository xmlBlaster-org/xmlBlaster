# ConnectQos.pm
#
# 11/02/02 11:53 mad@ktaland.com
#
#	connection data helper for perl xmlBlaster client
#

# =======================================
#
# 08/07/02 16:53 cyrille@ktaland.com
#
# REMARQUE :
#
# 08/07/02 16:02 cyrille@ktaland.com 
# some warning in xmlBlaster with connectQos due to:
# from src/java/org/xmlBlaster/protocol/xmlrpc/AuthenticateImpl.java
#         returnValueStripped = StringHelper.replaceAll(returnValue, "<![CDATA[", "");
# 118          returnValueStripped = StringHelper.replaceAll(returnValueStripped, "]]>", "");
# 119          if (!returnValueStripped.equals(returnValue)) {
# 120             log.trace(ME, "Stripped CDATA tags surrounding security credentials, XMLRPC does not like it (Helma does not escape ']]>'). " +
# 121                            "This shouldn't be a problem as long as your credentials doesn't contain '<'");
# 122          }
# 123  

# =======================================
#
# 08/07/02 16:53 cyrille@ktaland.com
#
#	XML returned by $srv->call( 'authenticate.connect', $loginQos->xml );
#
#<qos>
#   <securityService type="htpasswd" version="1.0">
#      <user>admin</user>
#      <passwd>secret</passwd>
#   </securityService>
#   <ptp>true</ptp>
#   <session timeout=\'86400000\' maxSessions=\'10\' clearSessions=\'false\' publicSessionId=\'15\'>
#      <sessionId>sessionId:213.186.34.8-null-1026139708095--176967386-16</sessionId>
#   </session>
#</qos>

package xmlBlaster::ConnectQos ;

use strict ;

#######################
# new
#
# 11/02/02 11:54 mad@ktaland.com
#
sub new {

	my $class = shift;
	my $connectdata = shift;
	###my $self = ($#_ == 0) ? { %{ (shift) } } : { @_ };
	my $self = {} ;
    bless( $self, $class );

	if( exists $connectdata->{'securityService.type'} ){
		$self->{'securityService.type'} = $connectdata->{'securityService.type'} ;
	}else{
		$self->{'securityService.type'} = 'htpasswd' ;
	}

	if( exists $connectdata->{'securityService.version'} ){
		$self->{'securityService.version'} = $connectdata->{'securityService.version'} ;
	}else{
		$self->{'securityService.version'} = '1.0' ;
	}

	$self->{'passwd'} = $connectdata->{'passwd'} ;
	$self->{'user'} = $connectdata->{'user'} ;

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
		$self->_fromXml( $xml );
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

	my $xml = "<qos>\n"
			."<securityService type=\"".$self->{'securityService.type'}
						."\" version=\"".$self->{'securityService.version'}
						."\">\n"
            ."   <user>".$self->{'user'}."</user>\n"
            ."   <passwd>".$self->{'passwd'}."</passwd>\n"
            ."</securityService>\n"
            #."<ptp>true</ptp>\n"
            #."<session timeout='3600000' maxSessions='6'>\n</session>\n"
            ."</qos>\n" ;

	return $xml ;

}#xml

###############
#
sub _fromXml {

	# TODO : full parsing to get all da parameters & associated values
	# actually, only sessionId is parsed

	my $self = shift ;
	my $xml = shift ;

# <qos>
#   <securityService
#     type="htpasswd"
#     version="1.0">
#    <user>admin</user>
#    <passwd>secret</passwd>
#   </securityService>
#   <session name='/node/http_129_194_17_16_3412/client/admin/-24'
#       timeout='86400000'
#       maxSessions='10'
#       clearSessions='false'
#       sessionId='sessionId:129.194.17.16-null-1053040177135-712235115-24'/>
#   <queue
#       relating='connection'
#       maxEntries='10000000'
#       maxEntriesCache='1000'>
#    <address
#        type='IOR'
#        bootstrapHostname='129.194.17.16'
#        bootstrapPort='3412'
#        dispatchPlugin='undef'>
#     http://129.194.17.16:3412
#    </address>
#   </queue>
#   <queue
#       relating='subject'/>
#   <queue
#       relating='callback'
#       maxEntries='1000'
#       maxEntriesCache='1000'/>
#  </qos>


	if( $xml =~ /\bsessionId=\'([^\']*)\'/ ){
		$self->{'sessionId'} = $1 ;
	}

	return 1 ;

}#_fromXml

1;
