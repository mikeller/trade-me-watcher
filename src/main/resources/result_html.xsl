<?xml version="1.0" encoding="ISO-8859-1"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
xmlns:tm="http://api.trademe.co.nz/v1" version="1.0">
	<xsl:output method="html" />

	<xsl:template match="/ScanResults">
		<html>
			<body>
				<xsl:apply-templates select="Searches|QuestionItems" />
			</body>
		</html>
	</xsl:template>

	<xsl:template match="Searches">
		<h1> Searches </h1>
		<hr/>
		<xsl:apply-templates select="./Search" />
	</xsl:template>

	<xsl:template match="Search">
		<h2>
			Search:
			<xsl:value-of select="@Parameters" />
		</h2>
		<hr/>
		<xsl:apply-templates select="./tm:Listing" />
	</xsl:template>

	<xsl:template match="tm:Listing | Item">
		<table>
			<tr>
				<td>
					<h3>
						<xsl:element name="a">
							<xsl:attribute name="href">http://www.trademe.co.nz/Browse/Listing.aspx?id=<xsl:value-of select="tm:ListingId" /></xsl:attribute>
							<xsl:value-of select="./tm:Title" />
						</xsl:element>
					</h3>
					<ul>
						<xsl:if test="./tm:IsNew='true'">
							<li>New Item</li>
						</xsl:if>
						<li>
							Start Price:
							<xsl:value-of select="./tm:StartPrice" />
						</li>
						<xsl:if test="./tm:HasBuyNow='true'">
							<li>
								Buy Now Price:
								<xsl:value-of select="./tm:BuyNowPrice" />
							</li>
						</xsl:if>
						<xsl:if test="./tm:MaxBidAmount">
							<li>
								Highest Bid:
								<xsl:value-of select="./tm:MaxBidAmount" />
							</li>
						</xsl:if>
						<xsl:if test="./tm:IsReserveMet = 'true'">
							<li>Reserve Met</li>
						</xsl:if>
						<li>End Date: <xsl:value-of select="./tm:EndDate"/></li>
						<xsl:if test="./tm:BidCount">
						<li>Number of Bids: <xsl:value-of select="./tm:BidCount" /></li>
						</xsl:if>
						<xsl:if test="./tm:ViewCount">
						<li>Number of Views: <xsl:value-of select="./tm:ViewCount" /></li>
						</xsl:if>
						<xsl:if test="./tm:Nickname">
						<li>Seller: <xsl:value-of select="./tm:Nickname" /></li>
						</xsl:if>						
						<li>Location: <xsl:value-of select="./tm:Region" /> / <xsl:value-of select="./tm:Suburb" /></li>
					</ul>
				</td>
				<td>
					<xsl:element name="img" >
						<xsl:attribute name="src">
        <xsl:value-of select="./tm:PictureHref | ./tm:List" />
      </xsl:attribute>
					</xsl:element>
				</td>
			</tr>
		</table>
		<hr/>
		<xsl:apply-templates select="./Questions" />
	</xsl:template>

	<xsl:template match="QuestionItems">
		<h1> Questions </h1>
		<hr/>
		<xsl:apply-templates select="./Item" />
	</xsl:template>

	<xsl:template match="Questions">
		<xsl:apply-templates select="./tm:Question" />
	</xsl:template>

	<xsl:template match="tm:Question">
	<xsl:choose>
	<xsl:when test="./tm:IsSellerComment='false'">
					<h3>Question: <xsl:value-of select="./tm:Comment" />
					</h3>
					<ul>
					<li>Asked by: <xsl:value-of select="./tm:AskingMember/tm:Nickname" /></li>
						<li><xsl:value-of select="./tm:Answer" />
						</li>
					</ul>
					</xsl:when><xsl:otherwise>
						<h3>Comment: <xsl:value-of select="./tm:Comment" />
					</h3>
					</xsl:otherwise></xsl:choose>
		<hr/>
	</xsl:template>

</xsl:stylesheet>


