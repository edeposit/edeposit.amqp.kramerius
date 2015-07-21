<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns="http://www.openarchives.org/OAI/1.1/oai_marc"
                >
  <xsl:template match="present">
    <xsl:apply-templates select="@*|node()|comment()"/>
  </xsl:template>

  <xsl:template match="record_header" />
  <xsl:template match="doc_number" />
  <xsl:template match="session-id" />
  
  <xsl:template match="comment()">
    <xsl:copy>
      <xsl:apply-templates select="comment()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="*">
    <xsl:element name="{local-name()}">
      <xsl:apply-templates select="node() | @*"/>
    </xsl:element>
  </xsl:template>

</xsl:stylesheet>
