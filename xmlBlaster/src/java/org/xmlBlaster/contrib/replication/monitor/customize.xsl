<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>

<xsl:param name="queue.highalarm" select="10"/>
<xsl:param name="queue.highwarn" select="4"/>
<xsl:param name="show.cascading" select="'no'"/> <!-- the single quotes are important ! -->
<xsl:param name="refresh.rate" select="10"/>

<xsl:template name="header">
    <div class="header"><br/>
      <table border="3px outset" width="630" style="background-color: #007524;">
        <tr>
	  <td>
	    <center>
	      <a style="color: #007524;" href="http://www.xmlBlaster.org"><img src="http://www.xmlBlaster.org/images/xmlBlaster/logo.gif" alt="logo" height="80"/></a>
            </center>
          </td>
        </tr>
      </table>
    </div>
</xsl:template>

<xsl:template name="footer">
     <div class="footer">
        <a class="image" href="http://www.xmlBlaster.org"><img src="tims_rainbowfish.gif" alt="xmlBlaster" title="xmlBlaster.org" width="40"/></a>
     </div>
</xsl:template>

<xsl:template name="modifySessionName">
   <xsl:param name="content" select=""/>
   <xsl:value-of select="substring-before(substring-after($content, '-'), '/')"/>
</xsl:template>



<!-- These should not be changed, they are defined here since they are used in all stylesheets -->

<xsl:param name="request.objectname"/>


<xsl:param name="destinationListUrl" select="'mbean?objectname=org.xmlBlaster:contribClass=contrib,*&amp;attributes=true&amp;operations=false&amp;notifications=false&amp;constructors=false&amp;template=destinationList'"/>
<xsl:param name="initiateReplicationUrl" select="'mbean?objectname=org.xmlBlaster:contribClass=contrib,contrib=%22replication%22,*&amp;template=initiateReplication'"/>


</xsl:stylesheet>
