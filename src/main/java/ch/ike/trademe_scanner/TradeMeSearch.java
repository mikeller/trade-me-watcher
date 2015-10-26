package ch.ike.trademe_scanner;

public class TradeMeSearch {
	private String title;
	private String parameters;
	private float maxPrice;
	
	public TradeMeSearch(String title, String parameters, float maxPrice) {
		this.title = title;
		this.parameters = parameters;
		this.maxPrice = maxPrice;
	}

	public String getTitle() {
		return title;
	}

	public String getParameters() {
		return parameters;
	}

	public float getMaxPrice() {
		return maxPrice;
	}
}
