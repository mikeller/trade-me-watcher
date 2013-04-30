package ch.ike;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class TradeMeScanner implements Runnable {

	private final Properties props;
	private final Preferences prefs;
	private final Preferences seenItems;
	private final Preferences latestStartDates;
	private final Preferences seenQuestions;

	private ResultHandler resultHandler;
	private final TradeMeConnector connector;

	private EmailProvider emailProvider;

	private volatile boolean stopped;

	public static void main(String[] args) {
		List<String> argList = Arrays.asList(args);

		String configFile = "TradeMeScanner.xml";
		if (argList.contains("-c")) {
			configFile = argList.get(argList.indexOf("-c") + 1);
		}
		TradeMeScanner self = new TradeMeScanner(configFile);

		try {
			if (argList.contains("deauthorise")) {
				self.connector.deauthoriseUser();
			} else if (argList.contains("get_access_token")) {
				self.connector.printAccessToken();
			} else if (argList.contains("clear_cache")) {
				self.clearCache();
			} else {
				boolean interactive = false;
				if (argList.contains("interactive")) {
					interactive = true;
				}
				self.runScanner(interactive);
			}
		} catch (RuntimeException e) {
			StringWriter message = new StringWriter();
			PrintWriter printer = new PrintWriter(message);
			printer.print("TradeMeScanner: An exception occurred: \n\n"
					+ e.getMessage() + "\n\n Cause:\n\n");
			e.printStackTrace(printer);
			self.emailProvider.sendEmail("TradeMeScanner error", message
					.getBuffer().toString());
		}
	}

	private void clearCache() {
		try {
			seenItems.removeNode();
			latestStartDates.removeNode();
			seenQuestions.removeNode();
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	public TradeMeScanner(String configFile) {
		props = new Properties();
		try {
			FileInputStream in = new FileInputStream(configFile);
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

		latestStartDates = prefs.node("latest_start_dates");

		seenQuestions = prefs.node("seen_questions");

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

		boolean sendMessage = false;
		StringBuffer message = new StringBuffer();
		while (!stopped) {
			System.out.println("Starting scanner run at "
					+ SimpleDateFormat.getDateTimeInstance().format(
							GregorianCalendar.getInstance().getTime()));
			sendMessage = searchNewListings(searches, message);
			sendMessage = searchNewListingsNoDate(searches, message)
					|| sendMessage;
			sendMessage = searchNewQuestions(message) || sendMessage;

			if (sendMessage) {
				emailProvider.sendEmail("New TradeMe Listings Found",
						message.toString());

				sendMessage = false;
			}
			message.setLength(0);

			try {
				Thread.sleep(1000 * interval);
			} catch (InterruptedException e) {
			}
		}

		System.out.println("Terminated.");
	}

	private boolean searchNewQuestions(StringBuffer message) {
		Set<String> allQuestions;
		try {
			allQuestions = new HashSet<String>(Arrays.asList(seenQuestions
					.keys()));
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}

		int index;
		Set<String> expiredQuestions = new HashSet<String>(allQuestions);
		Calendar now = GregorianCalendar.getInstance();
		boolean questionsFound = false;

		Response response = connector
				.sendGetRequest("https://api.trademe.co.nz/v1/MyTradeMe/Watchlist/All.xml");

		if (!checkError(response)) {
			NodeList watchlistItems = resultHandler.getWatchlistItem(response
					.getBody());

			StringBuffer itemMessage = new StringBuffer();
			int itemIndex = 0;
			while (itemIndex < watchlistItems.getLength()) {
				Node item = watchlistItems.item(itemIndex);
				String itemTitle = resultHandler.getTitle(item);

				itemMessage.append("New questions for \"" + itemTitle
						+ "\":\n\n");

				response = connector
						.sendGetRequest("https://api.trademe.co.nz/v1/Listings/"
								+ resultHandler.getListingId(item) + ".xml");

				if (!checkError(response)) {
					NodeList questions = resultHandler
							.getListingQuestions(response.getBody());

					index = 0;
					int newQuestions = 0;
					while (index < questions.getLength()) {
						Node question = questions.item(index);
						String questionId = resultHandler
								.getQuestionId(question);

						expiredQuestions.remove(questionId);
						seenQuestions.put(questionId,
								DatatypeConverter.printDateTime(now));
						if (!allQuestions.contains(questionId)) {
							allQuestions.add(questionId);
							itemMessage.append(format(question) + "\n");

							newQuestions = newQuestions + 1;
						}

						index = index + 1;
					}

					if (newQuestions > 0) {
						message.append(itemMessage);
						message.append("\n");

						questionsFound = true;
					}

					itemMessage.setLength(0);

					System.out.println("Found " + questions.getLength()
							+ " questions, " + newQuestions
							+ " new questions for watchlist item \""
							+ itemTitle + "\".");
				}

				itemIndex = itemIndex + 1;
			}

			for (String questionId : expiredQuestions) {
				Calendar lastSeen = null;
				try {
					lastSeen = DatatypeConverter.parseDateTime(seenQuestions
							.get(questionId, null));
					lastSeen.add(Calendar.DATE, 1);
				} catch (IllegalArgumentException e) {

				}

				if ((lastSeen == null) || lastSeen.before(now)) {
					seenQuestions.remove(questionId);
				}
			}

			try {
				seenQuestions.flush();
			} catch (BackingStoreException e) {
				throw new RuntimeException(e);
			}
		}
		return questionsFound;
	}

	private boolean searchNewListings(Map<String, String> searches,
			StringBuffer message) {
		Set<String> allItems;
		try {
			allItems = new HashSet<String>(Arrays.asList(seenItems.keys()));
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}

		int index;
		Set<String> expiredItems = new HashSet<String>(allItems);
		Calendar now = GregorianCalendar.getInstance();
		boolean itemsFound = false;
		StringBuffer searchMessage = new StringBuffer();

		for (String parameters : searches.keySet()) {
			String latestDateString = latestStartDates.get(
					searches.get(parameters), null);
			Calendar latestDate = null;
			if (latestDateString != null) {
				latestDate = DatatypeConverter.parseDateTime(latestDateString);
			}

			String request = "https://api.trademe.co.nz/v1/Search/General.xml?"
					+ parameters;

			if (latestDate != null) {
				request = request + "&date_from="
						+ DatatypeConverter.printDateTime(latestDate);
			}

			Response response = connector.sendGetRequest(request);

			if (!checkError(response)) {
				NodeList items = resultHandler.getSearchListings(response
						.getBody());

				searchMessage.append("New items for \""
						+ searches.get(parameters) + "\":\n\n");

				index = 0;
				int newItems = 0;
				while (index < items.getLength()) {
					Node item = items.item(index);
					String listingId = resultHandler.getListingId(item);
					Calendar startDate = resultHandler.getStartDate(item);
					if ((latestDate == null) || latestDate.before(startDate)) {
						latestDate = startDate;
					}

					expiredItems.remove(listingId);
					seenItems.put(listingId,
							DatatypeConverter.printDateTime(now));
					if (!allItems.contains(listingId)) {
						allItems.add(listingId);
						searchMessage.append(format(item) + "\n");

						newItems = newItems + 1;
					}

					index = index + 1;
				}

				if (latestDate != null) {
					latestStartDates.put(searches.get(parameters),
							DatatypeConverter.printDateTime(latestDate));
				}
				try {
					latestStartDates.flush();
				} catch (BackingStoreException e) {
					throw new RuntimeException(e);
				}

				if (newItems > 0) {
					message.append(searchMessage);
					message.append("\n");

					itemsFound = true;
				}

				searchMessage.setLength(0);

				System.out.println("Found " + items.getLength() + " items, "
						+ newItems + " new items for search \""
						+ searches.get(parameters) + "\".");
			}
		}

		for (String itemId : expiredItems) {
			Calendar lastSeen = null;
			try {
				lastSeen = DatatypeConverter.parseDateTime(seenItems.get(
						itemId, null));
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
		return itemsFound;
	}

	private boolean searchNewListingsNoDate(Map<String, String> searches,
			StringBuffer message) {
		Set<String> allItems;
		try {
			allItems = new HashSet<String>(Arrays.asList(seenItems.keys()));
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}

		int index;
		Set<String> expiredItems = new HashSet<String>(allItems);
		Calendar now = GregorianCalendar.getInstance();
		boolean itemsFound = false;
		StringBuffer searchMessage = new StringBuffer();

		for (String parameters : searches.keySet()) {
			Response response = connector
					.sendGetRequest("https://api.trademe.co.nz/v1/Search/General.xml?"
							+ parameters);

			if (!checkError(response)) {
				NodeList items = resultHandler.getSearchListings(response
						.getBody());

				searchMessage
						.append("New items (found without start date) for \""
								+ searches.get(parameters) + "\":\n\n");

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
						searchMessage.append(format(item) + "\n");

						newItems = newItems + 1;
					}

					index = index + 1;
				}

				if (newItems > 0) {
					message.append(searchMessage);
					message.append("\n");

					itemsFound = true;
				}

				searchMessage.setLength(0);

				System.out.println("Found " + items.getLength()
						+ " items (not restricted by start date), " + newItems
						+ " new items for search \"" + searches.get(parameters)
						+ "\".");
			}
		}

		for (String itemId : expiredItems) {
			Calendar lastSeen = null;
			try {
				lastSeen = DatatypeConverter.parseDateTime(seenItems.get(
						itemId, null));
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
		return itemsFound;
	}

	private boolean checkError(Response response) {
		if (!response.isSuccessful()) {
			String message;
			try {
				Node error = resultHandler.getBody(response.getBody());
				message = "TradeMeScanner: An API call returned an error\n\n"
						+ format(error);
			} catch (SAXException e) {
				message = "TradeMeScanner: An API call returned an error\n\n"
						+ response.getBody();
			} catch (IOException e) {
				message = "TradeMeScanner: An API call returned an error\n\n"
						+ response.getBody();
			}
			emailProvider.sendEmail("TradeMeScanner: API call error", message);

			System.out.println("API call error: " + response.getBody());

			return true;
		} else {
			return false;
		}
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
			OutputFormat format;
			if (node instanceof Document) {
				format = new OutputFormat((Document) node);
			} else {
				format = new OutputFormat(node.getOwnerDocument());
			}
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
