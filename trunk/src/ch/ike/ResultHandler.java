package ch.ike;

import java.io.IOException;
import java.io.StringReader;

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

    public final DocumentBuilder docBuilder;
    public final XPathExpression itemExpr;
    public final XPathExpression listingIdExpr;

    public ResultHandler() {
	try {
	    docBuilder = docFactory.newDocumentBuilder();
	} catch (ParserConfigurationException e) {
	    throw new RuntimeException(e);
	}

	try {
	    itemExpr = xPathFactory.newXPath().compile(
		    "/SearchResults/List/Listing");
	    listingIdExpr = xPathFactory.newXPath().compile("./ListingId");
	} catch (XPathExpressionException e) {
	    throw new RuntimeException(e);
	}
    }

    NodeList parseResponse(String response) {
        InputSource is = new InputSource(new StringReader(response));
        try {
            Document document = docBuilder.parse(is);
            return (NodeList) itemExpr.evaluate(document,
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
            return (String) listingIdExpr.evaluate(item,
        	    XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }
}