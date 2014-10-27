package ch.ike.trademe_scanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class InteractiveThread implements Runnable {

	@Override
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
