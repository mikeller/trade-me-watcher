package nz.co.trademe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map.Entry;
import java.util.Properties;

import org.scribe.exceptions.OAuthException;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import ch.ike.trademe_scanner.TradeMeScannerPersistence;

public class TradeMeConnector {
	private final Properties props;
	private final TradeMeScannerPersistence persistence;

	public OAuthService service;
	public Token accessToken;

	public TradeMeConnector(Properties props, TradeMeScannerPersistence persistence) {
		this.props = props;
		this.persistence = persistence;
	}

	public void checkAuthorisation() {
		String accessTokenString = props.getProperty("access.token");
		String accessSecretString = props.getProperty("access.secret");
		Entry<String, String> accessTokenData = persistence.getAccessToken();
		if ((accessTokenString != null) && (accessSecretString != null)) {
			accessToken = new Token(accessTokenString, accessSecretString);
		} else if (accessTokenData != null) {
			accessToken = new Token(accessTokenData.getKey(), accessTokenData.getValue());
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
			
			persistence.setAccessToken(accessToken.getToken(), accessToken.getSecret());

			System.out.println("Authorisation successful.");
		}
	}

	public void deauthoriseUser() {
		persistence.deleteAccessToken();

		System.out.println("Deauthorised user.");
	}

	public void printAccessToken() {
		Entry<String, String> accessTokenData = persistence.getAccessToken();
		if (accessTokenData != null) {
			System.out.println("Access Token: "
					+ accessTokenData.getKey());
			System.out.println("Access Secret: "
					+ accessTokenData.getValue());
		} else {
			System.out.println("Access Token not Set.");
		}
	}
	public Response sendGetRequest(String url) {
		OAuthRequest request = new OAuthRequest(Verb.GET, url);
		service.signRequest(accessToken, request);

		return request.send();
	}
}