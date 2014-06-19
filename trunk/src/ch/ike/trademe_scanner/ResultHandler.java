package ch.ike.trademe_scanner;

import java.io.IOException;
import java.io.StringReader;
import java.util.Calendar;
import java.util.Iterator;

import javax.xml.bind.DatatypeConverter;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
		docFactory.setNamespaceAware(true);
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}

		NamespaceContext ns = new NamespaceContext() {
			@Override
			public String getNamespaceURI(String prefix) {
				if ("tm".equals(prefix)) {
					return "http://api.trademe.co.nz/v1";
				} else if ("i".equals(prefix)) {
					return "http://www.w3.org/2001/XMLSchema-instance";
				}
				return null;
			}

			@Override
			public String getPrefix(String namespaceURI) {
				return null;
			}

			@SuppressWarnings("rawtypes")
			@Override
			public Iterator getPrefixes(String namespaceURI) {
				return null;
			}
		};

		try {
			XPath xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);			
			searchListingExpr = xPath.compile(
					"/tm:SearchResults/tm:List/tm:Listing");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);			
			watchlistItemExpr = xPath.compile(
					"/tm:Watchlist/tm:List/tm:WatchlistItem");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);			
			listingQuestionsExpr = xPath.compile(
					"/tm:ListedItemDetail/tm:Questions/tm:List/tm:Question");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);			
			listingIdExpr = xPath.compile("./tm:ListingId");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);			
			startDateExpr = xPath.compile("./tm:StartDate");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);			
			titleExpr = xPath.compile("./tm:Title");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);			
			questionIdExpr = xPath.compile(
					"./tm:ListingQuestionId");
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
			return DatatypeConverter.parseDateTime((String) startDateExpr
					.evaluate(item, XPathConstants.STRING));
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

	public Element createScanResultsDocument() {
		Element result = docBuilder.newDocument().createElement("ScanResults");
		result.getOwnerDocument().appendChild(result);
		
		return result;		
	}

	private Element getOrCreateElement(Element parent, String name) {
		Element result;
		NodeList resultList = parent.getElementsByTagName(name);
		if (resultList.getLength() > 0) {
			result = (Element) resultList.item(0);
		} else {
			result = parent.getOwnerDocument().createElement(name);
			parent.appendChild(result);
		}
		return result;
	}

	private void importNode(Element parent, Node node) {
		Node result = parent.getOwnerDocument().importNode(node, true);
		parent.appendChild(result);
	}

	public Element addQuestion(Element item, Element parent, String title,
			Node question) {
		
		importNamespaces(parent, question);
		
		if (item == null) {
			Element questions = getOrCreateElement(parent, "Questions");

			item = parent.getOwnerDocument().createElement("Item");

			item.setAttribute("ItemTitle", title);

			questions.appendChild(item);
		}

		importNode(item, question);

		return item;
	}

	public Element addItem(Element search, Element parent,
			String searchParameters, Node item) {
		
		importNamespaces(parent, item);
		
		if (search == null) {
			Element searches = getOrCreateElement(parent, "Searches");

			search = parent.getOwnerDocument().createElement("Search");

			search.setAttribute("Parameters", searchParameters);

			searches.appendChild(search);
		}

		importNode(search, item);

		return search;
	}

	private void importNamespaces(Node dest, Node source) {
		NamedNodeMap sourceAttrs = source.getOwnerDocument().getDocumentElement().getAttributes();
		int i = 0;
		while (i < sourceAttrs.getLength()) {
			Attr sourceAttr = (Attr)sourceAttrs.item(i);
			if (sourceAttr.getName().startsWith("xmlns:")) {
				dest.getOwnerDocument().getDocumentElement().setAttribute(sourceAttr.getName(), sourceAttr.getValue());				
			}
			
			i = i + 1;
		}
	}
}