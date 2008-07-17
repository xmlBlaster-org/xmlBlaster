<?php


/**
 *      xmlBlaster.inc
 *
 *      Client Functions for xmlBlaster Server - www.xmlBlaster.org
 *      Contains functions for accessing the server via XMLRPC
 *
 *      TODO :
 *      - replace durty (but so fast ;o) regular expression by a real xml parsing !
 *
 *      08/07/02 21:36 cyrille@ktaland.com
*       Fixed dirty reg exp for new sessionId layout. PB
 *      28/02/03 10:58 bencom@bencom.co.nz
 */

// php xml-rpc implemetation by Edd Dumbill (C) 1999-2001 <edd@usefulinc.com>
include ("xmlrpc.inc");

// =================
// 08/07/02 21:36 cyrille@ktaland.com
//
class xmlBlaster {

	var $server;
	var $port;
	var $user;
	var $password;

	var $securityService_type = "htpasswd";
	var $securityService_version = "1.0";

	var $client;
	var $connectQos;
	var $isConnected;
	
	// =================
	function xmlBlaster($server, $port, $user, $password) {

		dbgprint("xmlBlaster::xmlBlaster()");

		$this->server = $server;
		$this->port = ($port * 1);
		$this->user = $user;
		$this->password = $password;

		dbgprint("xmlBlaster::xmlBlaster() $this->server::$this->port $this->user:$this->password");

		$this->connectQos = null;
		$this->isConnected = false;

	} //xmlBlaster

	// =================
	function isConnected() {

		return $this->isConnected;

	} //isConnected

	// =================
	function sessionId() {

		//dbgprint("xmlBlaster::sessionId()");

		if (isset ($this->connectQos)) {
			dbgprint("xmlBlaster::sessionId() sessionId = " . $this->connectQos->sessionId());
			return $this->connectQos->sessionId();
		}
		return null;

	} //sessionId

	// =================
	function connect() {

		dbgprint("xmlBlaster::connect()");

		if (!isset ($this->server)) {
			return array (
				'ERROR',
				"To connect, you have to fill fields &lt;server&gt; &lt;port&gt; &lt;user&gt; &lt;password&gt;"
			);
		}

		$connect_qos_xml = "<qos>\n" .
		"<securityService type=\"" . $this->securityService_type . "\" version=\"" . $this->securityService_version . "\">\n" .
		"   <user>" . $this->user . "</user>\n" .
		"   <passwd>" . $this->password . "</passwd>\n" .
		"</securityService>\n" .
		"</qos>\n";

		$this->client = new xmlrpc_client('/', $this->server, $this->port);

		/*if( ! isset( $this->client ) || $this->client==null ){
		        return array( 'ERROR', "Failed to connect, check you &lt;server&gt; and &lt;port&gt; parameters." );
		}*/

		$rpcmsg = new xmlrpcmsg("authenticate.connect", array (
			new xmlrpcval($connect_qos_xml, "string")
		));
		$result = $this->client->send($rpcmsg); // class xmlrpcresp, see http://phpxmlrpc.sourceforge.net/doc-2/

		//if( ! isset( $result ) ){
		//        return array( 'ERROR', "Failed to connect, check you &lt;server&gt; and &lt;port&gt; parameters." );
		//}

		if ($result->faultCode()) {
			// a xml-rpc error occured
			//$res = xmlrpc_decode($result->faultString());
			//dbgprint("ERROR xmlBlaster::connect() xml-rpc ERROR = $res");
			dbgprint("ERROR xmlBlaster::connect() xml-rpc ERROR = " . ($result->faultString()));
			//dbgprint("ERROR xmlBlaster::connect() xml-rpc ERROR = " . ($result->serialize()));
			return array (
				'ERROR',
				$result->faultString() // e.g. XmlBlasterException from server
			);
		}

		// $result contains the xmlrpc markup response
		// $res contains the ConnectReturnQos xml: "<qos>...</qos>"
		$res = php_xmlrpc_decode($result->value());
		$res_type = gettype($res);

		if (gettype($res) == 'array' && isset ($res['faultString'])) {
			// a xmlBlaster error occured
			dbgprint("ERROR xmlBlaster::connect() xmlBlaster ERROR = [" . $res['faultString'] . "]");
			return array (
				'ERROR',
				$res['faultString']
			);
		}

		//dbgprint("OK xmlBlaster::connect() response= [$res]");

		$this->connectQos = new ConnectQos($res);
		$this->isConnected = true;

		return array (
			'OK',
			$this->connectQos
		);

	} //connect

	// =================
	function logout() {

		dbgprint("xmlBlaster::logout()");

		if (!isset ($this->client)) {
			return;
		}

		// $self->{'server'}->call( 'authenticate.logout', $self->{'sessionId'} );      
		$rpcmsg = new xmlrpcmsg("authenticate.logout", array (
			new xmlrpcval($this->sessionId(), "string")
		));
		$result = $this->client->send($rpcmsg);

	} //logout

