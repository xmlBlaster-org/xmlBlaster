<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">

<xsl:output method="html"/>

<xsl:template match="/">
	<html>
	<head>
	   <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
	   <title>xmlBlaster - Requirements</title>
	</head>

	<body>
	<h1>XmlBlaster Requirements</h1>
   <TABLE BORDER="1" cellpadding="6">
      <THEAD>
         <TR>
         <TD><b>ID</b></TD>
         <TD>Status</TD>
         <TD>Topic</TD>
         </TR>
      </THEAD>
      <xsl:for-each order-by="+ requirement[@id]" select="/requirement">
         <TR>
            <TD><xsl:value-of select="@id"/></TD>
            <TD><xsl:value-of select="fix/@status"/></TD>
            <TD><xsl:value-of select="topic"/></TD>
         </TR>
      </xsl:for-each>
   </TABLE>
	</body>
	</html>
</xsl:template>


</xsl:stylesheet>

