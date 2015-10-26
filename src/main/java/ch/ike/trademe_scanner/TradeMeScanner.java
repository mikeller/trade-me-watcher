package ch.ike.trademe_scanner;

import it.sauronsoftware.cron4j.Scheduler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
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

import argo.jdom.JdomParser;
import argo.jdom.JsonRootNode;
import argo.saj.InvalidSyntaxException;

public class TradeMeScanner implements Runnable {

	private static final String TRADE_ME_SCANNER = "trademescanner";

	private static final String TRADE_ME_SCANNER_XML = "TradeMeScanner.xml";

	private final TradeMeScannerPersistence persistence;
	private final TradeMeConnector connector;
	private final Scheduler scheduler;

	private final List<TradeMeSearch> searches;
	private final boolean clearCache;

	private JsonRootNode vcapServices;

	private ResultHandler resultHandler;

	private EmailProvider emailProvider;

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
		Properties props = new EnvironmentBackedProperties(configFile, TRADE_ME_SCANNER);

		if ("redis".equals(props.getProperty("persistence.method"))) {
			persistence = new RedisPersistence(TRADE_ME_SCANNER,
					getVcapServices());
		} else if ("postgres".equals(props.getProperty("persistence.method"))) {
			persistence = new PostgresPersistence(TRADE_ME_SCANNER,
					getVcapServices());
		} else {
			persistence = new PreferencesPersistence(this.getClass());
		}

		if ("sendgrid".equals(props.getProperty("email.method"))) {
			emailProvider = new SendGridEmailProvider(props, getVcapServices());
		} else {
			emailProvider = new JavaxMailEmailProvider(props);
		}

		connector = new TradeMeConnector(props, persistence);
		connector.service = new ServiceBuilder().provider(TradeMeApi.class)
				.apiKey(props.getProperty("consumer.key"))
				.apiSecret(props.getProperty("consumer.secret")).build();

		searches = new ArrayList<TradeMeSearch>();
		int index = 0;
		String parameters = props
				.getProperty("search." + index + ".parameters");
		while (parameters != null) {
			String title = props.getProperty("search." + index + ".title",
					"<unspecified>");
			float maxPrice = 0;
			String propertyName = "search." + index + ".maxPrice";
			String propertyValue = props.getProperty(propertyName, "0");
			try {
				maxPrice = Float.parseFloat(propertyValue);
			} catch (NumberFormatException e) {
				System.out.println("Ignored illegal value for property \"" + propertyName + "\": " 
						+ propertyValue + ". Please specify a float." );
			}
			searches.add(new TradeMeSearch(title, parameters, maxPrice));
			if (maxPrice > 0) {
				System.out.println("Added search " + title + " with parameters "
						+ parameters + ", maximum price " + maxPrice);
			} else {
				System.out.println("Added search " + title + " with parameters "
						+ parameters);				
			}

			index = index + 1;
			parameters = props.getProperty("search." + index + ".parameters");
		}
		
		clearCache = "true".equals(props.getProperty("clearcache"));

		resultHandler = new ResultHandler();
		
