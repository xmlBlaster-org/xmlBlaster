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
      <link REL="stylesheet" type="text/css" href="../xmlBlaster.css" />
      <title>xmlBlaster - Requirements</title>
   </head>

   <body>

   <p class="sideend">
       Last updated $Date: 2000/03/18 23:40:55 $ $Author: ruff $
   </p>
   <p class="sitetitel">XmlBlaster Requirements Reference</p>

   <p /><br />
   <table cellpadding="2" cellspacing="4">
      <thead>
         <tr>
         <th><b>ID</b></th>
         <th>Status</th>
         <th>Topic</th>
         </tr>
      </thead>
      <xsl:for-each select="/files/url">
         <xsl:apply-templates select="document(.)/requirement"/>
      </xsl:for-each>
   </table>
   </body>
   </html>
</xsl:template>

<xsl:template match="requirement">
   <tr>
      <td>
         <xsl:value-of select="@id"/></td>
      <td>
         <xsl:attribute name="class"><xsl:value-of select="@status"/></xsl:attribute>
         <xsl:value-of select="@status"/>
      </td>
      <td><xsl:value-of select="topic"/></td>
   </tr>
   
   <tr>
      <td></td>
      <td></td>
      <td class="description" colspan="1"><xsl:value-of select="description"/></td>
   </tr>

   <xsl:if test="example">
      <td></td>
      <td></td>
      <td class="example" colspan="1"><xsl:value-of select="example"/></td>
   </xsl:if>
</xsl:template>

</xsl:stylesheet>




