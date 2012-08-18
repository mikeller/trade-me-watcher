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

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
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
		TradeMeScanner self = new TradeMeScanner();

		if ((args.length > 0) && "deauthorise".equals(args[0])) {
			self.deauthorise();
		} else if ((args.length > 0) && "clear_cache".equals(args[0])) {
			self.clearCache();
		} else {
			self.runScanner();
		}
	}

	private void deauthorise() {
		try {
			prefs.node(ACCESS_TOKEN).removeNode();
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	private void clearCache() {
		try {
			seenItems.removeNode();
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	private final Properties props;
	private final Preferences prefs;
	private final Preferences seenItems;

	private final DocumentBuilder docBuilder;

	private final XPathExpression itemExpr;
	private final XPathExpression listingIdExpr;

	private boolean stopped = false;
	private OAuthService service;
	private Token accessToken;

	public TradeMeScanner() {
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

	private void runScanner() {
		service = new ServiceBuilder().provider(TradeMeApi.class)
				.apiKey(props.getProperty("consumer.key"))
				.apiSecret(props.getProperty("consumer.secret")).build();

		checkAuthorisation();

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

		System.out.println("Terminating...");

		stopped = true;

		loop.interrupt();
		try {
			loop.join();
		} catch (InterruptedException e) {
		}
	}

	public void run() {
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

			List<String> itemList;
			try {
				itemList = new ArrayList<String>(
						Arrays.asList(seenItems.keys()));
			} catch (BackingStoreException e) {
				throw new RuntimeException(e);
			}

			StringBuffer message = new StringBuffer();
			int index = 0;
			int newItems = 0;
			while (index < items.getLength()) {
				Node item = items.item(index);
				String listingId;
				try {
					listingId = (String) listingIdExpr.evaluate(item,
							XPathConstants.STRING);
				} catch (XPathExpressionException e) {
					throw new RuntimeException(e);
				}
				if (!itemList.contains(listingId)) {
					message.append(format(item) + "\n");

					newItems = newItems + 1;
					seenItems.put(listingId, new Date().toString());
				} else {
					itemList.remove(listingId);
				}

				index = index + 1;
			}

			if (newItems > 0) {
				sendEmail(message.toString());
			}

			System.out
					.println(new Date().toString() + ": Found "
							+ items.getLength() + " items, " + newItems
							+ " new items.");

			for (String itemId : itemList) {
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

		System.out.println("Ended.");
	}

	private void checkAuthorisation() {
		try {
			if (prefs.nodeExists(ACCESS_TOKEN)) {
				accessToken = new Token(prefs.node(ACCESS_TOKEN).get(TOKEN,
						null), prefs.node(ACCESS_TOKEN).get(SECRET, null));
			} else {
				System.out
						.println("This application needs authorisation first.");

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
						System.out.println("Exception during authorisation: "
								+ e.getMessage());
						System.out.println("Retrying");
					}
					System.out.println();
				}

				prefs.node(ACCESS_TOKEN).put(TOKEN, accessToken.getToken());
				prefs.node(ACCESS_TOKEN).put(SECRET, accessToken.getSecret());
				prefs.flush();

				System.out.println("Authorisation successful.");
			}
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
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

	private void sendEmail(String message) {
		Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.host",
				props.getProperty("email.smtphost"));
		Session session = Session.getDefaultInstance(properties);
		MimeMessage email = new MimeMessage(session);

		try {
			email.setFrom(new InternetAddress(props.getProperty("email.from")));
			email.addRecipient(Message.RecipientType.TO, new InternetAddress(
					props.getProperty("email.to")));

			email.setSubject("New TradeMe Listings Found");

			email.setText(message);

			Transport.send(email);
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
	}
}
