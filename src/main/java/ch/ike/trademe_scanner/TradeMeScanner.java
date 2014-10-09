package ch.ike.trademe_scanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import nz.co.trademe.TradeMeApi;
import nz.co.trademe.TradeMeConnector;

import org.scribe.builder.ServiceBuilder;
import org.scribe.model.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TradeMeScanner implements Runnable {

	private static final String TRADE_ME_SCANNER = "trademescanner";

	private static final String TRADE_ME_SCANNER_XML = "TradeMeScanner.xml";

	private final Properties props;
	private final TradeMeScannerPersistence persistence;

	private ResultHandler resultHandler;
	private final TradeMeConnector connector;

	private EmailProvider emailProvider;

	private volatile boolean stopped;

	public static void main(String[] args) {
		List<String> argList = Arrays.asList(args);

		String configFile = TRADE_ME_SCANNER_XML;
		if (argList.contains("-c")) {
			configFile = argList.get(argList.indexOf("-c") + 1);
		}

		TradeMeScanner self = new TradeMeScanner(configFile);

		try {
			if (argList.contains("deauthorise")) {
				self.connector.deauthoriseUser();
			} else if (argList.contains("get_access_token")) {
				self.connector.printAccessToken();
			} else {
				boolean clearCache = false;
				if (argList.contains("clear_cache")) {
					clearCache = true;
				}
				boolean interactive = false;
				if (argList.contains("interactive")) {
					interactive = true;
				}
				self.runScanner(clearCache, interactive);
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

	public TradeMeScanner(String configFile) {
		props = new EnvironmentBackedProperties(configFile, TRADE_ME_SCANNER);

		if ("redis".equals(props.getProperty("persistence.method"))) {
			persistence = new RedisPersistence(TRADE_ME_SCANNER);
		} else {
			persistence = new PreferencesPersistence(this.getClass());
		}

		emailProvider = new JavaxMailEmailProvider(props);

		connector = new TradeMeConnector(props, persistence);

		resultHandler = new ResultHandler();

		stopped = false;
	}

	private void runScanner(boolean clearCache, boolean interactive) {
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
		
		if (clearCache || "true".equals(props.getProperty("clearcache"))) {
			persistence.clearCache();
		}

		int interval = Integer.parseInt(props.getProperty("search.interval"));
		System.out.println("Set search interval to " + interval + " seconds.");

		Map<String, String> searches = new Hashtable<String, String>();
		int index = 0;
		String parameters = props
				.getProperty("search." + index + ".parameters");
		while (parameters != null) {
			String title = props.getProperty("search." + index + ".title",
					"<unspecified>");
			searches.put(parameters, title);
			System.out.println("Added search " + title + " with parameters "
					+ parameters);

			index = index + 1;
			parameters = props.getProperty("search." + index + ".parameters");
		}

		connector.service = new ServiceBuilder().provider(TradeMeApi.class)
				.apiKey(props.getProperty("consumer.key"))
				.apiSecret(props.getProperty("consumer.secret")).build();

		connector.checkAuthorisation();

		if (interactive) {
			Thread interactiveThread = new Thread(this);
			interactiveThread.start();
		}

		Element result = null;
		while (!stopped) {
			System.out.println("Starting scanner run at "
					+ SimpleDateFormat.getDateTimeInstance().format(
							GregorianCalendar.getInstance().getTime()));

			result = searchNewListings(searches, result);
			result = searchNewListingsNoDate(searches, result);
			result = searchNewQuestions(result);

			if (result != null) {
				String title = "";

				int count = resultHandler.getItemCount(result
						.getOwnerDocument());
				if (count > 0) {
					title = title + count + " new search results";
				}

				count = resultHandler.getQuestionCount(result
						.getOwnerDocument());
				if (count > 0) {
					if (!title.equals("")) {
						title = title + ", ";
					}
					title = title + count + " new questions";
				}

				title = "TradeMe Scanner: " + title;

				emailProvider.sendEmail(title, resultHandler.toString(result),
						resultHandler.toHtml(result));

				result = null;

				System.out.println("Email sent");
			}

			System.out.println("Finished scanner run at "
					+ SimpleDateFormat.getDateTimeInstance().format(
							GregorianCalendar.getInstance().getTime()));
			try {
				Thread.sleep(1000 * interval);
			} catch (InterruptedException e) {
			}
		}

		System.out.println("Terminated.");
	}

	private Element searchNewQuestions(Element result) {
		PersistenceObject seenQuestions = persistence.getSeenQuestions();
		Set<String> allQuestions = new HashSet<String>(seenQuestions.getKeys());

		int index;
		Set<String> expiredQuestions = new HashSet<String>(allQuestions);
		Calendar now = GregorianCalendar.getInstance();

		Response response = connector
				.sendGetRequest("https://api.trademe.co.nz/v1/MyTradeMe/Watchlist/All.xml");

		if (!checkError(response)) {
			Document document = resultHandler.getBody(response.getBody());
			NodeList watchlistItems = resultHandler.getWatchlistItem(document);

			int itemIndex = 0;
			while (itemIndex < watchlistItems.getLength()) {
				Node item = watchlistItems.item(itemIndex);
				String itemTitle = resultHandler.getTitle(item);

				response = connector
						.sendGetRequest("https://api.trademe.co.nz/v1/Listings/"
								+ resultHandler.getListingId(item) + ".xml");

				if (!checkError(response)) {
					document = resultHandler.getBody(response.getBody());
					NodeList resultList = resultHandler
							.getListingQuestions(document);

					index = 0;
					int newQuestions = 0;
					Element questions = null;
					Element resultItem = null;
					while (index < resultList.getLength()) {
						Element question = ((Element) resultList.item(index));
						String questionId = resultHandler
								.getQuestionId(question);

						expiredQuestions.remove(questionId);
						seenQuestions.put(questionId,
								DatatypeConverter.printDateTime(now));
						if (!allQuestions.contains(questionId)) {
							allQuestions.add(questionId);

							if (result == null) {
								result = resultHandler
										.createScanResultsDocument();
							}

							if (resultItem == null) {
								resultItem = addListingContents(result,
										document);
							}

							questions = resultHandler.addQuestion(questions,
									resultItem, question);

							newQuestions = newQuestions + 1;
						}

						index = index + 1;
					}

					System.out.println("Found " + resultList.getLength()
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
							.get(questionId));
					lastSeen.add(Calendar.DATE, 1);
				} catch (IllegalArgumentException e) {

				}

				if ((lastSeen == null) || lastSeen.before(now)) {
					seenQuestions.remove(questionId);
				}
			}

			seenQuestions.commit();
		}
		return result;
	}

	private Element addListingContents(Element result, Document document) {
		Element resultItem = null;

		int index;
		NodeList resultList = resultHandler.getListingContents(document);

		index = 0;
		while (index < resultList.getLength()) {
			resultItem = resultHandler.addItemDetail(resultItem, result,
					((Element) resultList.item(index)));

			index = index + 1;
		}
		return resultItem;
	}

	private Element searchNewListings(Map<String, String> searches,
			Element result) {
		PersistenceObject seenItems = persistence.getSeenItems();
		PersistenceObject latestStartDates = persistence.getLatestStartDates();
		Set<String> allItems = new HashSet<String>(seenItems.getKeys());

		int index;
		Set<String> expiredItems = new HashSet<String>(allItems);
		Calendar now = GregorianCalendar.getInstance();
		Element searchResult;

		for (String parameters : searches.keySet()) {
			String latestDateString = latestStartDates.get(searches
					.get(parameters));
			Calendar latestDate = null;
			if (latestDateString != null) {
				latestDate = DatatypeConverter.parseDateTime(latestDateString);
			}

			String request = "https://api.trademe.co.nz/v1/Search/General.xml?photo_size=List&"
					+ parameters;

			if (latestDate != null) {
				request = request + "&date_from="
						+ DatatypeConverter.printDateTime(latestDate);
			}

			Response response = connector.sendGetRequest(request);

			if (!checkError(response)) {
				Document document = resultHandler.getBody(response.getBody());
				NodeList items = resultHandler.getSearchListings(document);

				searchResult = null;

				index = 0;
				int newItems = 0;
				while (index < items.getLength()) {
					Element item = ((Element) items.item(index));
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

						if (result == null) {
							result = resultHandler.createScanResultsDocument();
						}
						searchResult = resultHandler.addItem(searchResult,
								result, searches.get(parameters), item);

						newItems = newItems + 1;
					}

					index = index + 1;
				}

				if (latestDate != null) {
					latestStartDates.put(searches.get(parameters),
							DatatypeConverter.printDateTime(latestDate));
				}
				latestStartDates.commit();

				System.out.println("Found " + items.getLength() + " items, "
						+ newItems + " new items for search \""
						+ searches.get(parameters) + "\".");
			}
		}

		for (String itemId : expiredItems) {
			Calendar lastSeen = null;
			try {
				lastSeen = DatatypeConverter.parseDateTime(seenItems
						.get(itemId));
				lastSeen.add(Calendar.DATE, 1);
			} catch (IllegalArgumentException e) {

			}

			if ((lastSeen == null) || lastSeen.before(now)) {
				seenItems.remove(itemId);
			}
		}

		seenItems.commit();

		return result;
	}

	private Element searchNewListingsNoDate(Map<String, String> searches,
			Element result) {
		PersistenceObject seenItems = persistence.getSeenItems();
		Set<String> allItems = new HashSet<String>(seenItems.getKeys());

		int index;
		Set<String> expiredItems = new HashSet<String>(allItems);
		Calendar now = GregorianCalendar.getInstance();
		Element searchResult;

		for (String parameters : searches.keySet()) {
			Response response = connector
					.sendGetRequest("https://api.trademe.co.nz/v1/Search/General.xml?photo_size=List&"
							+ parameters);

			if (!checkError(response)) {
				Document document = resultHandler.getBody(response.getBody());
				NodeList items = resultHandler.getSearchListings(document);

				searchResult = null;

				index = 0;
				int newItems = 0;
				while (index < items.getLength()) {
					Element item = ((Element) items.item(index));
					String listingId = resultHandler.getListingId(item);

					expiredItems.remove(listingId);
					seenItems.put(listingId,
							DatatypeConverter.printDateTime(now));
					if (!allItems.contains(listingId)) {
						allItems.add(listingId);

						if (result == null) {
							result = resultHandler.createScanResultsDocument();
						}
						searchResult = resultHandler.addItem(searchResult,
								result, searches.get(parameters), item);

						newItems = newItems + 1;
					}

					index = index + 1;
				}

				System.out.println("Found " + items.getLength()
						+ " items (not restricted by start date), " + newItems
						+ " new items for search \"" + searches.get(parameters)
						+ "\".");
			}
		}

		for (String itemId : expiredItems) {
			Calendar lastSeen = null;
			try {
				lastSeen = DatatypeConverter.parseDateTime(seenItems
						.get(itemId));
				lastSeen.add(Calendar.DATE, 1);
			} catch (IllegalArgumentException e) {

			}

			if ((lastSeen == null) || lastSeen.before(now)) {
				seenItems.remove(itemId);
			}
		}

		seenItems.commit();

		return result;
	}

	private boolean checkError(Response response) {
		if (!response.isSuccessful()) {
			String message;
			Document error = resultHandler.getBody(response.getBody());
			message = "TradeMeScanner: An API call returned an error\n\n"
					+ resultHandler.toString(error.getDocumentElement());
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
}
