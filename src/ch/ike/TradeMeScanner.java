package ch.ike;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
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


import nz.co.trademe.TradeMeApi;
import nz.co.trademe.TradeMeConnector;

import org.scribe.builder.ServiceBuilder;
import org.scribe.model.Response;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class TradeMeScanner implements Runnable {

    public static void main(String[] args) {
	TradeMeScanner self = new TradeMeScanner();

	if ((args.length > 0) && "deauthorise".equals(args[0])) {
	    self.connector.deauthoriseUser();
	} else if ((args.length > 0) && "clear_cache".equals(args[0])) {
	    self.clearCache();
	} else {
	    self.runScanner();
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

    final Properties props;
    private final Preferences prefs;
    private final Preferences seenItems;

    private ResultHandler resultHandler;
    private final TradeMeConnector connector;

    private EmailProvider emailProvider;

    private boolean stopped;

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

    private void runScanner() {
	connector.service = new ServiceBuilder().provider(TradeMeApi.class)
		.apiKey(props.getProperty("consumer.key"))
		.apiSecret(props.getProperty("consumer.secret")).build();

	connector.checkAuthorisation();

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
	    Response response = connector
		    .sendGetRequest("https://api.trademe.co.nz/v1/Search/General.xml?"
			    + props.getProperty("search.parameters"));

	    NodeList items = resultHandler.parseResponse(response.getBody());

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
		String listingId = resultHandler.getListingId(item);
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
		emailProvider.sendEmail(message.toString());
	    }

	    System.out
		    .println("Found "
			    + items.getLength() + " items, " + newItems
			    + " new items, " + itemList.size() + " items to remove.");

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
