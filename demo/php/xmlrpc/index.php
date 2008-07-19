<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">
<?php


/**
 *	look at xmlBlanster.inc to know more
 *
 *	08/07/02 21:51 cyrille@ktaland.com 
 */

// ===============
$DEBUG = 1;
function dbgprint($str) {
	global $DEBUG;
	if (isset ($DEBUG) && $DEBUG > 0)
		error_log("[DBG] $str", 0);
} //dbgprint
function svar_dump($data) {
	// like var_dump, but no print !
	ob_start();
	var_dump($data);
	$ret_val = ob_get_contents();
	ob_end_clean();
	return $ret_val;
}
// ===============

include ('xmlBlaster.inc');

$server = getHttpVar('server', 'localhost');
$port = getHttpVar('port', 8080);
$user = getHttpVar('user', 'joe');
$password = getHttpVar('password', 'secret');
$securityPlugin = getHttpVar('securityPlugin', 'htpasswd');
$xpathQuery = getHttpVar('xpathQuery', '//key');
$xbMethod = getHttpVar('xbMethod');
$publishKey = getHttpVar('publishKey');
$publishQos = getHttpVar('publishQos');
$publishContent = getHttpVar('publishContent');
$publishRes = getHttpVar('publishRes');

if ($xpathQuery != "") {
	$xpathQuery = preg_replace("/\\\'/", "'", $xpathQuery);
}

if ($publishKey != "") {
	$publishKey = preg_replace("/\\\'/", "'", $publishKey);
}
if ($publishQos != "") {
	$publishQos = preg_replace("/\\\'/", "'", $publishQos);
}

/*
 *	Create a xmlBlaster object and connect it to xmlBlaster server
 */

$xb = new xmlBlaster($server, $port, $user, $password);
$xb->securityService_type = $securityPlugin;

$res = $xb->connect();
if ($res[0] != 'OK') {
	$error_message = $res[1];
}

/*
 *	get some server's information
 */

$sysInternal = array ();
if ($xb->isConnected()) {

	$sysInternalVariables = array (
		'nodeId',
		'version',
		'uptime',
		'totalMem',
		'usedMem',
		'freeMem',
		'clientList'
	);
	//$sysInternalVariables = array( 'uptime' );
	//$sysInternalVariables = array( 'freeMem','syspropList','toto' );

	foreach ($sysInternalVariables as $sysVar) {

		$res = $xb->get("<key oid='__cmd:?$sysVar' />");

		// pre-define content because used later for echo in html ...
		$sysInternal[$sysVar] = '?';

		if ($res[0] != 'OK') {
			$error_message = $res[1];

			$sysInternal[$sysVar] = $error_message;

		} else {
			$messages = $res[1];
			$message_count = count($messages);
			// should be only one because it's admin value

			foreach ($messages as $msg) {
				dbgPrint("MSG:" . $msg->content());
				$sysInternal[$sysVar] = $msg->content();
			}
		}
	}
}
?>

<html>
<head>
	<title>PHP XmlBlaster client - running on <?php echo $server ?> </title>

