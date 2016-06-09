package ch.ike.trademe_scanner;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import javax.xml.bind.DatatypeConverter;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
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
	private static final String TRADEME_PREFIX = "tm";

	private static final String ITEM = "Item";

	private static final String QUESTION_ITEMS = "QuestionItems";

	private static final String SEARCH = "Search";

	private static final String SEARCHES = "Searches";

	private static final DocumentBuilderFactory docFactory = DocumentBuilderFactory
			.newInstance();
	private static final XPathFactory xPathFactory = XPathFactory.newInstance();

	private final DocumentBuilder docBuilder;
	private final XPathExpression searchListingExpr;
	private final XPathExpression watchlistItemExpr;
	private final XPathExpression listingQuestionsExpr;
	private final XPathExpression listingIdExpr;
	private final XPathExpression priceExpr;
	private final XPathExpression buyNowPriceExpr;
	private final XPathExpression titleExpr;
	private final XPathExpression questionIdExpr;
	private final XPathExpression startDateExpr;
	private final XPathExpression listingContentsExpr;
	private final XPathExpression questionCountExpr;
	private final XPathExpression itemCountExpr;
	private final XPathExpression searchListExpr;

	private static final TransformerFactory tFactory = TransformerFactory
			.newInstance();
	private final Transformer htmlTransformer;
	private final Transformer stringTransformer;

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
				if (TRADEME_PREFIX.equals(prefix)) {
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
			searchListingExpr = xPath
					.compile("/tm:SearchResults/tm:List/tm:Listing");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			watchlistItemExpr = xPath
					.compile("/tm:Watchlist/tm:List/tm:WatchlistItem");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			listingQuestionsExpr = xPath
					.compile("/tm:ListedItemDetail/tm:Questions/tm:List/tm:Question");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			listingIdExpr = xPath.compile("./tm:ListingId");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			priceExpr = xPath.compile("./tm:StartPrice");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			buyNowPriceExpr = xPath.compile("./tm:BuyNowPrice");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			startDateExpr = xPath.compile("./tm:StartDate");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			titleExpr = xPath.compile("./tm:Title");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			questionIdExpr = xPath.compile("./tm:ListingQuestionId");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			listingContentsExpr = xPath
					.compile("/tm:ListedItemDetail/tm:ListingId | /tm:ListedItemDetail/tm:Title | "
							+ "/tm:ListedItemDetail/tm:StartPrice | /tm:ListedItemDetail/tm:EndDate | "
							+ "/tm:ListedItemDetail/tm:HasBuyNow | /tm:ListedItemDetail/tm:BuyNowPrice | "
							+ "/tm:ListedItemDetail/tm:MaxBidAmount | /tm:ListedItemDetail/tm:Region | "
							+ "/tm:ListedItemDetail/tm:Suburb | /tm:ListedItemDetail/tm:BidCount | "
							+ "/tm:ListedItemDetail/tm:ViewCount | /tm:ListedItemDetail/tm:IsReserveMet | "
							+ "/tm:ListedItemDetail/tm:Member/tm:Nickname | "
							+ "/tm:ListedItemDetail/tm:Photos/tm:Photo[tm:PhotoId = /tm:ListedItemDetail/tm:PhotoId]/tm:Value/tm:List");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			itemCountExpr = xPath
					.compile("/ScanResults/Searches/Search/tm:Listing");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			searchListExpr = xPath
					.compile("/ScanResults/Searches/Search/@Parameters");

			xPath = xPathFactory.newXPath();
			xPath.setNamespaceContext(ns);
			questionCountExpr = xPath
					.compile("/ScanResults/QuestionItems/Item/Questions/tm:Question");
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}

		try {
			htmlTransformer = tFactory.newTransformer(new StreamSource(
					getClass().getResource("/result_html.xsl").toString()));
			stringTransformer = tFactory.newTransformer();
		} catch (TransformerConfigurationException e) {
			throw new RuntimeException(e);
		}
		stringTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
		stringTransformer.setOutputProperty(
				"{http://xml.apache.org/xslt}indent-amount", "2");
	}

	public NodeList getSearchListings(Document document) {
		try {
			return (NodeList) searchListingExpr.evaluate(document,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public String getListingId(Node item) {
		try {
			return (String) listingIdExpr.evaluate(item, XPathConstants.STRING);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public Calendar getStartDate(Node item) {
		try {
			return DatatypeConverter.parseDateTime((String) startDateExpr
					.evaluate(item, XPathConstants.STRING));
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public NodeList getWatchlistItem(Document document) {
		try {
			return (NodeList) watchlistItemExpr.evaluate(document,
					XPathConstants.NODESET);
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

	public NodeList getListingQuestions(Document document) {
		try {
			return (NodeList) listingQuestionsExpr.evaluate(document,
					XPathConstants.NODESET);
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

	public NodeList getListingContents(Document document) {
		try {
			return (NodeList) listingContentsExpr.evaluate(document,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public Document getBody(String response) {
		InputSource is = new InputSource(new StringReader(response));
		try {
			return docBuilder.parse(is);
		} catch (SAXException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Element createScanResultsDocument() {
		Element result = docBuilder.newDocument().createElementNS(null,
				"ScanResults");
		result.getOwnerDocument().appendChild(result);

		return result;
	}

	private Element getOrCreateElement(Element parent, String name) {
		Element result;
		NodeList resultList = parent.getElementsByTagName(name);
		if (resultList.getLength() > 0) {
			result = (Element) resultList.item(0);
		} else {
			result = parent.getOwnerDocument().createElementNS(null, name);
			parent.appendChild(result);
		}
		return result;
	}

	private Node importElement(Element parent, Element element,
			String defaultNamespace) {
		Element result = ((Element) parent.getOwnerDocument().importNode(
				element, true));
		parent.appendChild(result);

		result.setPrefix(TRADEME_PREFIX);
		NodeList subNodes = ((Element) result).getElementsByTagNameNS(
				defaultNamespace, "*");
		int i = 0;
		while (i < subNodes.getLength()) {
			subNodes.item(i).setPrefix(TRADEME_PREFIX);

			i = i + 1;
		}

		return result;
	}

	public Element addItemDetail(Element item, Element parent, Element detail) {

		String defaultNamespace = importNamespaces(parent, detail);

		if (item == null) {
			Element questions = getOrCreateElement(parent, QUESTION_ITEMS);

			item = parent.getOwnerDocument().createElementNS(null, ITEM);

			questions.appendChild(item);
		}

		importElement(item, detail, defaultNamespace);

		return item;
	}

	public Element addQuestion(Element questions, Element parent,
			Element question) {

		String defaultNamespace = importNamespaces(parent, question);

		if (questions == null) {
			questions = parent.getOwnerDocument().createElementNS(null, "Questions");

			parent.appendChild(questions);
		}

		importElement(questions, question, defaultNamespace);

		return questions;
	}

	public Element addItem(Element search, Element parent,
			String searchParameters, Element item) {

		String defaultNamespace = importNamespaces(parent, item);

		if (search == null) {
			Element searches = getOrCreateElement(parent, SEARCHES);

			search = parent.getOwnerDocument().createElementNS(null, SEARCH);

			search.setAttribute("Parameters", searchParameters);

			searches.appendChild(search);
		}

		importElement(search, item, defaultNamespace);

		return search;
	}

	private String importNamespaces(Node dest, Node source) {
		NamedNodeMap sourceAttrs = source.getOwnerDocument()
				.getDocumentElement().getAttributes();
		String defaultNamespace = "";
		int i = 0;
		while (i < sourceAttrs.getLength()) {
			Attr sourceAttr = (Attr) sourceAttrs.item(i);
			if (sourceAttr.getName().startsWith("xmlns")) {
				String name = sourceAttr.getName();
				if ("xmlns".equals(name)) {
					name = name + ":" + TRADEME_PREFIX;

					defaultNamespace = sourceAttr.getValue();
				}

				dest.getOwnerDocument()
						.getDocumentElement()
						.setAttributeNS("http://www.w3.org/2000/xmlns/", name,
								sourceAttr.getValue());
			}

			i = i + 1;
		}

		return defaultNamespace;
	}

	public int getItemCount(Document document) {
		try {
			return ((NodeList) itemCountExpr.evaluate(document,
					XPathConstants.NODESET)).getLength();
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public List<String> getSearchList(Document document) {
		try {
			NodeList results = ((NodeList) searchListExpr.evaluate(document,
					XPathConstants.NODESET));
			ArrayList<String> searchList = new ArrayList<String>();
			int i = 0;
			while (i < results.getLength()) {
				searchList.add(results.item(i).getNodeValue());

				i = i + 1;
			}

			return searchList;
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public int getQuestionCount(Document document) {
		try {
			return ((NodeList) questionCountExpr.evaluate(document,
					XPathConstants.NODESET)).getLength();
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public String toHtml(Element input) {
		return transform(input, htmlTransformer);
	}

	public String toString(Element input) {
		return transform(input, stringTransformer);
	}

	private String transform(Element input, Transformer transformer) {
		StringWriter output = new StringWriter();
		try {
			transformer.transform(new DOMSource(input),
					new StreamResult(output));
		} catch (TransformerException e) {
			throw new RuntimeException(e);
		}
		return output.toString();
	}

	public float getPrice(Element item) {
		try {
			float result = 0;
			String resultStr = (String)priceExpr.evaluate(item, XPathConstants.STRING);
			try {
				result = Float.parseFloat(resultStr);
			} catch (NumberFormatException e) {
			}

			return result;
		} catch (XPathExpressionException | NumberFormatException  e) {
			throw new RuntimeException(e);
		}
	}

	public float getBuyNowPrice(Element item) {
		try {
			float result = 0;
			String resultStr = (String)buyNowPriceExpr.evaluate(item, XPathConstants.STRING);
			try {
				result = Float.parseFloat(resultStr);
			} catch (NumberFormatException e) {
			}

			return result;
		} catch (XPathExpressionException | NumberFormatException  e) {
			throw new RuntimeException(e);
		}
	}
}
