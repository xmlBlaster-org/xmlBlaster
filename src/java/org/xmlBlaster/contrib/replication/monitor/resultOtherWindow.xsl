<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<xsl:include href="customize.xsl"/>

<xsl:param name="request.val"/>

<xsl:template match ='/'>
<html>
<head>
<title>Result in other window</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>

<link href="styles.css" rel="stylesheet" type="text/css"/>
<script language="JavaScript" type="text/javascript">

function gotoDestination() {
   if (self.opener != null) {
      if (self.opener.sync != null) {
        self.opener.sync.val = "<xsl:value-of select='$request.val'/>";
        self.opener.sync.err = "<xsl:value-of select='/MBeanOperation/Operation/attribute::return'/>";
        // + "<xsl:value-of select='/MBeanOperation/Operation/attribute::result'/>";
      }
   }
}

</script>

</head>
   <body onLoad="gotoDestination();">
      <center>Initiating Replication ...</center>
   </body>
</html>
</xsl:template>

</xsl:stylesheet>



