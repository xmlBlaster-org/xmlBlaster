<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>

<xsl:param name="queue.highalarm" select="10"/>
<xsl:param name="queue.highwarn" select="4"/>
<xsl:param name="show.cascading" select="'no'"/> <!-- the single quotes are important ! -->
<xsl:param name="show.initialFilesLocation" select="'yes'"/> <!-- the single quotes are important ! -->

<xsl:param name="refresh.rate" select="10"/>

<!-- If you choose not empty here (does not matter what) it will output all ReplSlaves together -->
<xsl:variable name="replPrefixAll"></xsl:variable>

<!-- otherwise you can choose up to five separations (if you use less, ignore) The value you pass
     here is the replication.prefix of the associated DbWatcher
-->
<xsl:variable name="replPrefix1">DEMO_</xsl:variable>
<xsl:variable name="replPrefix2">DEMO_</xsl:variable>
<xsl:variable name="replPrefix3"></xsl:variable>
<xsl:variable name="replPrefix4"></xsl:variable>
<xsl:variable name="replPrefix5"></xsl:variable>



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
        <a class="image" href="http://www.xmlblaster.org/replication/ch03.html"><img src="tims_rainbowfish.gif" alt="xmlBlaster" title="monitoring documentation" width="40"/></a>
     </div>
</xsl:template>

<xsl:template name="modifySessionName">
   <xsl:param name="content"/>
   <xsl:value-of select="substring-before($content, '/')"/>
</xsl:template>



<!-- These should not be changed, they are defined here since they are used in all stylesheets -->

<xsl:param name="request.objectname"/>


<!-- 'objectName=' is xmlBlaster specific, rest is MX4J search pattern -->
<xsl:param name="destinationListUrl" select="'mbean?objectname=org.xmlBlaster:contribClass=contrib,*&amp;attributes=true&amp;operations=false&amp;notifications=false&amp;constructors=false&amp;template=destinationList'"/>
<xsl:param name="sqlStatementsUrl" select="'mbean?objectname=org.xmlBlaster:contribClass=contrib,*&amp;attributes=true&amp;operations=false&amp;notifications=false&amp;constructors=false&amp;template=sqlStatements'"/>
<xsl:param name="initiateReplicationUrl" select="'mbean?objectname=org.xmlBlaster:contribClass=contrib,contrib=%22replication%22,*&amp;template=initiateReplication'"/>
<xsl:param name="doStatementUrl" select="'mbean?objectname=org.xmlBlaster:contribClass=contrib,contrib=%22replication%22,*&amp;template=doStatement'"/>

</xsl:stylesheet>
