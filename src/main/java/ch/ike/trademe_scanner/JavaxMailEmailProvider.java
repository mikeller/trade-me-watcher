package ch.ike.trademe_scanner;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class JavaxMailEmailProvider implements EmailProvider {

	private final Properties props;

	private Session session;

	public JavaxMailEmailProvider(Properties props) {
		this.props = props;
	}

	private Session getSession() {
		if (session == null) {
			session = createSession();
		}
		
		return session;
	}

	protected Session createSession() {
		Properties properties = new Properties();
		properties.put("mail.transport.protocol", "smtp");
		properties.put("mail.smtp.host",
				props.getProperty("email.smtphost"));
		
		System.out.println("Setting up mail out using javax.mail, with SMTP host set to " + props.getProperty("email.smtphost") + ".");

		return Session.getDefaultInstance(properties);
	}

	@Override
	public void sendEmail(String subject, String message, String htmlMessage) {
		String fromAddress = props.getProperty("email.from");
		if (fromAddress == null) {
			fromAddress = "TradeMeScanner";
		}
		if (!fromAddress.contains("@")) {
			try {
				fromAddress = fromAddress + "@"
						+ InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				fromAddress = fromAddress + "@localhost";
			}
		}

		MimeMessage email = new MimeMessage(getSession());

		try {
			email.setFrom(new InternetAddress(fromAddress));
			email.addRecipient(RecipientType.TO,
					new InternetAddress(props.getProperty("email.to")));

			email.setSubject(subject);

			Multipart multiPart = new MimeMultipart("alternative");

			if (htmlMessage != null) {
				MimeBodyPart textPart = new MimeBodyPart();
				textPart.setText(message, "utf-8");

				MimeBodyPart htmlPart = new MimeBodyPart();
				htmlPart.setContent(htmlMessage, "text/html; charset=utf-8");

				multiPart.addBodyPart(textPart);
				multiPart.addBodyPart(htmlPart);
				email.setContent(multiPart);
			} else {
				email.setText(message);
			}

			Transport.send(email);
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void sendEmail(String subject, String message) {
		sendEmail(subject, message, null);
	}
}