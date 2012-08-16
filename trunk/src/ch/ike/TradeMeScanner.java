package ch.ike;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class TradeMeScanner {

	public static void main(String[] args) {
		TradeMeScanner self = new TradeMeScanner();
		self.execute(args);
	}

	public void execute(String[] args) {
		OAuthService service = new ServiceBuilder().provider(TradeMeApi.class)
				.apiKey(args[0]).apiSecret(args[1]).build();

		if ("authorize".equals(args[2])) {
			Token requestToken = service.getRequestToken();

			System.out.println("Please go to "
					+ service.getAuthorizationUrl(requestToken)
					+ " to authorize this app.");
			System.out.println();

			BufferedReader br = new BufferedReader(new InputStreamReader(
					System.in));

			String pin = null;
			Token accessToken = null;
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

			System.out.println("Access Token: " + accessToken.getToken());
			System.out.println("Access Secret: " + accessToken.getSecret());

		} else if ("run".equals(args[2])) {
			Token accessToken = new Token(args[3], args[4]);

			OAuthRequest request = new OAuthRequest(Verb.GET,
					"https://api.trademe.co.nz/v1/Search/General.xml?"
							+ args[5]);
			service.signRequest(accessToken, request);

			Response response = request.send();

			System.out.println(format(response.getBody()));
		}
	}

	private String format(String unformattedXml) {
		try {
			final Document document = parseXmlFile(unformattedXml);

			OutputFormat format = new OutputFormat(document);
			format.setLineWidth(65);
			format.setIndenting(true);
			format.setIndent(2);
			Writer out = new StringWriter();
			XMLSerializer serializer = new XMLSerializer(out, format);
			serializer.serialize(document);

			return out.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Document parseXmlFile(String in) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(in));
			return db.parse(is);
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		} catch (SAXException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