	// =================
	function get($key) {

		dbgprint("xmlBlaster::get(" . $key . ")");

		$qos = '<qos></qos>';

		//my $messages = $self->{'server'}->call( 'xmlBlaster.get', $self->{'sessionId'}, $key ,$qos );
		$rpcmsg = new xmlrpcmsg("xmlBlaster.get", array (
			new xmlrpcval($this->sessionId(), "string"),
			new xmlrpcval($key, "string"),
			new xmlrpcval($qos, "string"),

			
		));
		$result = $this->client->send($rpcmsg);

		// check if xml-rpc error

		if ($result->faultCode()) {
			// a xml-rpc error occured
			$res = xmlrpc_decode($result->faultString());
			dbgprint("ERROR xmlBlaster::get() xml-rpc ERROR = $res");
			return array (
				'ERROR',
				$result->faultString()
			);
		}

		$res_array = php_xmlrpc_decode($result->value()); // fails for content: $result->serialize()

		// check if xmlBlaster error
		if (gettype($res_array) == 'array' && isset ($res_array['faultString'])) {
			dbgprint("ERROR xmlBlaster::get() xmlBlaster ERROR = [" . $res_array['faultString'] . "]");
			return array (
				'ERROR',
				$res_array['faultString']
			);
		}

		// copy raw results in a array of messageUnit

		$messages = array (
			count($res_array)
		);

		$cpt = 0;
		foreach ($res_array as $r) {
			//dbgprint( "OK xmlBlaster::get() response key= [$r[0]]" );
			//dbgprint( "OK xmlBlaster::get() response cont = " . (string)$r[1] );
			//dbgprint( "OK xmlBlaster::get() response qos = [$r[2]]" );
			$messages[$cpt++] = new messageUnit($r[0], $r[1], $r[2]);
		}

		return array (
			'OK',
			$messages
		);

	} //get

	/**
	* Publish a message. 
	* @param string $key PublishKey
	* @param string $content byte array
	* @param string $qos PublishQos
	* @return array 0: 'OK' or 'ERROR'', 1: class PublishReturnQos or errorString
	*/
	function publish($key, $content, $qos) {

		dbgprint("xmlBlaster::publish(" . $key . ")");

		$rpcmsg = new xmlrpcmsg("xmlBlaster.publish", array (
			new xmlrpcval($this->sessionId(), "string"),
			new xmlrpcval($key, "string"),
			new xmlrpcval($content, "base64"),
			new xmlrpcval($qos, "string"),

			
		));
		$result = $this->client->send($rpcmsg);

		// check if xml-rpc error

		if ($result->faultCode()) {
			// a xml-rpc error occured
			$res = xmlrpc_decode($result->faultString());
			dbgprint("ERROR xmlBlaster::publish() xml-rpc ERROR = $res");
			return array (
				'ERROR',
				$result->faultString()
			);
		}

		//dbgprint( "publishResponse=" . $result->serialize() );

		$res = php_xmlrpc_decode($result->value());

		//dbgprint("publishResponse=" . $res);

		// check if xmlBlaster error

		if (gettype($res) == 'array' && isset ($res['faultString'])) {
			dbgprint("ERROR xmlBlaster::publish() xmlBlaster ERROR = [" . $res['faultString'] . "]");
			return array (
				'ERROR',
				$res['faultString']
			);
		}

		return array (
				'OK',
				new PublishReturnQos($res)
				);

	} //publish

} //class xmlBlaster

class messageUnit {

	var $keyOid;
	var $content;
	var $qos;

	function messageUnit($key, $content, $qos) {

		$this->keyOid = $key;
		$this->content = $content;
		$this->qos = $qos;

	} //messageUnit

	function content() {

		return $this->content;

	} //content

	function keyOid() {

		return $this->keyOid;

	} //keyOid

	function qos() {

		return $this->qos;

	} //qos

} //messageUnit

// =================
// 08/07/02 23:25 cyrille@ktaland.com 
//
class ConnectQos {

	/*
	<qos>
	<securityService type="htpasswd" version="1.0">
	   <user>joe</user>
	   <passwd>secret</passwd>
	</securityService>
	<instanceId>/xmlBlaster/node/xmlBlaster_192_168_1_25_3412/instanceId/1216309365453</instanceId>
	<session name='/node/xmlBlaster_192_168_1_25_3412/client/joe/-46' timeout='86400000' maxSessions='10' clearSessions='false'
	         reconnectSameClientOnly='false' sessionId='sessionId:192.168.1.25-null-1216311403568--785272225-45'/>
	  <queue relating='connection' maxEntries='10000000' maxEntriesCache='1000'>
	    <address type='SOCKET' dispatchPlugin='undef'></address>
	  </queue>
	  <queue relating='subject'/>
	  <queue relating='callback' maxEntries='1000' maxEntriesCache='1000' maxBytes='4147483647' maxBytesCache='200555666'/>
	  <clientProperty name='__rcvTimestampStr'>2008-07-17 16:16:43.573Z</clientProperty>
	</qos>
	*/
	var $sessionId;

	var $xml;

	// =================
	function ConnectQos($xml) {

		dbgprint("ConnectQos::ConnectQos()");

		$this->xml = $xml;
		$this->sessionId = null;

	} //ConnectQos

	// =================
	function sessionId() {

		//dbgprint("ConnectQos::sessionId()");

		if (!isset ($this->sessionId))
			if (preg_match("/sessionId=\'(.*)\'\/>/", $this->xml, $matches)) {
				$this->sessionId = $matches[1];
			}
		return $this->sessionId;

	} //sessionId

} //class ConnectQos

/**
 * @see http://www.xmlblaster.org/xmlBlaster/doc/client-api/org/xmlBlaster/client/qos/PublishReturnQos.html
 */
class PublishReturnQos {
	/*
	<qos>
  	  <state id='OK' info='QUEUED[bilbo]'/> <!-- optionally, defaults to 'OK' -->
	  <key oid='HelloWorld'/>
	  <rcvTimestamp nanos='1007764305862000002'/>
	  <!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
	  <isPublish/>
	</qos>
	*/
	var $xml;
	var $state;
	function PublishReturnQos($xml) {

		dbgprint("PublishReturnQos::PublishReturnQos()");

		$this->xml = $xml;
		$this->state = 'OK';

	} //PublishReturnQos
	function state() {
		if (preg_match("/state id=\'(.*)\'\/>/", $this->xml, $matches)) {
			$this->state = $matches[1];
		}
		return $this->state;

	} //sessionId
} // class PublishReturnQos
?>
