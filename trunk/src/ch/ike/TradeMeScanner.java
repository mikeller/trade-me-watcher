package ch.ike;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.xml.bind.DatatypeConverter;

import nz.co.trademe.TradeMeApi;
import nz.co.trademe.TradeMeConnector;

import org.scribe.builder.ServiceBuilder;
import org.scribe.model.Response;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class TradeMeScanner implements Runnable {

    private final Properties props;
    private final Preferences prefs;
    private final Preferences seenItems;

    private ResultHandler resultHandler;
    private final TradeMeConnector connector;

    private EmailProvider emailProvider;

    private volatile boolean stopped;

    public static void main(String[] args) {
	TradeMeScanner self = new TradeMeScanner();

	if ((args.length > 0) && "deauthorise".equals(args[0])) {
	    self.connector.deauthoriseUser();
	} else if ((args.length > 0) && "get_access_token".equals(args[0])) {
	    self.connector.printAccessToken();
	} else if ((args.length > 0) && "clear_cache".equals(args[0])) {
	    self.clearCache();
	} else {
	    boolean interactive = false;
	    if ((args.length > 0) && "interactive".equals(args[0])) {
		interactive = true;
	    }
	    self.runScanner(interactive);
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

    public TradeMeScanner() {
	props = new Properties();
	try {
	    FileInputStream in = new FileInputStream("TradeMeScanner.xml");
	    props.loadFromXML(in);
	    in.close();
	} catch (FileNotFoundException e) {
	    throw new RuntimeException(e);
	} catch (InvalidPropertiesFormatException e) {
	    throw new RuntimeException(e);
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}

	prefs = Preferences.userNodeForPackage(this.getClass());

	emailProvider = new EmailProvider(props);

	connector = new TradeMeConnector(props, prefs);

	seenItems = prefs.node("seen_items");

	resultHandler = new ResultHandler();

	stopped = false;
    }

    private void runScanner(boolean interactive) {
	final Thread mainThread = Thread.currentThread();
	Runtime.getRuntime().addShutdownHook(new Thread() {
	    public void run() {
		stopped = true;
		mainThread.interrupt();

		try {
		    mainThread.join();
		} catch (InterruptedException e) {
		}
	    }
	});

	int interval = Integer.parseInt(props.getProperty("search.interval"));
	System.out.println("Set search interval to " + interval + " seconds.");

	Map<String, String> searches = new Hashtable<String, String>();
	int index = 0;
	while (props.containsKey("search." + index + ".parameters")) {
	    String parameters = props.getProperty("search." + index
		    + ".parameters");
	    String title = props.getProperty("search." + index + ".title",
		    "<unspecified>");
	    searches.put(parameters, title);
	    System.out.println("Added search " + title + " with parameters "
		    + parameters);

	    index = index + 1;
	}

	connector.service = new ServiceBuilder().provider(TradeMeApi.class)
		.apiKey(props.getProperty("consumer.key"))
		.apiSecret(props.getProperty("consumer.secret")).build();

	connector.checkAuthorisation();

	if (interactive) {
	    Thread interactiveThread = new Thread(this);
	    interactiveThread.start();
	}

	while (!stopped) {
	    Set<String> allItems;
	    try {
		allItems = new HashSet<String>(Arrays.asList(seenItems.keys()));
	    } catch (BackingStoreException e) {
		throw new RuntimeException(e);
	    }
	    Set<String> expiredItems = new HashSet<String>(allItems);
	    Calendar now = GregorianCalendar.getInstance();
	    StringBuffer message = new StringBuffer();
	    boolean itemsFound = false;

	    for (String parameters : searches.keySet()) {
		Response response = connector
			.sendGetRequest("https://api.trademe.co.nz/v1/Search/General.xml?"
				+ parameters);

		NodeList items = resultHandler
			.parseResponse(response.getBody());

		message.append("New items for \"" + searches.get(parameters)
			+ "\":\n\n");

		index = 0;
		int newItems = 0;
		while (index < items.getLength()) {
		    Node item = items.item(index);
		    String listingId = resultHandler.getListingId(item);

		    expiredItems.remove(listingId);
		    seenItems.put(listingId,
			    DatatypeConverter.printDateTime(now));
		    if (!allItems.contains(listingId)) {
			allItems.add(listingId);
			message.append(format(item) + "\n");

			newItems = newItems + 1;
		    }

		    index = index + 1;
		}

		if (newItems > 0) {
		    message.append("\n");

		    itemsFound = true;
		}

		System.out.println("Found " + items.getLength() + " items, "
			+ newItems + " new items for search \""
			+ searches.get(parameters) + "\".");
	    }

	    if (itemsFound) {
		emailProvider.sendEmail(message.toString());
	    }

	    for (String itemId : expiredItems) {
		Calendar lastSeen = null;
		try {
			lastSeen = DatatypeConverter.parseDateTime(seenItems.get(itemId, null));
			lastSeen.add(Calendar.DATE, 1);
		} catch (IllegalArgumentException e) {
			
		}
			
		if ((lastSeen == null) || lastSeen.before(now)) {
		    seenItems.remove(itemId);
		}
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

	System.out.println("Terminated.");
    }

    public void run() {
	String input = "";
	BufferedReader inReader = new BufferedReader(new InputStreamReader(
		System.in));
	while (input != null) {
	    System.out.print("Enter [x] to exit:");
	    try {
		input = inReader.readLine();
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }

	    if ("x".equals(input)) {
		System.exit(0);
	    }

	    System.out.println();
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