		scheduler = new Scheduler();
		String schedule = props.getProperty("search.schedule", "*/10 * * * *");
		scheduler.schedule(schedule, this);
		System.out.println("Set search schedule to \"" + schedule + "\".");
	}

	private JsonRootNode getVcapServices() {
		if (vcapServices == null) {
			String vcapServicesEnv = System.getenv("VCAP_SERVICES");
			if (vcapServicesEnv != null && vcapServicesEnv.length() > 0) {
				try {
					vcapServices = new JdomParser().parse(vcapServicesEnv);
				} catch (InvalidSyntaxException e) {
					throw new RuntimeException(e);
				}
			} else {
				throw new RuntimeException(
						"No 'VCAP_SERVICES' environment variable found.");
			}
		}
		return vcapServices;
	}

	private void runScanner(boolean clearCache, boolean interactive) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				scheduler.stop();

				System.out.println("Terminated.");
			}
		});

		if (this.clearCache || clearCache) {
			persistence.clearCache();

			System.out.println("Cleared cache.");
		}

		connector.checkAuthorisation();

		if (interactive) {
			System.out.println("Starting in interactive mode...");

			Thread interactiveThread = new Thread(new InteractiveThread());
			interactiveThread.start();
		}

		scheduler.start();
		
		run();
	}

	private Element searchNewQuestions(Element result) {
		PersistenceObject seenQuestions = persistence.getSeenQuestions();
		try {
			Set<String> allQuestions = new HashSet<String>(
					seenQuestions.getKeys());

			int index;
			Set<String> expiredQuestions = new HashSet<String>(allQuestions);
			Calendar now = GregorianCalendar.getInstance();

			Response response = connector
					.sendGetRequest("https://api.trademe.co.nz/v1/MyTradeMe/Watchlist/All.xml");

			if (!checkError(response)) {
				Document document = resultHandler.getBody(response.getBody());
				NodeList watchlistItems = resultHandler
						.getWatchlistItem(document);

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
							Element question = ((Element) resultList
									.item(index));
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

								questions = resultHandler.addQuestion(
										questions, resultItem, question);

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
						lastSeen = DatatypeConverter
								.parseDateTime(seenQuestions.get(questionId));
						lastSeen.add(Calendar.DATE, 1);
					} catch (IllegalArgumentException | NullPointerException ee) {

					}

					if ((lastSeen == null) || lastSeen.before(now)) {
						seenQuestions.remove(questionId);
					}
				}
			}

		} finally {
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

	private Element searchNewListings(List<TradeMeSearch> searches,
			Element result) {
		PersistenceObject seenItems = persistence.getSeenItems();
		try {
			Set<String> allItems = new HashSet<String>(seenItems.getKeys());

			int index;
			Set<String> expiredItems = new HashSet<String>(allItems);
			Calendar now = GregorianCalendar.getInstance();
			Element searchResult;

			PersistenceObject latestStartDates = persistence
					.getLatestStartDates();
			try {
				for (TradeMeSearch search: searches) {
					String latestDateString = latestStartDates.get(String.valueOf(search.hashCode()));
					Calendar latestDate = null;
					if (latestDateString != null) {
						latestDate = DatatypeConverter
								.parseDateTime(latestDateString);
					}

					String request = "https://api.trademe.co.nz/v1/Search/General.xml?photo_size=List&"
							+ search.getParameters();

					if (latestDate != null) {
						request = request + "&date_from="
								+ DatatypeConverter.printDateTime(latestDate);
					}

					Response response = connector.sendGetRequest(request);

					if (!checkError(response)) {
						Document document = resultHandler.getBody(response
								.getBody());
						NodeList items = resultHandler
								.getSearchListings(document);

						searchResult = null;

						int newItems = 0;
						index = 0;
						while (index < items.getLength()) {
							Element item = ((Element) items.item(index));
							String listingId = resultHandler.getListingId(item);
							Calendar startDate = resultHandler
									.getStartDate(item);
							if ((latestDate == null)
									|| latestDate.before(startDate)) {
								latestDate = startDate;
							}

							expiredItems.remove(listingId);
							seenItems.put(listingId,
									DatatypeConverter.printDateTime(now));
							if (!allItems.contains(listingId)) {
								allItems.add(listingId);

								float price = resultHandler.getPrice(item);
								if (Float.compare(price, search.getMaxPrice()) <= 0 || Float.compare(price , 0) == 0 
										&& Float.compare(resultHandler.getBuyNowPrice(item), search.getMaxPrice()) <= 0) {
									if (result == null) {
										result = resultHandler
												.createScanResultsDocument();
									}
									
									searchResult = resultHandler.addItem(
											searchResult, result,
											search.getTitle(), item);
									
									newItems = newItems + 1;
								}
							}

							index = index + 1;
						}

						if (latestDate != null) {
							latestStartDates
									.put(String.valueOf(search.hashCode()),
											DatatypeConverter
													.printDateTime(latestDate));
						}

						System.out.println("Found " + items.getLength()
								+ " items, " + newItems
								+ " new items for search \""
								+ search.getTitle() + "\".");
					}
				}
			} finally {
				latestStartDates.commit();
			}

			for (String itemId : expiredItems) {
				Calendar lastSeen = null;
				try {
					lastSeen = DatatypeConverter.parseDateTime(seenItems
							.get(itemId));
					lastSeen.add(Calendar.DATE, 1);
				} catch (IllegalArgumentException | NullPointerException e) {

				}

				if ((lastSeen == null) || lastSeen.before(now)) {
					seenItems.remove(itemId);
				}
			}

		} finally {
			seenItems.commit();
		}
		return result;
	}

	private Element searchNewListingsNoDate(List<TradeMeSearch> searches,
			Element result) {
		PersistenceObject seenItems = persistence.getSeenItems();
		try {
			Set<String> allItems = new HashSet<String>(seenItems.getKeys());

			int index;
			Set<String> expiredItems = new HashSet<String>(allItems);
			Calendar now = GregorianCalendar.getInstance();
			Element searchResult;

			for (TradeMeSearch search: searches) {
				Response response = connector
						.sendGetRequest("https://api.trademe.co.nz/v1/Search/General.xml?photo_size=List&"
								+ search.getParameters());

				if (!checkError(response)) {
					Document document = resultHandler.getBody(response
							.getBody());
					NodeList items = resultHandler.getSearchListings(document);

					searchResult = null;

					int newItems = 0;
					index = 0;
					while (index < items.getLength()) {
						Element item = ((Element) items.item(index));
						String listingId = resultHandler.getListingId(item);

						expiredItems.remove(listingId);
						seenItems.put(listingId,
								DatatypeConverter.printDateTime(now));
						if (!allItems.contains(listingId)) {
							allItems.add(listingId);
							
							float price = resultHandler.getPrice(item);
							if (Float.compare(price, search.getMaxPrice()) <= 0 || Float.compare(price , 0) == 0 
									&& Float.compare(resultHandler.getBuyNowPrice(item), search.getMaxPrice()) <= 0) {
								if (result == null) {
									result = resultHandler
											.createScanResultsDocument();
								}
								searchResult = resultHandler.addItem(searchResult,
										result, search.getTitle(), item);
								
								newItems = newItems + 1;
							}
						}

						index = index + 1;
					}

					System.out.println("Found " + items.getLength()
							+ " items (not restricted by start date), "
							+ newItems + " new items for search \""
							+ search.getTitle() + "\".");
				}
			}

			for (String itemId : expiredItems) {
				Calendar lastSeen = null;
				try {
					lastSeen = DatatypeConverter.parseDateTime(seenItems
							.get(itemId));
					lastSeen.add(Calendar.DATE, 1);
				} catch (IllegalArgumentException | NullPointerException e) {

				}

				if ((lastSeen == null) || lastSeen.before(now)) {
					seenItems.remove(itemId);
				}
			}

		} finally {
			seenItems.commit();
		}

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
		System.out.println("Starting scanner run at "
				+ SimpleDateFormat.getDateTimeInstance().format(
						GregorianCalendar.getInstance().getTime()));

		Element result = searchNewListings(searches, null);
		result = searchNewListingsNoDate(searches, result);
		result = searchNewQuestions(result);

		if (result != null) {
			String title = "";

			int count = resultHandler.getItemCount(result.getOwnerDocument());
			if (count > 0) {
				title = title + count + " new search results";
			}

			count = resultHandler.getQuestionCount(result.getOwnerDocument());
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

	}
}
