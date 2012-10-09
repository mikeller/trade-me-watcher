package ch.ike;

import java.io.IOException;
import java.io.StringReader;
import java.util.Calendar;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ResultHandler {
	private static final DocumentBuilderFactory docFactory = DocumentBuilderFactory
			.newInstance();
	private static final XPathFactory xPathFactory = XPathFactory.newInstance();

	private final DocumentBuilder docBuilder;
	private final XPathExpression searchListingExpr;
	private final XPathExpression watchlistItemExpr;
	private final XPathExpression listingQuestionsExpr;
	private final XPathExpression listingIdExpr;
	private final XPathExpression titleExpr;
	private final XPathExpression questionIdExpr;
	private final XPathExpression startDateExpr;

	public ResultHandler() {
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}

		try {
			searchListingExpr = xPathFactory.newXPath().compile(
					"/SearchResults/List/Listing");
			watchlistItemExpr = xPathFactory.newXPath().compile(
					"/Watchlist/List/WatchlistItem");
			listingQuestionsExpr = xPathFactory.newXPath().compile(
					"/ListedItemDetail/Questions/List/Question");
			listingIdExpr = xPathFactory.newXPath().compile("./ListingId");
			startDateExpr = xPathFactory.newXPath().compile("./StartDate");
			titleExpr = xPathFactory.newXPath().compile("./Title");
			questionIdExpr = xPathFactory.newXPath().compile(
					"./ListingQuestionId");
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	NodeList getSearchListings(String response) {
		InputSource is = new InputSource(new StringReader(response));
		try {
			Document document = docBuilder.parse(is);
			return (NodeList) searchListingExpr.evaluate(document,
					XPathConstants.NODESET);
		} catch (SAXException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	String getListingId(Node item) {
		try {
			return (String) listingIdExpr.evaluate(item, XPathConstants.STRING);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	Calendar getStartDate(Node item) {
		try {
			return DatatypeConverter.parseDateTime((String) startDateExpr.evaluate(item, XPathConstants.STRING));
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	NodeList getWatchlistItem(String response) {
		InputSource is = new InputSource(new StringReader(response));
		try {
			Document document = docBuilder.parse(is);
			return (NodeList) watchlistItemExpr.evaluate(document,
					XPathConstants.NODESET);
		} catch (SAXException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public String getTitle(Node item) {
		try {
			return (String) titleExpr.evaluate(item, XPathConstants.STRING);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public NodeList getListingQuestions(String response) {
		InputSource is = new InputSource(new StringReader(response));
		try {
			Document document = docBuilder.parse(is);
			return (NodeList) listingQuestionsExpr.evaluate(document,
					XPathConstants.NODESET);
		} catch (SAXException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public String getQuestionId(Node item) {
		try {
			return (String) questionIdExpr
					.evaluate(item, XPathConstants.STRING);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public Node getBody(String response) throws SAXException, IOException {
		InputSource is = new InputSource(new StringReader(response));
		return docBuilder.parse(is);
	}
}