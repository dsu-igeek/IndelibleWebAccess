<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE xsl:stylesheet  [
	<!ENTITY nbsp   "&#160;">
	<!ENTITY copy   "&#169;">
	<!ENTITY reg    "&#174;">
	<!ENTITY trade  "&#8482;">
	<!ENTITY mdash  "&#8212;">
	<!ENTITY ldquo  "&#8220;">
	<!ENTITY rdquo  "&#8221;"> 
	<!ENTITY pound  "&#163;">
	<!ENTITY yen    "&#165;">
	<!ENTITY euro   "&#8364;">
]>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:media="http://search.yahoo.com/mrss/">
	<xsl:output method="xml" encoding="UTF-8" indent="yes" version="1.0"/>
	<xsl:template match="/">
		<rss version="2.0"
			xmlns:media="http://search.yahoo.com/mrss/">
			<xsl:apply-templates/>
		</rss>
	</xsl:template>
	<xsl:template match="albumlist">
		<channel>
			<title>Smith-Uchida Photo Feed</title>
			<link>http://www.smuchi.com/rss-photos/feed.sh</link>
			<description>Homedisk Photo Feed</description>
			<ttl>60</ttl>
			<xsl:for-each select="photo">
				<item>
					<title><xsl:value-of select="imagekey"/></title>
					<link><xsl:value-of select="../link" />/<xsl:value-of select="imagekey" />?getimage</link>
					<category>My Photos</category>
					<description></description>
					<pubDate></pubDate>
					<media:content url="{../link}/{imagekey}?getimage" type="image/{imageType}" height="600" width="800" duration="10" />
				</item>
			</xsl:for-each>
		</channel>
	</xsl:template>
</xsl:stylesheet>