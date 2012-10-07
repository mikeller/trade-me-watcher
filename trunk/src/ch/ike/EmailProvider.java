package ch.ike;

import java.util.Properties;

import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

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

    void sendEmail(String subject, String message) {
        MimeMessage email = new MimeMessage(session);

        try {
            email.setFrom(new InternetAddress(props.getProperty("email.from")));
            email.addRecipient(RecipientType.TO, new InternetAddress(
        	    props.getProperty("email.to")));

            email.setSubject(subject);

            email.setText(message);

            Transport.send(email);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}