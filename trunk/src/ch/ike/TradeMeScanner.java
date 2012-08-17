package ch.ike;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import nz.co.trademe.TradeMeApi;

import org.scribe.builder.ServiceBuilder;
import org.scribe.exceptions.OAuthException;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class TradeMeScanner implements Runnable {

    private static final String SECRET = "Secret";
    private static final String TOKEN = "Token";
    private static final String ACCESS_TOKEN = "AccessToken";

    private static final DocumentBuilderFactory docFactory = DocumentBuilderFactory
	    .newInstance();
    private static final XPathFactory xPathFactory = XPathFactory.newInstance();

    public static void main(String[] args) {
	TradeMeScanner self = new TradeMeScanner(args);
	self.main();
    }

    private boolean stopped = false;

    private final Properties props;
    private final Preferences prefs;
    private final Preferences seenItems;

    private final DocumentBuilder docBuilder;

    private final XPathExpression itemExpr;
    private final XPathExpression listingIdExpr;

    public TradeMeScanner(String[] args) {
	props = new Properties();
	FileInputStream in;
	try {
	    in = new FileInputStream("TradeMeScanner.xml");
	} catch (FileNotFoundException e) {
	    throw new RuntimeException(e);
	}

	try {
	    props.loadFromXML(in);
	} catch (InvalidPropertiesFormatException e) {
	    throw new RuntimeException(e);
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}

	try {
	    in.close();
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}

	prefs = Preferences.userNodeForPackage(this.getClass());

	seenItems = prefs.node("seen_items");

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

    private void main() {
	Thread loop = new Thread(this);
	loop.start();

	String input = "";
	BufferedReader inReader = new BufferedReader(new InputStreamReader(
		System.in));
	while ((input != null) && !input.equals("x")) {
	    try {
		input = inReader.readLine();
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	stopped = true;

	loop.interrupt();
	try {
	    loop.join();
	} catch (InterruptedException e) {
	}
    }

    public void run() {
	OAuthService service = new ServiceBuilder().provider(TradeMeApi.class)
		.apiKey(props.getProperty("consumer.key"))
		.apiSecret(props.getProperty("consumer.secret")).build();

	Token accessToken = null;
	try {
	    if (prefs.nodeExists(ACCESS_TOKEN)) {
		accessToken = new Token(prefs.node(ACCESS_TOKEN).get(TOKEN,
			null), prefs.node(ACCESS_TOKEN).get(SECRET, null));
	    } else {
		System.out
			.println("This application needs authorization first.");

		Token requestToken = service.getRequestToken();

		System.out.println("Please go to "
			+ service.getAuthorizationUrl(requestToken)
			+ " to authorize this app.");
		System.out.println();

		BufferedReader br = new BufferedReader(new InputStreamReader(
			System.in));

		String pin = null;
		while (accessToken == null) {
		    System.out.print("Please enter PIN (empty to abort): ");

		    try {
			pin = br.readLine();
		    } catch (IOException ioe) {
			System.out.println("IO error trying to read the PIN!");
			System.exit(1);
		    }

		    if (pin.equals("")) {
			System.out.println("Aborting");
			System.exit(1);
		    }

		    try {
			Verifier v = new Verifier(pin);
			accessToken = service.getAccessToken(requestToken, v);
		    } catch (OAuthException e) {
			System.out.println("Exception during authentication: "
				+ e.getMessage());
			System.out.println("Retrying");
		    }
		    System.out.println();
		}

		prefs.node(ACCESS_TOKEN).put(TOKEN, accessToken.getToken());
		prefs.node(ACCESS_TOKEN).put(SECRET, accessToken.getSecret());
		prefs.flush();

		System.out.println("Authorization successful.");
	    }
	} catch (BackingStoreException e) {
	    throw new RuntimeException(e);
	}
	
	int interval = Integer.parseInt(props.getProperty("search.interval"));

	while (!stopped) {
	    OAuthRequest request = new OAuthRequest(Verb.GET,
		    "https://api.trademe.co.nz/v1/Search/General.xml?"
			    + props.getProperty("search.parameters"));
	    service.signRequest(accessToken, request);

	    Response response = request.send();

	    InputSource is = new InputSource(new StringReader(
		    response.getBody()));
	    Document document;
	    try {
		document = docBuilder.parse(is);
	    } catch (SAXException e) {
		throw new RuntimeException(e);
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }

	    NodeList items;
	    try {
		items = (NodeList) itemExpr.evaluate(document,
			XPathConstants.NODESET);
	    } catch (XPathExpressionException e) {
		throw new RuntimeException(e);
	    }

	    System.out.println("Got " + items.getLength() + " items.");
	    System.out.println();

	    List<String> seenList;
	    try {
		seenList = new ArrayList<String>(
			Arrays.asList(seenItems.keys()));
	    } catch (BackingStoreException e) {
		throw new RuntimeException(e);
	    }

	    int index = 0;
	    while (index < items.getLength()) {
		Node item = items.item(index);
		String listingId;
		try {
		    listingId = (String) listingIdExpr.evaluate(item,
			    XPathConstants.STRING);
		} catch (XPathExpressionException e) {
		    throw new RuntimeException(e);
		}
		if (!seenList.contains(listingId)) {
		    System.out.println(format(item));
		    System.out.println();

		    seenItems.put(listingId, new Date().toString());
		} else {
		    seenList.remove(listingId);
		}

		index = index + 1;
	    }

	    for (String itemId : seenList) {
		seenItems.remove(itemId);
	    }

	    try {
		seenItems.flush();
	    } catch (BackingStoreException e) {
		throw new RuntimeException(e);
	    }
	    
	    try {
		Thread.sleep(1000 * interval);
	    } catch (InterruptedException e) {
	    }
	}
    }

    private String format(Node node) {
	try {
	    OutputFormat format = new OutputFormat(node.getOwnerDocument());
	    format.setLineWidth(65);
	    format.setIndenting(true);
	    format.setIndent(2);
	    Writer out = new StringWriter();
	    XMLSerializer serializer = new XMLSerializer(out, format);
	    serializer.serialize(node);

	    return out.toString();
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
    }

}
