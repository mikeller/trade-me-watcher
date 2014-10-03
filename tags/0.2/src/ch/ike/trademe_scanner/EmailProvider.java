package ch.ike.trademe_scanner;

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

public class EmailProvider {

	private final Properties props;

	private Session session;

	public EmailProvider(Properties props) {
		this.props = props;

		Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.host",
				props.getProperty("email.smtphost"));
		session = Session.getDefaultInstance(properties);
	}

	void sendEmail(String subject, String message, String htmlMessage) {
		MimeMessage email = new MimeMessage(session);

		try {
			email.setFrom(new InternetAddress(props.getProperty("email.from")));
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

	public void sendEmail(String subject, String message) {
		sendEmail(subject, message, null);
	}
}