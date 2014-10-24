package ch.ike.trademe_scanner;

public interface EmailProvider {

	public abstract void sendEmail(String subject, String message,
			String htmlMessage);

	public abstract void sendEmail(String subject, String message);

}