<style type="text/css">
<!--
.text {  font-family: Verdana, Arial, Helvetica, sans-serif; font-size: 10pt; color: #666666}
.title {  font-family: Verdana, Arial, Helvetica, sans-serif; font-size: 12pt; color: #000000}
.error {  font-family: Verdana, Arial, Helvetica, sans-serif; font-size: 12pt; color: #FF2020}
-->
</style>

<script language="javascript"> <!--

// --> </script>

</head>
<body>

<!-- only for debugging purposes uncomment this
<center>
<table>
  <tr><th></th><th></th></tr>
  <tr><td>server</td><td><?php echo $server; ?></td></tr>
  <tr><td>port</td><td><?php echo $port; ?></td></tr>
  <tr><td>user</td><td><?php echo $user; ?></td></tr>
  <tr><td>password</td><td><?php echo $password; ?></td></tr>
  <tr><td>xpathQuery</td><td><?php echo $xpathQuery; ?></td></tr>
  <tr><td>xbMethod</td><td><?php echo $xbMethod; ?></td></tr>
  <tr><td>publishKey (html)</td><td><?php echo htmlentities($publishKey); ?></td></tr>
  <tr><td>publishQos (html)</td><td><?php echo htmlentities($publishQos); ?></td></tr>
  <tr><td>publishContent</td><td><?php echo $publishContent; ?></td></tr>
  <tr><td>publishRes</td><td><?php echo $publishRes; ?></td></tr>
</table>
</center>
-->

<table>
<tr>
<td class="title"><a href="http://xmlBlaster.org/"><img src="logo_xmlBlaster_2.gif" border="0"></a></td>
<td class="title"><a href="http://xmlBlaster.org/">xmlBlaster</a> client for <a href="http://php.net/">php</a>,<br>using <a href="http://phpxmlrpc.sourceforge.net/">xml-rpc</a> protocol</td>
</tr>
</table>

<?php if( isset($error_message) ){ ?>
<p>&nbsp;</p>
<table border="1"><tr><td class="error">
ERROR : <?php echo $error_message; ?>
</td></tr></table>
<?php } ?>

<p>&nbsp;</p>
<form method="GET">
<div class="text">Need some parameters to connect :</div>
 <table>

  <tr>
   <td class="text">server :</td>
   <td class="text">
	<input type="text" name="server" value="<?php echo $server; ?>">
   </td>

   <td class="text" rowspan="4">

	<table>
	<?php foreach ($sysInternal as $k => $v) {?>
	<tr><td class="text"> &nbsp; <?php echo htmlentities($k); ?></td><td class="text"><?php echo htmlentities($v); ?></td></tr>
	<?php } ?>
	</table>

   </td>
  </tr>
  <tr><td class="text">port :</td><td class="text"><input type="text" name="port" value="<?php echo $port; ?>"></td></tr>
  <tr><td class="text">user :</td><td class="text"><input type="text" name="user" value="<?php echo $user; ?>"></td></tr>
  <tr><td class="text">password :</td><td class="text"><input type="text" name="password" value="<?php echo $password; ?>"></td></tr>
  <tr><td class="text">securityPlugin :</td><td class="text"><input type="text" name="securityPlugin" value="<?php echo $securityPlugin; ?>"></td></tr>
  <tr><td class="text" colspan="3" align="center"><input type="submit" value=" Connect "></td></tr>
 </table>
</form>

<p>&nbsp;</p>
<form method="GET">
<div class="text">After connection, you can query the server for messages :</div>
	<input type="hidden" name="xbMethod" value="get">
	<input type="hidden" name="server" value="<?php echo $server; ?>"><input type="hidden" name="port" value="<?php echo $port; ?>">
	<input type="hidden" name="user" value="<?php echo $user; ?>"><input type="hidden" name="password" value="<?php echo $password; ?>">
	<input type="hidden" name="securityPlugin" value="<?php echo $securityPlugin; ?>">
 <table border="1">
  <tr>
   <td class="text">
	XPath Query :<br><input type="text" name="xpathQuery" value="<?php echo $xpathQuery; ?>" size="50">
   </td>
  </tr>
  <tr><td><input type="submit" value=" Query "></td></tr>

<?php


if ($xb->isConnected() && isset ($xpathQuery) && ($xbMethod == 'get')) {

	// $key = "<key oid='' queryType='XPATH'>/xmlBlaster/key[starts-with(\@oid,'myHel')]</key>" ;
	$res = $xb->get("<key oid='' queryType='XPATH'>$xpathQuery</key>");

	if ($res[0] != 'OK') {
		$error_message = $res[1];
?>
		<tr><td>
			<?php echo $error_message; ?>
		</td></tr>
		<?php


	} else {
		$messages = $res[1];

		foreach ($messages as $msg) {
			//dbgPrint( "MSG.type:" .gettype($msg) );
			dbgPrint("MSG:" . svar_dump($msg));
			if ($msg == 0) {
?>
				<tr><td>
					No Result.
				</td></tr>
				<?php


			} else {
?>
				<tr><td>
					<?php echo htmlentities($msg->keyOid()); ?>
					<br>
					<?php echo htmlentities($msg->content()); ?>
				</td></tr>
				<?php


			}
		}
	}
}
?>

 </table>
</form>


<p>&nbsp;</p>
<form method="GET">
<div class="text">A Publish Test :</div>
 <input type="hidden" name="xbMethod" value="publish">
<?php

if ($xbMethod == 'publish') {
	$arr = $xb->publish($publishKey, $publishContent, $publishQos);
	if ($arr[0] == "OK") {
		$publishReturnQos = $arr[1];
		$publishRes = $publishReturnQos->state();
		dbgprint("PublishReturnQos::state=" . $publishRes);
	}
	else {
		$publishRes = $arr[1];
		dbgprint("PublishReturnQos::failed=" . $arr[1]);
	}
}
?> 

  <input type="hidden" name="server" value="<?php echo $server; ?>"><input type="hidden" name="port" value="<?php echo $port; ?>">
  <input type="hidden" name="user" value="<?php echo $user; ?>"><input type="hidden" name="password" value="<?php echo $password; ?>">
  <input type="hidden" name="securityPlugin" value="<?php echo $securityPlugin; ?>">


  <input type="hidden" name="$publishRes" value="<?php echo $publishRes; ?>">

 <table border="1">
  <tr><td class="text">key :</td><td class="text"><input type="text" id='publishKey' name="publishKey" value="<key oid='phpPublish'/>" size="50"></td></tr>
  <tr><td class="text">content :</td><td class="text"><input type="text" id='publishContent' name="publishContent" value="<?php echo $publishContent; ?>" size="50"></td></tr>
  <tr><td class="text">qos :</td><td class="text"><input type="text" id='publishQos' name="publishQos" value="&lt;qos/>" size="50"></td></tr>
  <tr><td class="text">Result :</td><td class="text"><input type="text" id='publishRes' name="publishRes" value="<?php echo $publishRes; ?>" size="50"></td></tr>
  <tr><td class="text" colspan="2" align="center"><input type="submit" value=" Publish "></td></tr>
 </table>
</form>


</body>
</html>

<?php $xb->logout(); ?>

<?php


function getHttpVar($name, $defaultVal = null) {
	// 04/06/02 mad@ktaland.com
	//global $HTTP_GET_VARS, $HTTP_POST_VARS, $HTTP_SERVER_VARS ;
	if ($_SERVER['REQUEST_METHOD'] == 'POST') {
		if (isset ($_POST[$name])) {
			return $_POST[$name];
		}
	} else {
		if (isset ($_GET[$name])) {
			return $_GET[$name];
		}
	}
	return $defaultVal;
}
?>
