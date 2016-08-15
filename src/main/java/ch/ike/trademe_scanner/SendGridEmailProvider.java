package ch.ike.trademe_scanner;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;

public class SendGridEmailProvider extends JavaxMailEmailProvider implements EmailProvider {

	private final String username;
	private final String hostname;
	private final String password;

	public SendGridEmailProvider(Properties props, JsonRootNode vcapServices) {
		super(props);
		
		JsonNode rediscloudNode = vcapServices.getNode("sendgrid");
		JsonNode credentials = rediscloudNode.getNode(0).getNode("credentials");
		username = credentials.getStringValue("username");
		hostname = credentials.getStringValue("hostname");
		password = credentials.getStringValue("password");
	}

	@Override
	protected Session createSession() {
		Properties properties = new Properties();
		properties.put("mail.transport.protocol", "smtp");
		properties.put("mail.smtp.host", hostname);
		properties.put("mail.smtp.port", 587);
		properties.put("mail.smtp.auth", "true");

		System.out.println("Setting up mail out using SendGrid, using username " + username + " on " + hostname + ".");

		return Session.getInstance(properties, new Authenticator() {
		       public PasswordAuthentication getPasswordAuthentication() {
		           return new PasswordAuthentication(username, password);		
		       }
		});
	}

}
