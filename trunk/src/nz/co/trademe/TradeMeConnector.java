package nz.co.trademe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.scribe.exceptions.OAuthException;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

public class TradeMeConnector {

	private static final String SECRET = "Secret";
	private static final String TOKEN = "Token";
	private static final String ACCESS_TOKEN = "AccessToken";

	private final Properties props;
	private final Preferences prefs;

	public OAuthService service;
	public Token accessToken;

	public TradeMeConnector(Properties props, Preferences prefs) {
		this.props = props;
		this.prefs = prefs;
	}

	public void checkAuthorisation() {
		try {
			if (props.containsKey("access.token")
					&& props.containsKey("access.secret")) {
				accessToken = new Token(props.getProperty("access.token"),
						props.getProperty("access.secret"));
			} else if (prefs.nodeExists(ACCESS_TOKEN)) {
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

	public void deauthoriseUser() {
		try {
			prefs.node(ACCESS_TOKEN).removeNode();
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	public void printAccessToken() {
		try {
			if (prefs.nodeExists(ACCESS_TOKEN)) {
				System.out.println("Access Token: "
						+ prefs.node(ACCESS_TOKEN).get(TOKEN, null));
				System.out.println("Access Secret: "
						+ prefs.node(ACCESS_TOKEN).get(SECRET, null));
			} else {
				System.out.println("Access Token not Set.");
			}
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}
	public Response sendGetRequest(String url) {
		OAuthRequest request = new OAuthRequest(Verb.GET, url);
		service.signRequest(accessToken, request);

		return request.send();
	}
}